package com.ngoctri.flashsale.inventory.application;

public interface InitialInventoryStore {

    void create(long productId, long availableQuantity);
}
