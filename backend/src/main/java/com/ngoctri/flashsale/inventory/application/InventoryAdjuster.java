package com.ngoctri.flashsale.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAdjuster {

    private final InventoryAdjustmentStore store;

    public InventoryAdjuster(InventoryAdjustmentStore store) {
        this.store = store;
    }

    @Transactional
    public InventoryView adjust(AdjustInventoryCommand command) {
        var result = store.adjust(command);
        return switch (result.status()) {
            case ACCEPTED -> result.inventory();
            case INVENTORY_NOT_FOUND -> throw new InventoryNotFoundException(command.productId());
            case WOULD_BE_NEGATIVE ->
                    throw new InventoryWouldBeNegativeException(command.productId());
        };
    }
}
