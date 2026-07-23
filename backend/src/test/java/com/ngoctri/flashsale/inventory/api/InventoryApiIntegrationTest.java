package com.ngoctri.flashsale.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        jdbcClient.sql("UPDATE inventories SET available_quantity = 18 WHERE product_id = 1").update();
        jdbcClient.sql("UPDATE inventories SET available_quantity = 12 WHERE product_id = 2").update();
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
        var response = get(path);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/problem+json"));
        assertThat(response.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .contains("\"field\":\"" + field + "\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
