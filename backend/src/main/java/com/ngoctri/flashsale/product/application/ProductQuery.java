package com.ngoctri.flashsale.product.application;

import java.util.Optional;

public interface ProductQuery {

    ProductPage findActiveProducts(ProductPageQuery query);

    Optional<ProductView> findById(long productId);
}
