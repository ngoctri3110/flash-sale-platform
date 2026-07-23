package com.ngoctri.flashsale.inventory.application;

import org.springframework.stereotype.Service;

@Service
public class InventoryCatalog {

    private final InventoryQuery inventoryQuery;

    public InventoryCatalog(InventoryQuery inventoryQuery) {
        this.inventoryQuery = inventoryQuery;
    }

    public InventoryPage browse(int page, int size, InventorySort sort) {
        return inventoryQuery.findInventory(page, size, sort);
    }
}
