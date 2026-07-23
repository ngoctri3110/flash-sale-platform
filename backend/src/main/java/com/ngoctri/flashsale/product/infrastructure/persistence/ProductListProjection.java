package com.ngoctri.flashsale.product.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;

record ProductListProjection(
        long id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
