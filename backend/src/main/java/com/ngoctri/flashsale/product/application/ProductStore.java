package com.ngoctri.flashsale.product.application;

import java.util.Optional;

public interface ProductStore {

    ProductView create(CreateProductCommand command);

    Optional<ProductView> update(long productId, UpdateProductCommand command);
}
