package com.ngoctri.flashsale.inventory.application;

public interface InventoryQuery {

    InventoryPage findInventory(int page, int size, InventorySort sort);
}
