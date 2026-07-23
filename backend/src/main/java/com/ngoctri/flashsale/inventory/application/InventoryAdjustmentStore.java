package com.ngoctri.flashsale.inventory.application;

public interface InventoryAdjustmentStore {

    InventoryAdjustmentResult adjust(AdjustInventoryCommand command);
}
