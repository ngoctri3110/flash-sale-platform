package com.ngoctri.flashsale.order.infrastructure.persistence;

import com.ngoctri.flashsale.order.application.OrderCreatedOutbox;
import com.ngoctri.flashsale.order.application.OrderView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
class JdbcOrderCreatedOutbox implements OrderCreatedOutbox {

    private static final String EVENT_TYPE = "ORDER_CREATED";
    private static final int EVENT_VERSION = 1;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    JdbcOrderCreatedOutbox(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(OrderView order) {
        var eventId = UUID.randomUUID();
        jdbcClient
                .sql("""
                        INSERT INTO outbox_events (
                            id, aggregate_type, aggregate_id, event_type, event_version,
                            occurred_at, payload
                        )
                        VALUES (
                            :id, 'ORDER', :aggregateId, :eventType, :eventVersion,
                            :occurredAt, CAST(:payload AS jsonb)
                        )
                        """)
                .param("id", eventId)
                .param("aggregateId", order.id())
                .param("eventType", EVENT_TYPE)
                .param("eventVersion", EVENT_VERSION)
                .param("occurredAt", OffsetDateTime.ofInstant(order.createdAt(), ZoneOffset.UTC))
                .param("payload", payload(eventId, order))
                .update();
    }

    private String payload(UUID eventId, OrderView order) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", EVENT_TYPE,
                    "eventVersion", EVENT_VERSION,
                    "occurredAt", order.createdAt(),
                    "order", order));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize Order Created outbox event", exception);
        }
    }
}
