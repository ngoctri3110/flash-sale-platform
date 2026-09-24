package com.example.order;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {
    private final CatalogClient catalogClient;

    OrderController(CatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }

    @PostMapping("/orders")
    Map<String, Object> create(@RequestBody CreateOrderRequest request) {
        var product = catalogClient.findProduct(request.productId());
        return Map.of("status", "CATALOG_CHECKED", "product", product, "quantity", request.quantity());
    }

    record CreateOrderRequest(long productId, int quantity) {}
}
