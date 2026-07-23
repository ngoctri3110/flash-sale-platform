package com.ngoctri.flashsale.order.api;

import com.ngoctri.flashsale.order.application.OrderView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record OrderResponse(
        long id,
        UUID customerId,
        long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String currency,
        BigDecimal totalAmount,
        Instant createdAt) {

    static OrderResponse from(OrderView order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.productId(),
                order.productName(),
                order.quantity(),
                order.unitPrice(),
                order.currency(),
                order.totalAmount(),
                order.createdAt());
    }
}
