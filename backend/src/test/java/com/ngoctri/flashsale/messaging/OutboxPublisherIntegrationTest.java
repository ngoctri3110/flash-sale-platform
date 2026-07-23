package com.ngoctri.flashsale.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.ngoctri.flashsale.messaging.infrastructure.OrderCreatedOutboxPublisher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.outbox.publisher.enabled=true",
            "spring.task.scheduling.enabled=false"
        })
class OutboxPublisherIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.0.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderCreatedOutboxPublisher publisher;

    @AfterEach
    void restoreSeedData() {
        jdbcClient.sql("DELETE FROM outbox_events").update();
        jdbcClient.sql("DELETE FROM orders").update();
        jdbcClient.sql("UPDATE inventories SET available_quantity = 18 WHERE product_id = 1").update();
    }

    @Test
    void publisherRetriesAnOrderCreatedEventAfterKafkaRecovers() throws Exception {
        var firstResponse = postOrder("kafka-outbox-key-0001");
        assertThat(firstResponse.statusCode()).isEqualTo(201);
        var firstOrderId = objectMapper.readTree(firstResponse.body()).path("id").asLong();

        publisher.publishPendingEvents();
        assertThat(readPublishedEvent(firstOrderId).key()).isEqualTo(Long.toString(firstOrderId));

        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            var responseWhileKafkaIsUnavailable = postOrder("kafka-outbox-key-0002");
            assertThat(responseWhileKafkaIsUnavailable.statusCode()).isEqualTo(201);

            publisher.publishPendingEvents();
            assertThat(jdbcClient
                            .sql("""
                                    SELECT publish_attempts, published_at IS NULL, last_error IS NOT NULL
                                    FROM outbox_events
                                    WHERE published_at IS NULL
                                    """)
                            .query((resultSet, rowNumber) -> new Object[] {
                                resultSet.getInt("publish_attempts"),
                                resultSet.getBoolean(2),
                                resultSet.getBoolean(3)
                            })
                            .single())
                    .containsExactly(1, true, true);
        } finally {
            KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }

        jdbcClient
                .sql("UPDATE outbox_events SET next_attempt_at = CURRENT_TIMESTAMP WHERE published_at IS NULL")
                .update();
        publisher.publishPendingEvents();

        assertThat(jdbcClient
                        .sql("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NOT NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    private org.apache.kafka.clients.consumer.ConsumerRecord<String, String> readPublishedEvent(long orderId) {
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(java.util.List.of("order-events.v1"));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).hasSize(1);
            var record = records.iterator().next();
            var event = objectMapper.readTree(record.value());
            assertThat(event.path("eventType").asString()).isEqualTo("ORDER_CREATED");
            assertThat(event.path("order").path("id").asLong()).isEqualTo(orderId);
            return record;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private HttpResponse<String> postOrder(String idempotencyKey) throws Exception {
        var body = """
                {
                  "customerId": "d97f2e84-3d38-4b2e-9828-56e641c98b88",
                  "productId": 1,
                  "quantity": 1
                }
                """;
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/orders"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
