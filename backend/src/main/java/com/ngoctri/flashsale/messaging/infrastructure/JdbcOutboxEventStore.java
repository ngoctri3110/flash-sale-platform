package com.ngoctri.flashsale.messaging.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcOutboxEventStore {

    private final JdbcClient jdbcClient;

    JdbcOutboxEventStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public List<OutboxEvent> claimPending(int batchSize, UUID leaseOwner, Duration leaseDuration) {
        return jdbcClient
                .sql("""
                        WITH candidates AS (
                            SELECT id
                            FROM outbox_events
                            WHERE published_at IS NULL
                              AND next_attempt_at <= CURRENT_TIMESTAMP
                              AND (lease_expires_at IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP)
                            ORDER BY occurred_at, id
                            LIMIT :batchSize
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE outbox_events event
                        SET lease_owner = :leaseOwner,
                            lease_expires_at = CURRENT_TIMESTAMP + CAST(:leaseDuration AS interval)
                        FROM candidates
                        WHERE event.id = candidates.id
                        RETURNING event.id, event.aggregate_id, event.payload::text
                        """)
                .param("batchSize", batchSize)
                .param("leaseOwner", leaseOwner)
                .param("leaseDuration", leaseDuration.toSeconds() + " seconds")
                .query((resultSet, rowNumber) -> new OutboxEvent(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getLong("aggregate_id"),
                        resultSet.getString("payload")))
                .list();
    }

    void markPublished(OutboxEvent event, UUID leaseOwner) {
        jdbcClient
                .sql("""
                        UPDATE outbox_events
                        SET published_at = CURRENT_TIMESTAMP,
                            last_error = NULL,
                            lease_owner = NULL,
                            lease_expires_at = NULL
                        WHERE id = :id
                          AND lease_owner = :leaseOwner
                        """)
                .param("id", event.id())
                .param("leaseOwner", leaseOwner)
                .update();
    }

    void recordFailure(OutboxEvent event, UUID leaseOwner, Exception exception, Duration retryDelay) {
        jdbcClient
                .sql("""
                        UPDATE outbox_events
                        SET publish_attempts = publish_attempts + 1,
                            last_error = :lastError,
                            next_attempt_at = CURRENT_TIMESTAMP + CAST(:retryDelay AS interval),
                            lease_owner = NULL,
                            lease_expires_at = NULL
                        WHERE id = :id
                          AND lease_owner = :leaseOwner
                        """)
                .param("id", event.id())
                .param("leaseOwner", leaseOwner)
                .param("lastError", exception.getClass().getSimpleName())
                .param("retryDelay", retryDelay.toSeconds() + " seconds")
                .update();
    }
}
