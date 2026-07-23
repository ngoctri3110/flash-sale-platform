package com.ngoctri.flashsale.inventory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A black-box PostgreSQL lab. It deliberately does not replace the production
 * order path, which continues to use the atomic conditional decrement.
 */
@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InventoryConcurrencyStrategyLabIntegrationTest {

    private static final long PRODUCT_ID = 1;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void restoreStock() {
        jdbcClient.sql("UPDATE inventories SET available_quantity = 18, version = 0 WHERE product_id = :productId")
                .param("productId", PRODUCT_ID)
                .update();
    }

    @Test
    void everyStrategyPreservesTheInvariantAtLowContention() throws Exception {
        for (var strategy : Strategy.values()) {
            var result = runWorkload(strategy, 1, 1);
            assertThat(result.accepted()).isEqualTo(1);
            assertThat(result.rejected()).isZero();
            assertThat(result.finalQuantity()).isZero();
            print(result);
        }
    }

    @Test
    void everyStrategyPreservesTheInvariantAtHighContention() throws Exception {
        for (var strategy : Strategy.values()) {
            var result = runWorkload(strategy, 8, 24);
            assertThat(result.accepted()).isEqualTo(8);
            assertThat(result.rejected()).isEqualTo(16);
            assertThat(result.finalQuantity()).isZero();
            assertThat(result.accepted() + result.rejected()).isEqualTo(24);
            print(result);
        }
    }

    private WorkloadResult runWorkload(Strategy strategy, long stock, int attempts) throws Exception {
        jdbcClient.sql("UPDATE inventories SET available_quantity = :stock, version = 0 WHERE product_id = :productId")
                .param("stock", stock)
                .param("productId", PRODUCT_ID)
                .update();

        var ready = new CountDownLatch(attempts);
        var start = new CountDownLatch(1);
        var measuring = new AtomicBoolean(true);
        var lockWaitSamples = new java.util.concurrent.atomic.AtomicLong();
        var monitor = Thread.ofVirtual().start(() -> {
            while (measuring.get()) {
                var waiting = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM pg_stat_activity
                        WHERE datname = current_database() AND wait_event_type = 'Lock'
                        """).query(Long.class).single();
                if (waiting > 0) {
                    lockWaitSamples.incrementAndGet();
                }
                try {
                    Thread.sleep(1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });

        var startedAt = System.nanoTime();
        List<AttemptResult> results;
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<AttemptResult>>();
            for (var index = 0; index < attempts; index++) {
                futures.add(executor.submit(attempt(strategy, ready, start)));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }
        } finally {
            measuring.set(false);
            monitor.join();
        }
        var elapsedNanos = System.nanoTime() - startedAt;
        var accepted = results.stream().filter(AttemptResult::accepted).count();
        var retries = results.stream().mapToInt(AttemptResult::retries).sum();
        var p95Nanos = percentile(results.stream().mapToLong(AttemptResult::elapsedNanos).sorted().toArray(), 0.95);
        var finalQuantity = jdbcClient.sql("SELECT available_quantity FROM inventories WHERE product_id = :productId")
                .param("productId", PRODUCT_ID)
                .query(Long.class)
                .single();
        return new WorkloadResult(
                strategy, attempts, accepted, attempts - accepted, finalQuantity, retries,
                lockWaitSamples.get(), elapsedNanos, p95Nanos);
    }

    private Callable<AttemptResult> attempt(Strategy strategy, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            start.await();
            var startedAt = System.nanoTime();
            var result = switch (strategy) {
                case ATOMIC -> atomicDecrement();
                case PESSIMISTIC -> pessimisticDecrement();
                case OPTIMISTIC -> optimisticDecrement();
            };
            return new AttemptResult(result.accepted(), result.retries(), System.nanoTime() - startedAt);
        };
    }

    private Outcome atomicDecrement() throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        UPDATE inventories SET available_quantity = available_quantity - 1, version = version + 1
                        WHERE product_id = ? AND available_quantity >= 1
                        """)) {
            statement.setLong(1, PRODUCT_ID);
            return new Outcome(statement.executeUpdate() == 1, 0);
        }
    }

    private Outcome pessimisticDecrement() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long quantity;
                try (var read = connection.prepareStatement("SELECT available_quantity FROM inventories WHERE product_id = ? FOR UPDATE")) {
                    read.setLong(1, PRODUCT_ID);
                    try (var rows = read.executeQuery()) { rows.next(); quantity = rows.getLong(1); }
                }
                if (quantity < 1) { connection.commit(); return new Outcome(false, 0); }
                try (var update = connection.prepareStatement("UPDATE inventories SET available_quantity = available_quantity - 1, version = version + 1 WHERE product_id = ?")) {
                    update.setLong(1, PRODUCT_ID); update.executeUpdate();
                }
                connection.commit();
                return new Outcome(true, 0);
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    private Outcome optimisticDecrement() throws Exception {
        var retries = 0;
        while (retries < 20) {
            try (var connection = dataSource.getConnection()) {
                long quantity; long version;
                try (var read = connection.prepareStatement("SELECT available_quantity, version FROM inventories WHERE product_id = ?")) {
                    read.setLong(1, PRODUCT_ID);
                    try (var rows = read.executeQuery()) { rows.next(); quantity = rows.getLong(1); version = rows.getLong(2); }
                }
                if (quantity < 1) return new Outcome(false, retries);
                try (var update = connection.prepareStatement("""
                        UPDATE inventories SET available_quantity = available_quantity - 1, version = version + 1
                        WHERE product_id = ? AND version = ? AND available_quantity >= 1
                        """)) {
                    update.setLong(1, PRODUCT_ID); update.setLong(2, version);
                    if (update.executeUpdate() == 1) return new Outcome(true, retries);
                }
            }
            retries++;
        }
        return new Outcome(false, retries);
    }

    private static long percentile(long[] values, double percentile) {
        return values[(int) Math.ceil(percentile * values.length) - 1];
    }

    private static void print(WorkloadResult result) {
        System.out.printf("strategy=%s attempts=%d accepted=%d rejected=%d throughput=%.1f/s p95=%.2fms retries=%d lockWaitSamples=%d%n",
                result.strategy(), result.attempts(), result.accepted(), result.rejected(),
                result.attempts() * 1_000_000_000.0 / result.elapsedNanos(), result.p95Nanos() / 1_000_000.0,
                result.retries(), result.lockWaitSamples());
    }

    private enum Strategy { ATOMIC, PESSIMISTIC, OPTIMISTIC }
    private record Outcome(boolean accepted, int retries) {}
    private record AttemptResult(boolean accepted, int retries, long elapsedNanos) {}
    private record WorkloadResult(Strategy strategy, int attempts, long accepted, long rejected, long finalQuantity,
                                  int retries, long lockWaitSamples, long elapsedNanos, long p95Nanos) {}
}
