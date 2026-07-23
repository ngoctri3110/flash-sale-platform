package com.ngoctri.flashsale.product.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.ngoctri.flashsale.product.application.ProductView;

public record ProductResponse(
        long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static ProductResponse from(ProductView product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.description(),
                product.price(),
                product.currency(),
                product.active(),
                product.createdAt(),
                product.updatedAt());
    }
}
