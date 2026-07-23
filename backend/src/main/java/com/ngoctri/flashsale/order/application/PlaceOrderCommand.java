package com.ngoctri.flashsale.order.application;

import java.util.UUID;

public record PlaceOrderCommand(
        UUID customerId,
        long productId,
        int quantity,
        String idempotencyKey) {
}
