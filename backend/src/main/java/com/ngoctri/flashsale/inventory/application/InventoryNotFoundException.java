package com.ngoctri.flashsale.inventory.application;

public class InventoryNotFoundException extends RuntimeException {

    private final long productId;

    public InventoryNotFoundException(long productId) {
        super("Inventory for Product " + productId + " was not found");
        this.productId = productId;
    }

    public long productId() {
        return productId;
    }
}
