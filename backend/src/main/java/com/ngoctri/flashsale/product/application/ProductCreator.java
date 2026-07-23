package com.ngoctri.flashsale.product.application;

import com.ngoctri.flashsale.inventory.application.InitialInventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductCreator {

    private final ProductStore productStore;
    private final InitialInventoryStore initialInventoryStore;

    public ProductCreator(
            ProductStore productStore,
            InitialInventoryStore initialInventoryStore) {
        this.productStore = productStore;
        this.initialInventoryStore = initialInventoryStore;
    }

    @Transactional
    public ProductView create(CreateProductCommand command) {
        var product = productStore.create(command);
        initialInventoryStore.create(product.id(), command.initialInventory());
        return product;
    }
}
