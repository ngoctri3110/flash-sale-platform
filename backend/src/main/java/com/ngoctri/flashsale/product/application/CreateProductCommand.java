package com.ngoctri.flashsale.product.application;

import java.math.BigDecimal;

public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        boolean active,
        long initialInventory) {
}
