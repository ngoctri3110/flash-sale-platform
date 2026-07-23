package com.ngoctri.flashsale.inventory.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventories")
class InventoryEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "available_quantity", nullable = false)
    private long availableQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryEntity() {
    }

    static InventoryEntity initial(long productId, long availableQuantity, Instant now) {
        var inventory = new InventoryEntity();
        inventory.productId = productId;
        inventory.availableQuantity = availableQuantity;
        inventory.updatedAt = now;
        return inventory;
    }
}
