package com.ngoctri.flashsale.order.api;

import com.ngoctri.flashsale.order.application.PlaceOrderCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotNull @Min(1) Long productId,
        @NotNull @Min(1) @Max(5) Integer quantity) {

    PlaceOrderCommand toCommand(String idempotencyKey) {
        return new PlaceOrderCommand(customerId, productId, quantity, idempotencyKey);
    }
}
