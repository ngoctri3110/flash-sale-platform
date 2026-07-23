package com.ngoctri.flashsale.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(properties = {
    "spring.kafka.listener.auto-startup=true",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "logging.level.org.springframework.kafka.listener=DEBUG"
})
class OrderEventConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:4.0.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void cleanUp() {
        jdbcClient.sql("DELETE FROM order_event_audit").update();
        jdbcClient.sql("DELETE FROM processed_events").update();
    }

    @Test
    void validOrderCreatedEventCreatesAnAuditRecord() throws Exception {
        kafkaTemplate.send("order-events.v1", "101", validOrderCreatedEvent()).get();

        awaitUntil(() -> auditCount() == 1);

        var audit = jdbcClient
                .sql("SELECT event_id, order_id, customer_id, total_amount FROM order_event_audit")
                .query((resultSet, rowNumber) -> new Object[] {
                    resultSet.getObject("event_id").toString(),
                    resultSet.getLong("order_id"),
                    resultSet.getObject("customer_id").toString(),
                    resultSet.getBigDecimal("total_amount")
                })
                .single();
        assertThat(audit).containsExactly(
                "d4ac0a84-3d38-4b2e-9828-56e641c98b88",
                101L,
                "d97f2e84-3d38-4b2e-9828-56e641c98b88",
                new java.math.BigDecimal("4980000.00"));
    }

    @Test
    void duplicateDeliveryCreatesOneAuditRecord() throws Exception {
        var event = validOrderCreatedEvent();
        kafkaTemplate.send("order-events.v1", "101", event).get();
        kafkaTemplate.send("order-events.v1", "101", event).get();

        awaitUntil(() -> auditCount() == 1);
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM processed_events").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void unsupportedEventReachesDeadLetterTopicWithoutBlockingValidEvent() throws Exception {
        kafkaTemplate.send("order-events.v1", "bad", validOrderCreatedEvent().replace("ORDER_CREATED", "ORDER_CANCELLED")).get();
        kafkaTemplate.send("order-events.v1", "101", validOrderCreatedEvent()).get();

        awaitUntil(() -> auditCount() == 1);
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(java.util.List.of("order-events.v1.DLT"));
            assertThat(consumer.poll(Duration.ofSeconds(10))).hasSize(1);
        }
    }

    private long auditCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM order_event_audit").query(Long.class).single();
    }

    private void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        var deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private String validOrderCreatedEvent() {
        return """
                {
                  "eventId": "d4ac0a84-3d38-4b2e-9828-56e641c98b88",
                  "eventType": "ORDER_CREATED",
                  "eventVersion": 1,
                  "occurredAt": "2026-07-23T09:00:00Z",
                  "order": {
                    "id": 101,
                    "customerId": "d97f2e84-3d38-4b2e-9828-56e641c98b88",
                    "productId": 1,
                    "productName": "Mechanical Keyboard",
                    "quantity": 2,
                    "unitPrice": 2490000.00,
                    "currency": "VND",
                    "totalAmount": 4980000.00,
                    "createdAt": "2026-07-23T09:00:00Z"
                  }
                }
                """;
    }
}
