package com.ngoctri.flashsale.inventory.application;

public record InventoryAdjustmentResult(Status status, InventoryView inventory) {

    public enum Status {
        ACCEPTED,
        INVENTORY_NOT_FOUND,
        WOULD_BE_NEGATIVE
    }

    public static InventoryAdjustmentResult accepted(InventoryView inventory) {
        return new InventoryAdjustmentResult(Status.ACCEPTED, inventory);
    }

    public static InventoryAdjustmentResult inventoryNotFound() {
        return new InventoryAdjustmentResult(Status.INVENTORY_NOT_FOUND, null);
    }

    public static InventoryAdjustmentResult wouldBeNegative() {
        return new InventoryAdjustmentResult(Status.WOULD_BE_NEGATIVE, null);
    }
}
