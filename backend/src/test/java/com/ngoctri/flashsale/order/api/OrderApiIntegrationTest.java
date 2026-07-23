package com.ngoctri.flashsale.order.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        jdbcClient.sql("DELETE FROM outbox_events").update();
        jdbcClient.sql("DELETE FROM orders").update();
        jdbcClient.sql("UPDATE inventories SET available_quantity = 18 WHERE product_id = 1").update();
    }

    @Test
    void administratorCanBrowseAcceptedOrdersNewestFirst() throws Exception {
        insertOrder(
                UUID.fromString(CUSTOMER_ID),
                1,
                "Mechanical Keyboard",
                1,
                new BigDecimal("2490000.00"),
                "browse-key-0001",
                Instant.parse("2026-07-23T01:00:00Z"));
        var newestId = insertOrder(
                UUID.fromString(CUSTOMER_ID),
                2,
                "Noise-Cancelling Headphones",
                2,
                new BigDecimal("8990000.00"),
                "browse-key-0002",
                Instant.parse("2026-07-23T02:00:00Z"));

        var response = getOrders("");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/json"));
        JsonNode page = objectMapper.readTree(response.body());
        assertThat(page.path("page").path("number").asInt()).isZero();
        assertThat(page.path("page").path("size").asInt()).isEqualTo(20);
        assertThat(page.path("page").path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("page").path("totalPages").asInt()).isEqualTo(1);
        assertThat(page.path("content").size()).isEqualTo(2);
        var newest = page.path("content").get(0);
        assertThat(newest.path("id").asLong()).isEqualTo(newestId);
        assertThat(newest.path("customerId").asString()).isEqualTo(CUSTOMER_ID);
        assertThat(newest.path("productId").asLong()).isEqualTo(2);
        assertThat(newest.path("productName").asString())
                .isEqualTo("Noise-Cancelling Headphones");
        assertThat(newest.path("quantity").asInt()).isEqualTo(2);
        assertThat(newest.path("unitPrice").decimalValue())
                .isEqualByComparingTo("8990000.00");
        assertThat(newest.path("currency").asString()).isEqualTo("VND");
        assertThat(newest.path("totalAmount").decimalValue())
                .isEqualByComparingTo("17980000.00");
        assertThat(newest.path("createdAt").asString()).isEqualTo("2026-07-23T02:00:00Z");
    }

    @Test
    void administratorCanCombineProductCustomerAndTimeRangeFilters() throws Exception {
        var targetCustomer = UUID.fromString("c440eb9d-71a8-4435-8648-45b5696f9ec6");
        insertOrder(
                UUID.fromString(CUSTOMER_ID),
                1,
                "Mechanical Keyboard",
                1,
                new BigDecimal("2490000.00"),
                "filter-key-0001",
                Instant.parse("2026-07-23T01:00:00Z"));
        var matchingId = insertOrder(
                targetCustomer,
                2,
                "Noise-Cancelling Headphones",
                1,
                new BigDecimal("8990000.00"),
                "filter-key-0002",
                Instant.parse("2026-07-23T02:00:00Z"));
        insertOrder(
                targetCustomer,
                2,
                "Noise-Cancelling Headphones",
                1,
                new BigDecimal("8990000.00"),
                "filter-key-0003",
                Instant.parse("2026-07-23T03:00:00Z"));

        var response = getOrders(
                "?productId=2"
                        + "&customerId=" + targetCustomer
                        + "&createdFrom=2026-07-23T02:00:00Z"
                        + "&createdTo=2026-07-23T03:00:00Z");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = objectMapper.readTree(response.body());
        assertThat(page.path("page").path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").size()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(matchingId);
    }

    @Test
    void orderPaginationUsesIdAsTheStableTieBreaker() throws Exception {
        var createdAt = Instant.parse("2026-07-23T02:00:00Z");
        var olderId = insertOrder(
                UUID.fromString(CUSTOMER_ID),
                1,
                "Mechanical Keyboard",
                1,
                new BigDecimal("2490000.00"),
                "stable-key-0001",
                createdAt);
        var newerId = insertOrder(
                UUID.fromString(CUSTOMER_ID),
                2,
                "Noise-Cancelling Headphones",
                1,
                new BigDecimal("8990000.00"),
                "stable-key-0002",
                createdAt);

        var firstPage = objectMapper.readTree(
                getOrders("?page=0&size=1&sort=createdAt,desc").body());
        var secondPage = objectMapper.readTree(
                getOrders("?page=1&size=1&sort=createdAt,desc").body());

        assertThat(firstPage.path("content").get(0).path("id").asLong()).isEqualTo(newerId);
        assertThat(secondPage.path("content").get(0).path("id").asLong()).isEqualTo(olderId);
        assertThat(secondPage.path("page").path("number").asInt()).isEqualTo(1);
        assertThat(secondPage.path("page").path("size").asInt()).isEqualTo(1);
        assertThat(secondPage.path("page").path("totalPages").asInt()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock = """
                    ?page=-1 | page
                    ?size=0 | size
                    ?size=101 | size
                    ?sort=productId,asc | sort
                    ?productId=0 | productId
                    ?productId= | productId
                    ?customerId=not-a-uuid | customerId
                    ?customerId= | customerId
                    ?createdFrom=not-a-date | createdFrom
                    ?createdFrom= | createdFrom
                    ?createdTo=not-a-date | createdTo
                    ?createdFrom=2026-07-23T03:00:00Z&createdTo=2026-07-23T03:00:00Z | createdTo
                    """)
    void invalidOrderListParametersUseStableFieldErrors(String query, String field)
            throws Exception {
        assertValidationProblem(getOrders(query.trim()), field.trim());
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
    void acceptedOrderCommitsOneVersionedOrderCreatedOutboxEvent() throws Exception {
        var response = postOrder(
                "outbox-order-key-0001",
                """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 2
                }
                """.formatted(CUSTOMER_ID));

        assertThat(response.statusCode()).isEqualTo(201);
        var order = objectMapper.readTree(response.body());
        var event = jdbcClient
                .sql("""
                        SELECT id, event_type, event_version, occurred_at, payload::text
                        FROM outbox_events
                        """)
                .query((resultSet, rowNumber) -> new OutboxEventRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getInt("event_version"),
                        resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        resultSet.getString("payload")))
                .single();

        assertThat(event.eventType()).isEqualTo("ORDER_CREATED");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isNotNull();
        var payload = objectMapper.readTree(event.payload());
        assertThat(payload.path("eventId").asString()).isEqualTo(event.id().toString());
        assertThat(payload.path("eventType").asString()).isEqualTo("ORDER_CREATED");
        assertThat(payload.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(payload.path("occurredAt").asString()).isNotBlank();
        assertThat(payload.path("order").path("id").asLong()).isEqualTo(order.path("id").asLong());
        assertThat(payload.path("order").path("customerId").asString()).isEqualTo(CUSTOMER_ID);
        assertThat(payload.path("order").path("quantity").asInt()).isEqualTo(2);
        assertThat(payload.path("order").path("totalAmount").decimalValue())
                .isEqualByComparingTo("4980000.00");
    }

    @Test
    void identicalRequestCanBeReplayedWithoutAnotherInventoryDeduction() throws Exception {
        var body = """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 2
                }
                """.formatted(CUSTOMER_ID);

        var accepted = postOrder("replay-order-key", body);
        var replayed = postOrder("replay-order-key", body);

        assertThat(accepted.statusCode()).isEqualTo(201);
        assertThat(replayed.statusCode()).isEqualTo(200);
        assertThat(replayed.body()).isEqualTo(accepted.body());
        assertThat(availableQuantity(1)).isEqualTo(16);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(outboxEventCount()).isEqualTo(1);
    }

    @Test
    void reusedKeyWithDifferentInputIsRejectedWithoutAnotherSideEffect() throws Exception {
        var accepted = postOrder(
                "conflicting-order-key",
                """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 1
                }
                """.formatted(CUSTOMER_ID));
        var conflicting = postOrder(
                "conflicting-order-key",
                """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 2
                }
                """.formatted(CUSTOMER_ID));

        assertThat(accepted.statusCode()).isEqualTo(201);
        assertThat(conflicting.statusCode()).isEqualTo(409);
        assertThat(conflicting.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(conflicting.body())
                .contains("\"code\":\"IDEMPOTENCY_KEY_REUSED\"")
                .contains("\"instance\":\"/api/v1/orders\"");
        assertThat(availableQuantity(1)).isEqualTo(17);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(outboxEventCount()).isEqualTo(1);
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
        assertThat(outboxEventCount()).isZero();
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
            assertThat(outboxEventCount()).isZero();
        } finally {
            jdbcClient.sql("DROP TRIGGER IF EXISTS reject_order_insert ON orders").update();
            jdbcClient.sql("DROP FUNCTION IF EXISTS reject_order_insert()").update();
        }
    }

    @Test
    void concurrentOrdersNeverOversellInventory() throws Exception {
        jdbcClient.sql("UPDATE inventories SET available_quantity = 10 WHERE product_id = 1").update();
        var requestCount = 100;
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
            var completedResponses = responses.stream().map(future -> {
                try {
                    return future.get(30, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertThat(completedResponses).filteredOn(response -> response.statusCode() == 201)
                    .hasSize(10);
            assertThat(completedResponses).filteredOn(response -> response.statusCode() == 409)
                    .hasSize(90)
                    .allSatisfy(response ->
                            assertThat(response.body()).contains("\"code\":\"INSUFFICIENT_STOCK\""));
        }

        assertThat(availableQuantity(1)).isZero();
        assertThat(orderCount()).isEqualTo(10);
        assertThat(outboxEventCount()).isEqualTo(10);
    }

    @Test
    void concurrentDuplicateRequestsCreateOneOrderAndDeductInventoryOnce() throws Exception {
        var body = """
                {
                  "customerId": "%s",
                  "productId": 1,
                  "quantity": 2
                }
                """.formatted(CUSTOMER_ID);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responses = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return postOrder("concurrent-replay-key", body);
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

            assertThat(statusCodes).containsExactlyInAnyOrder(201, 200);
        }

        assertThat(availableQuantity(1)).isEqualTo(16);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(outboxEventCount()).isEqualTo(1);
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

    private HttpResponse<String> getOrders(String query) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/orders" + query))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private long insertOrder(
            UUID customerId,
            long productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            String idempotencyKey,
            Instant createdAt) {
        return jdbcClient
                .sql("""
                        INSERT INTO orders (
                            customer_id, product_id, product_name, quantity,
                            unit_price, currency, total_amount, idempotency_key, created_at
                        )
                        VALUES (
                            :customerId, :productId, :productName, :quantity,
                            :unitPrice, 'VND', :totalAmount, :idempotencyKey, :createdAt
                        )
                        RETURNING id
                        """)
                .param("customerId", customerId)
                .param("productId", productId)
                .param("productName", productName)
                .param("quantity", quantity)
                .param("unitPrice", unitPrice)
                .param("totalAmount", unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .param("idempotencyKey", idempotencyKey)
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .query(Long.class)
                .single();
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

    private long outboxEventCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM outbox_events").query(Long.class).single();
    }

    private record OutboxEventRow(
            UUID id,
            String eventType,
            int eventVersion,
            Instant occurredAt,
            String payload) {
    }
}
