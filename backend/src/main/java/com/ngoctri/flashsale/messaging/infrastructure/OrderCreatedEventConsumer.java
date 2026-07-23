package com.ngoctri.flashsale.messaging.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Component
class OrderCreatedEventConsumer {

    private static final String EVENT_TYPE = "ORDER_CREATED";
    private static final int EVENT_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final JdbcOrderEventAuditStore auditStore;

    OrderCreatedEventConsumer(ObjectMapper objectMapper, JdbcOrderEventAuditStore auditStore) {
        this.objectMapper = objectMapper;
        this.auditStore = auditStore;
    }

    @KafkaListener(
            topics = "order-events.v1",
            groupId = "order-event-audit.v1",
            containerFactory = "orderEventsKafkaListenerContainerFactory")
    @Transactional
    public void consume(String payload) {
        auditStore.record(parse(payload));
    }

    private OrderCreatedEvent parse(String payload) {
        try {
            var root = objectMapper.readTree(payload);
            if (!EVENT_TYPE.equals(root.path("eventType").asString())
                    || root.path("eventVersion").asInt() != EVENT_VERSION) {
                throw new UnsupportedOrderEventException("Unsupported Order event type or version");
            }
            var order = root.path("order");
            return new OrderCreatedEvent(
                    UUID.fromString(root.path("eventId").asString()),
                    order.path("id").asLong(),
                    UUID.fromString(order.path("customerId").asString()),
                    order.path("totalAmount").decimalValue(),
                    Instant.parse(root.path("occurredAt").asString()));
        } catch (UnsupportedOrderEventException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnsupportedOrderEventException("Invalid Order event envelope");
        }
    }
}
