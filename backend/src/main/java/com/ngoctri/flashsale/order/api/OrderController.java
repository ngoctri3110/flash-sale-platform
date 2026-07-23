package com.ngoctri.flashsale.order.api;

import com.ngoctri.flashsale.order.application.PlaceOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final PlaceOrder placeOrder;

    OrderController(PlaceOrder placeOrder) {
        this.placeOrder = placeOrder;
    }

    @PostMapping
    ResponseEntity<OrderResponse> placeOrder(
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 128) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        var result = placeOrder.place(request.toCommand(idempotencyKey));
        var order = OrderResponse.from(result.order());
        if (!result.created()) {
            return ResponseEntity.ok(order);
        }
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + order.id()))
                .body(order);
    }
}
