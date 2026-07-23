package com.ngoctri.flashsale.messaging.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
class JdbcOrderEventAuditStore {

    private final JdbcClient jdbcClient;

    JdbcOrderEventAuditStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    void record(OrderCreatedEvent event) {
        var processed = jdbcClient
                .sql("""
                        INSERT INTO processed_events (event_id, event_type)
                        VALUES (:eventId, 'ORDER_CREATED')
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", event.eventId())
                .update();
        if (processed == 0) {
            return;
        }

        jdbcClient
                .sql("""
                        INSERT INTO order_event_audit (
                            event_id, order_id, customer_id, total_amount, occurred_at
                        )
                        VALUES (:eventId, :orderId, :customerId, :totalAmount, :occurredAt)
                        """)
                .param("eventId", event.eventId())
                .param("orderId", event.orderId())
                .param("customerId", event.customerId())
                .param("totalAmount", event.totalAmount())
                .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                .update();
    }
}
