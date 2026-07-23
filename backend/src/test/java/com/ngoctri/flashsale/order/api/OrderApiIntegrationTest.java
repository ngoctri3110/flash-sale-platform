package com.ngoctri.flashsale.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiIntegrationTest {

    private static final String CUSTOMER_ID = "d97f2e84-3d38-4b2e-9828-56e641c98b88";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void restoreSeedData() {
        jdbcClient.sql("DELETE FROM orders").update();
        jdbcClient.sql("UPDATE inventories SET available_quantity = 18 WHERE product_id = 1").update();
    }

    @Test
    void customerCanPlaceAnOrderAtTheAcceptedPrice() throws Exception {
        var response = postOrder(
                "order-key-0001",
                """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 2
                }
                """.formatted(CUSTOMER_ID));

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode order = objectMapper.readTree(response.body());
        assertThat(order.path("id").asLong()).isPositive();
        assertThat(response.headers().firstValue("Location"))
                .hasValue("/api/v1/orders/" + order.path("id").asLong());
        assertThat(order.path("customerId").asString()).isEqualTo(CUSTOMER_ID);
        assertThat(order.path("productId").asLong()).isEqualTo(1);
        assertThat(order.path("productName").asString()).isEqualTo("Mechanical Keyboard");
        assertThat(order.path("quantity").asInt()).isEqualTo(2);
        assertThat(order.path("unitPrice").decimalValue()).isEqualByComparingTo("2490000.00");
        assertThat(order.path("currency").asString()).isEqualTo("VND");
        assertThat(order.path("totalAmount").decimalValue()).isEqualByComparingTo("4980000.00");
        assertThat(order.path("createdAt").asString()).isNotBlank();
        assertThat(availableQuantity(1)).isEqualTo(16);

        var persistedSnapshot = jdbcClient
                .sql("""
                        SELECT product_name, unit_price, currency, total_amount
                        FROM orders
                        WHERE id = ?
                        """)
                .param(order.path("id").asLong())
                .query((resultSet, rowNumber) -> new Object[] {
                    resultSet.getString("product_name"),
                    resultSet.getBigDecimal("unit_price"),
                    resultSet.getString("currency"),
                    resultSet.getBigDecimal("total_amount")
                })
                .single();
        assertThat(persistedSnapshot).containsExactly(
                "Mechanical Keyboard",
                new BigDecimal("2490000.00"),
                "VND",
                new BigDecimal("4980000.00"));
    }

    @Test
    void insufficientInventoryRejectsTheAttemptWithoutAnySideEffect() throws Exception {
        jdbcClient.sql("UPDATE inventories SET available_quantity = 3 WHERE product_id = 1").update();
        var response = postOrder(
                "order-key-0002",
                """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 4
                }
                """.formatted(CUSTOMER_ID));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"INSUFFICIENT_STOCK\"");
        assertThat(availableQuantity(1)).isEqualTo(3);
        assertThat(orderCount()).isZero();
    }

    @Test
    void inactiveProductIsNotAvailableForOrdering() throws Exception {
        var response = postOrder(
                "order-key-0003",
                """
                {
                  "customerId": "%s",
                  "productId": 4,
                  "quantity": 1
                }
                """.formatted(CUSTOMER_ID));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"PRODUCT_NOT_AVAILABLE\"");
        assertThat(availableQuantity(4)).isZero();
        assertThat(orderCount()).isZero();
    }

    @Test
    void missingProductUsesThePublicProblemDetailsContract() throws Exception {
        var response = postOrder(
                "order-key-0004",
                """
                {
                  "customerId": "%s",
                  "productId": 999999,
                  "quantity": 1
                }
                """.formatted(CUSTOMER_ID));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("\"code\":\"PRODUCT_NOT_FOUND\"")
                .contains("\"instance\":\"/api/v1/orders\"");
        assertThat(orderCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "'{\"customerId\":null,\"productId\":1,\"quantity\":1}', customerId",
        "'{\"customerId\":\"d97f2e84-3d38-4b2e-9828-56e641c98b88\",\"productId\":0,\"quantity\":1}', productId",
        "'{\"customerId\":\"d97f2e84-3d38-4b2e-9828-56e641c98b88\",\"productId\":1,\"quantity\":0}', quantity",
        "'{\"customerId\":\"d97f2e84-3d38-4b2e-9828-56e641c98b88\",\"productId\":1,\"quantity\":6}', quantity",
        "'{\"customerId\":\"d97f2e84-3d38-4b2e-9828-56e641c98b88\",\"productId\":1,\"quantity\":null}', quantity"
    })
    void invalidOrderRequestUsesStableFieldErrors(String body, String field) throws Exception {
        assertValidationProblem(postOrder("order-key-0005", body), field);
        assertThat(orderCount()).isZero();
        assertThat(availableQuantity(1)).isEqualTo(18);
    }

    @Test
    void invalidIdempotencyKeyIsRejected() throws Exception {
        var body = """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 1
                }
                """.formatted(CUSTOMER_ID);

        assertValidationProblem(postOrder("1234567", body), "idempotencyKey");
        assertValidationProblem(postOrder("x".repeat(129), body), "idempotencyKey");
        assertThat(orderCount()).isZero();
        assertThat(availableQuantity(1)).isEqualTo(18);
    }

    @Test
    void idempotencyHeaderIsRequired() throws Exception {
        var response = postOrder(
                null,
                """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 1
                }
                """.formatted(CUSTOMER_ID));

        assertValidationProblem(response, "Idempotency-Key");
        assertThat(orderCount()).isZero();
        assertThat(availableQuantity(1)).isEqualTo(18);
    }

    @Test
    void inventoryDecrementRollsBackWhenOrderInsertionFails() throws Exception {
        jdbcClient.sql("""
                CREATE FUNCTION reject_order_insert()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'order persistence unavailable';
                END;
                $$
                """).update();
        jdbcClient.sql("""
                CREATE TRIGGER reject_order_insert
                BEFORE INSERT ON orders
                FOR EACH ROW
                EXECUTE FUNCTION reject_order_insert()
                """).update();

        try {
            var response = postOrder(
                    "order-key-0006",
                    """
                    {
                      "customerId": "%s",
                      "productId": 1,
                      "quantity": 2
                    }
                    """.formatted(CUSTOMER_ID));

            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"");
            assertThat(availableQuantity(1)).isEqualTo(18);
            assertThat(orderCount()).isZero();
        } finally {
            jdbcClient.sql("DROP TRIGGER IF EXISTS reject_order_insert ON orders").update();
            jdbcClient.sql("DROP FUNCTION IF EXISTS reject_order_insert()").update();
        }
    }

    @Test
    void concurrentOrdersNeverOversellInventory() throws Exception {
        jdbcClient.sql("UPDATE inventories SET available_quantity = 10 WHERE product_id = 1").update();
        var requestCount = 30;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responses = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return postOrder(
                                "concurrent-order-" + index,
                                """
                                {
                                  "customerId": "%s",
                                  "productId": 1,
                                  "quantity": 1
                                }
                                """.formatted(new UUID(0, index + 1)));
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var statusCodes = responses.stream().map(future -> {
                try {
                    return future.get(30, TimeUnit.SECONDS).statusCode();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertThat(statusCodes).filteredOn(status -> status == 201).hasSize(10);
            assertThat(statusCodes).filteredOn(status -> status == 409).hasSize(20);
        }

        assertThat(availableQuantity(1)).isZero();
        assertThat(orderCount()).isEqualTo(10);
    }

    private HttpResponse<String> postOrder(String idempotencyKey, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long availableQuantity(long productId) {
        return jdbcClient
                .sql("SELECT available_quantity FROM inventories WHERE product_id = ?")
                .param(productId)
                .query(Long.class)
                .single();
    }

    private void assertValidationProblem(HttpResponse<String> response, String field) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(response.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .contains("\"field\":\"" + field + "\"");
    }

    private long orderCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM orders").query(Long.class).single();
    }
}
