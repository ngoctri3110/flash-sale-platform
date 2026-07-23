package com.ngoctri.flashsale.inventory.api;

import com.ngoctri.flashsale.inventory.application.InventoryView;
import java.time.Instant;

record InventoryResponse(
        long productId,
        String productName,
        long availableQuantity,
        Instant updatedAt) {

    static InventoryResponse from(InventoryView inventory) {
        return new InventoryResponse(
                inventory.productId(),
                inventory.productName(),
                inventory.availableQuantity(),
                inventory.updatedAt());
    }
}
