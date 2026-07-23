package com.ngoctri.flashsale.messaging.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record OrderCreatedEvent(
        UUID eventId, long orderId, UUID customerId, BigDecimal totalAmount, Instant occurredAt) {
}
