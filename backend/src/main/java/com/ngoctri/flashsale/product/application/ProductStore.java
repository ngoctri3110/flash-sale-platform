package com.ngoctri.flashsale.product.application;

public interface ProductStore {

    ProductView create(CreateProductCommand command);
}
