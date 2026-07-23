package com.ngoctri.flashsale.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderView(
        long id,
        UUID customerId,
        long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt) {
}
