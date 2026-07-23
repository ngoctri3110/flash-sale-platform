package com.ngoctri.flashsale.product.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("local")
@Import(ProductApiIntegrationTest.FailingController.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void customerCanBrowseSeededActiveProducts() throws Exception {
        var response = get("/api/v1/products");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/json"));

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.at("/page/number").asInt()).isZero();
        assertThat(body.at("/page/size").asInt()).isEqualTo(20);
        assertThat(body.at("/page/totalElements").asLong()).isEqualTo(4);
        assertThat(body.at("/content").size()).isEqualTo(4);
        assertThat(body.at("/content/0/name").asString()).isEqualTo("Mechanical Keyboard");
        assertThat(body.at("/content/1/name").asString()).isEqualTo("Noise-Cancelling Headphones");
        assertThat(body.at("/content/2/name").asString()).isEqualTo("Portable SSD 1TB");
        assertThat(body.at("/content/3/name").asString()).isEqualTo("Ergonomic Mouse");
        assertThat(body.at("/content/0/active").asBoolean()).isTrue();
    }

    @Test
    void customerCanPageAndSortProductsUsingTheAllowlist() throws Exception {
        var response = get("/api/v1/products?page=0&size=2&sort=price,desc");

        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.at("/page/number").asInt()).isZero();
        assertThat(body.at("/page/size").asInt()).isEqualTo(2);
        assertThat(body.at("/page/totalElements").asLong()).isEqualTo(4);
        assertThat(body.at("/page/totalPages").asInt()).isEqualTo(2);
        assertThat(body.at("/content/0/name").asString()).isEqualTo("Noise-Cancelling Headphones");
        assertThat(body.at("/content/1/name").asString()).isEqualTo("Mechanical Keyboard");
    }

    @Test
    void customerCanViewProductDetailsAndAvailability() throws Exception {
        var response = get("/api/v1/products/4");

        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("id").asLong()).isEqualTo(4);
        assertThat(body.path("name").asString()).isEqualTo("Smart Desk Lamp");
        assertThat(body.path("description").asString())
                .isEqualTo("An adjustable desk lamp currently unavailable for ordering.");
        assertThat(body.path("price").decimalValue()).isEqualByComparingTo("1290000.00");
        assertThat(body.path("currency").asString()).isEqualTo("VND");
        assertThat(body.path("active").asBoolean()).isFalse();
        assertThat(body.path("createdAt").asString()).isNotBlank();
        assertThat(body.path("updatedAt").asString()).isNotBlank();
    }

    @Test
    void missingProductUsesThePublicProblemDetailsContract() throws Exception {
        var response = get("/api/v1/products/999999");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("type").asString()).isEqualTo("https://flash-sale.local/problems/product-not-found");
        assertThat(body.path("title").asString()).isEqualTo("Product not found");
        assertThat(body.path("status").asInt()).isEqualTo(404);
        assertThat(body.path("detail").asString()).isEqualTo("Product 999999 was not found");
        assertThat(body.path("instance").asString()).isEqualTo("/api/v1/products/999999");
        assertThat(body.path("code").asString()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(body.path("traceId").asString()).isNotBlank();
    }

    @Test
    void pageSizeAboveThePublicLimitIsRejected() throws Exception {
        var response = get("/api/v1/products?size=101");

        assertValidationProblem(response, "size");
    }

    @ParameterizedTest
    @CsvSource({
        "'/api/v1/products?page=-1', page",
        "'/api/v1/products?size=0', size",
        "'/api/v1/products?page=', page",
        "'/api/v1/products?size=', size"
    })
    void invalidPagingInputsAreRejected(String path, String field) throws Exception {
        assertValidationProblem(get(path), field);
    }

    @Test
    void sortOutsideTheAllowlistIsRejected() throws Exception {
        assertValidationProblem(get("/api/v1/products?sort=inventory,desc"), "sort");
    }

    @Test
    void blankSortIsRejected() throws Exception {
        assertValidationProblem(get("/api/v1/products?sort="), "sort");
    }

    @Test
    void nonNumericPagingInputIsRejected() throws Exception {
        assertValidationProblem(get("/api/v1/products?page=abc"), "page");
    }

    @ParameterizedTest
    @CsvSource({
        "'name,asc', 'Ergonomic Mouse'",
        "'name,desc', 'Portable SSD 1TB'",
        "'price,asc', 'Portable SSD 1TB'",
        "'price,desc', 'Noise-Cancelling Headphones'",
        "'createdAt,asc', 'Mechanical Keyboard'",
        "'createdAt,desc', 'Mechanical Keyboard'"
    })
    void documentedSortAllowlistIsAvailable(String sort, String expectedFirstProduct) throws Exception {
        var response = get("/api/v1/products?sort=" + sort);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.at("/content/0/name").asString()).isEqualTo(expectedFirstProduct);
    }

    @Test
    void productIdIsTheStableTieBreaker() throws Exception {
        var response = get("/api/v1/products?sort=price,desc");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.at("/content/1/id").asLong()).isEqualTo(1);
        assertThat(body.at("/content/2/id").asLong()).isEqualTo(5);
        assertThat(body.at("/content/1/price").decimalValue())
                .isEqualByComparingTo(body.at("/content/2/price").decimalValue());
    }

    @Test
    void unexpectedFailureUsesTheSanitizedProblemDetailsContract() throws Exception {
        var response = get("/test/failure");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.path("detail").asString()).isEqualTo("An unexpected error occurred");
        assertThat(body.path("detail").asString()).doesNotContain("sensitive internal detail");
        assertThat(response.headers().firstValue("X-Trace-Id"))
                .contains(body.path("traceId").asString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertValidationProblem(HttpResponse<String> response, String field) throws Exception {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/problem+json"));

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(400);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.path("traceId").asString()).isNotBlank();
        assertThat(response.headers().firstValue("X-Trace-Id"))
                .contains(body.path("traceId").asString());
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.path("fieldErrors").toString()).contains("\"field\":\"" + field + "\"");
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/failure")
        void fail() {
            throw new IllegalStateException("sensitive internal detail");
        }
    }
}
