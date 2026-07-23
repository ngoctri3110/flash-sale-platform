package com.ngoctri.flashsale.order.api;

import com.ngoctri.flashsale.order.application.OrderCatalog;
import com.ngoctri.flashsale.order.application.OrderSort;
import com.ngoctri.flashsale.order.application.PlaceOrder;
import com.ngoctri.flashsale.order.application.UnsupportedOrderSortException;
import com.ngoctri.flashsale.shared.api.InvalidListParameterException;
import com.ngoctri.flashsale.shared.api.ListParameters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final PlaceOrder placeOrder;
    private final OrderCatalog orderCatalog;

    OrderController(PlaceOrder placeOrder, OrderCatalog orderCatalog) {
        this.placeOrder = placeOrder;
        this.orderCatalog = orderCatalog;
    }

    @GetMapping
    OrderPageResponse listOrders(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo) {
        var parsedProductId = ListParameters.parseOptionalLong(
                "productId", productId, 1, Long.MAX_VALUE);
        var parsedCustomerId = ListParameters.parseOptionalUuid("customerId", customerId);
        var parsedCreatedFrom = ListParameters.parseOptionalInstant(
                "createdFrom", createdFrom);
        var parsedCreatedTo = ListParameters.parseOptionalInstant("createdTo", createdTo);
        validateTimeRange(parsedCreatedFrom, parsedCreatedTo);
        return OrderPageResponse.from(orderCatalog.browse(
                ListParameters.parseInteger("page", page, 0, 0, Integer.MAX_VALUE),
                ListParameters.parseInteger("size", size, 20, 1, 100),
                parseSort(sort == null ? "createdAt,desc" : sort),
                parsedProductId,
                parsedCustomerId,
                parsedCreatedFrom,
                parsedCreatedTo));
    }

    private static void validateTimeRange(Instant createdFrom, Instant createdTo) {
        if (createdFrom != null && createdTo != null && !createdTo.isAfter(createdFrom)) {
            throw new InvalidListParameterException(
                    "createdTo", "must be later than createdFrom");
        }
    }

    private static OrderSort parseSort(String value) {
        return switch (value) {
            case "id,asc" -> OrderSort.ID_ASC;
            case "id,desc" -> OrderSort.ID_DESC;
            case "createdAt,asc" -> OrderSort.CREATED_AT_ASC;
            case "createdAt,desc" -> OrderSort.CREATED_AT_DESC;
            default -> throw new UnsupportedOrderSortException(value);
        };
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
