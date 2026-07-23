package com.ngoctri.flashsale.product.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductUpdater {

    private final ProductStore productStore;

    public ProductUpdater(ProductStore productStore) {
        this.productStore = productStore;
    }

    @Transactional
    public ProductView update(long productId, UpdateProductCommand command) {
        return productStore
                .update(productId, command)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
