package com.ngoctri.flashsale.inventory.application;

public class InventoryWouldBeNegativeException extends RuntimeException {

    private final long productId;

    public InventoryWouldBeNegativeException(long productId) {
        super("Inventory adjustment would make Available Quantity negative for Product " + productId);
        this.productId = productId;
    }

    public long productId() {
        return productId;
    }
}
