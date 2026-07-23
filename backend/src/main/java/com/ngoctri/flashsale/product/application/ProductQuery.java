package com.ngoctri.flashsale.product.application;

import java.util.Optional;

public interface ProductQuery {

    ProductPage findProducts(ProductPageQuery query);

    Optional<ProductView> findById(long productId);
}
