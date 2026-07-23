package com.ngoctri.flashsale.inventory.application;

import java.time.Instant;

public record InventoryView(
        long productId,
        String productName,
        long availableQuantity,
        Instant updatedAt) {
}
