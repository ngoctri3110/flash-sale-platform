package com.ngoctri.flashsale.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngoctri.flashsale.FlashSaleApplication;
import com.ngoctri.flashsale.product.application.ProductPageQuery;
import com.ngoctri.flashsale.product.application.ProductSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityGraph;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(
        classes = {FlashSaleApplication.class, NPlusOneQueryLabIntegrationTest.LabEntityConfiguration.class},
        properties = {
            "spring.jpa.properties.hibernate.generate_statistics=true"
        })
class NPlusOneQueryLabIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private JpaProductRepositoryAdapter productCatalog;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        jdbcClient.sql("DELETE FROM outbox_events").update();
        jdbcClient.sql("DELETE FROM orders").update();
        for (var productId = 1; productId <= 4; productId++) {
            insertOrder(productId);
        }
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM outbox_events").update();
        jdbcClient.sql("DELETE FROM orders").update();
    }

    @Test
    @Transactional
    void lazyAssociationLoopReproducesNPlusOne() {
        var statements = observe(() -> entityManager
                .createQuery("SELECT order FROM OrderLazyLabEntity order ORDER BY order.id", OrderLazyLabEntity.class)
                .getResultList()
                .forEach(order -> order.getProduct().getName()));

        assertThat(statements).isGreaterThan(2);
    }

    @Test
    @Transactional
    void fetchJoinLoadsTheBoundedDetailGraphInOneStatement() {
        var statements = observe(() -> entityManager
                .createQuery(
                        "SELECT order FROM OrderFetchLabEntity order JOIN FETCH order.product ORDER BY order.id",
                        OrderFetchLabEntity.class)
                .getResultList()
                .forEach(order -> order.getProduct().getName()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @Transactional
    void entityGraphLoadsTheBoundedDetailGraphInOneStatement() {
        EntityGraph<OrderFetchLabEntity> graph = entityManager.createEntityGraph(OrderFetchLabEntity.class);
        graph.addAttributeNodes("product");

        var statements = observe(() -> entityManager
                .createQuery("SELECT order FROM OrderFetchLabEntity order ORDER BY order.id", OrderFetchLabEntity.class)
                .setHint("jakarta.persistence.fetchgraph", graph)
                .getResultList()
                .forEach(order -> order.getProduct().getName()));

        assertThat(statements).isEqualTo(1);
    }

    @Test
    @Transactional
    void batchFetchingBoundsLazyAssociationStatements() {
        var statements = observe(() -> entityManager
                .createQuery("SELECT order FROM OrderFetchLabEntity order ORDER BY order.id", OrderFetchLabEntity.class)
                .getResultList()
                .forEach(order -> order.getProduct().getName()));

        assertThat(statements).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void dtoProjectionSelectsOnlyListFieldsInOneStatement() {
        var statements = observe(() -> entityManager
                .createQuery(
                        "SELECT new com.ngoctri.flashsale.product.infrastructure.persistence.NPlusOneQueryLabIntegrationTest$OrderListRow("
                                + "order.id, order.product.name) FROM OrderFetchLabEntity order ORDER BY order.id",
                        OrderListRow.class)
                .getResultList());

        assertThat(statements).isEqualTo(1);
    }

    @Test
    void productionProductListHasABoundedStatementBudget() {
        entityManager.clear();
        statistics.clear();

        var page = productCatalog.findProducts(new ProductPageQuery(0, 3, ProductSort.ID_ASC));

        assertThat(page.content()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    private long observe(Runnable query) {
        entityManager.clear();
        statistics.clear();
        query.run();
        return statistics.getPrepareStatementCount();
    }

    private void insertOrder(long productId) {
        var unitPrice = new BigDecimal("100000.00");
        jdbcClient
                .sql("""
                        INSERT INTO orders (
                            customer_id, product_id, product_name, quantity,
                            unit_price, currency, total_amount, idempotency_key, created_at
                        ) VALUES (
                            :customerId, :productId, :productName, 1,
                            :unitPrice, 'VND', :totalAmount, :idempotencyKey, :createdAt
                        )
                        """)
                .param("customerId", UUID.randomUUID())
                .param("productId", productId)
                .param("productName", "Lab Product " + productId)
                .param("unitPrice", unitPrice)
                .param("totalAmount", unitPrice)
                .param("idempotencyKey", "n-plus-one-lab-" + productId)
                .param("createdAt", OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
                .update();
    }

    record OrderListRow(long orderId, String productName) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {FlashSaleApplication.class, OrderFetchLabEntity.class})
    static class LabEntityConfiguration {
    }
}
