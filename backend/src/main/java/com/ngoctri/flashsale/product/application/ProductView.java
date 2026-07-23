package com.ngoctri.flashsale.product.application;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductView(
        long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {}
