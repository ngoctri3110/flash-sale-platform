package com.ngoctri.flashsale.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
class InventoryApiIntegrationTest {

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
    void restoreSeedQuantity() {
        jdbcClient.sql("DELETE FROM inventory_adjustments").update();
        jdbcClient.sql("UPDATE inventories SET available_quantity = 18 WHERE product_id = 1").update();
        jdbcClient.sql("UPDATE inventories SET available_quantity = 12 WHERE product_id = 2").update();
    }

    @Test
    void administratorCanApplyAReasonedInventoryAdjustment() throws Exception {
        var response = post(
                "/api/v1/inventories/1/adjustments",
                """
                {
                  "quantityDelta": 7,
                  "reason": "Received supplier shipment"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode adjusted = objectMapper.readTree(response.body());
        assertThat(adjusted.path("productId").asLong()).isEqualTo(1);
        assertThat(adjusted.path("productName").asString()).isEqualTo("Mechanical Keyboard");
        assertThat(adjusted.path("availableQuantity").asLong()).isEqualTo(25);
        assertThat(adjusted.path("updatedAt").asString()).isNotBlank();

        JsonNode inventory = objectMapper.readTree(
                get("/api/v1/inventories?sort=productId,asc").body());
        assertThat(inventory.at("/content/0/availableQuantity").asLong()).isEqualTo(25);

        var audit = jdbcClient
                .sql("""
                        SELECT quantity_delta, reason, resulting_available_quantity
                        FROM inventory_adjustments
                        WHERE product_id = 1
                        """)
                .query((resultSet, rowNumber) -> new Object[] {
                    resultSet.getLong("quantity_delta"),
                    resultSet.getString("reason"),
                    resultSet.getLong("resulting_available_quantity")
                })
                .single();
        assertThat(audit).containsExactly(7L, "Received supplier shipment", 25L);
    }

    @ParameterizedTest
    @CsvSource({
        "'{\"quantityDelta\":0,\"reason\":\"Cycle count\"}', quantityDelta",
        "'{\"quantityDelta\":null,\"reason\":\"Cycle count\"}', quantityDelta",
        "'{\"quantityDelta\":1,\"reason\":null}', reason",
        "'{\"quantityDelta\":1,\"reason\":\"ab\"}', reason",
        "'{\"quantityDelta\":1,\"reason\":\"   \"}', reason"
    })
    void invalidInventoryAdjustmentsUseStableFieldErrors(String body, String field)
            throws Exception {
        assertValidationProblem(post("/api/v1/inventories/1/adjustments", body), field);
    }

    @Test
    void overlongAdjustmentReasonIsRejected() throws Exception {
        assertValidationProblem(
                post(
                        "/api/v1/inventories/1/adjustments",
                        """
                        {
                          "quantityDelta": 1,
                          "reason": "%s"
                        }
                        """.formatted("R".repeat(201))),
                "reason");
    }

    @Test
    void adjustmentCannotMakeAvailableQuantityNegative() throws Exception {
        var response = post(
                "/api/v1/inventories/1/adjustments",
                """
                {
                  "quantityDelta": -19,
                  "reason": "Correcting a count error"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(response.body())
                .contains("\"code\":\"INSUFFICIENT_STOCK\"")
                .contains("\"instance\":\"/api/v1/inventories/1/adjustments\"");
        assertThat(availableQuantity(1)).isEqualTo(18);
        assertThat(adjustmentCount(1)).isZero();
    }

    @Test
    void adjustmentForMissingProductUsesThePublicProblemDetailsContract() throws Exception {
        var response = post(
                "/api/v1/inventories/999999/adjustments",
                """
                {
                  "quantityDelta": 1,
                  "reason": "Opening inventory"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("\"code\":\"PRODUCT_NOT_FOUND\"")
                .contains("\"instance\":\"/api/v1/inventories/999999/adjustments\"");
    }

    @Test
    void concurrentAdjustmentsPreserveTheNonNegativeInvariantAndAuditEverySuccess()
            throws Exception {
        jdbcClient.sql("UPDATE inventories SET available_quantity = 10 WHERE product_id = 1").update();
        var requestCount = 30;
        var ready = new CountDownLatch(requestCount);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responses = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return post(
                                "/api/v1/inventories/1/adjustments",
                                """
                                {
                                  "quantityDelta": -1,
                                  "reason": "Concurrent cycle count"
                                }
                                """);
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var statusCodes = responses.stream().map(future -> {
                try {
                    return future.get(20, TimeUnit.SECONDS).statusCode();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();

            assertThat(statusCodes).filteredOn(status -> status == 200).hasSize(10);
            assertThat(statusCodes).filteredOn(status -> status == 409).hasSize(20);
        }

        assertThat(availableQuantity(1)).isZero();
        assertThat(adjustmentCount(1)).isEqualTo(10);
    }

    @Test
    void administratorCanPageInventoryUsingThePublicContract() throws Exception {
        var response = get("/api/v1/inventories?page=1&size=2&sort=productId,asc");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.at("/content/0/productId").asLong()).isEqualTo(3);
        assertThat(body.at("/content/0/productName").asString()).isEqualTo("Portable SSD 1TB");
        assertThat(body.at("/page/number").asInt()).isEqualTo(1);
        assertThat(body.at("/page/size").asInt()).isEqualTo(2);
        assertThat(body.at("/page/totalElements").asLong()).isEqualTo(5);
        assertThat(body.at("/page/totalPages").asInt()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
        "'productId,asc', 1",
        "'productId,desc', 5",
        "'productName,asc', 5",
        "'productName,desc', 4",
        "'availableQuantity,asc', 4",
        "'availableQuantity,desc', 3"
    })
    void documentedInventorySortsAreAvailable(String sort, long expectedProductId)
            throws Exception {
        var body = objectMapper.readTree(get("/api/v1/inventories?sort=" + sort).body());

        assertThat(body.at("/content/0/productId").asLong()).isEqualTo(expectedProductId);
    }

    @Test
    void productIdIsTheStableInventoryTieBreaker() throws Exception {
        jdbcClient.sql("UPDATE inventories SET available_quantity = 12 WHERE product_id = 1").update();

        var body = objectMapper.readTree(
                get("/api/v1/inventories?sort=availableQuantity,asc").body());

        assertThat(body.at("/content/1/productId").asLong()).isEqualTo(1);
        assertThat(body.at("/content/2/productId").asLong()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
        "'/api/v1/inventories?page=-1', page",
        "'/api/v1/inventories?page=', page",
        "'/api/v1/inventories?size=0', size",
        "'/api/v1/inventories?size=101', size",
        "'/api/v1/inventories?sort=id,asc', sort"
    })
    void invalidInventoryListParametersUseStableFieldErrors(String path, String field)
            throws Exception {
        assertValidationProblem(get(path), field);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertValidationProblem(HttpResponse<String> response, String field) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(response.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .contains("\"field\":\"" + field + "\"");
    }

    private long availableQuantity(long productId) {
        return jdbcClient
                .sql("SELECT available_quantity FROM inventories WHERE product_id = ?")
                .param(productId)
                .query(Long.class)
                .single();
    }

    private long adjustmentCount(long productId) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM inventory_adjustments WHERE product_id = ?")
                .param(productId)
                .query(Long.class)
                .single();
    }
}
