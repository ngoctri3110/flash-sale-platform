package com.ngoctri.flashsale.product.application;

import org.springframework.stereotype.Service;

@Service
public class ProductCatalog {

    private final ProductQuery productQuery;

    public ProductCatalog(ProductQuery productQuery) {
        this.productQuery = productQuery;
    }

    public ProductPage browse(int page, int size, ProductSort sort) {
        return productQuery.findProducts(new ProductPageQuery(page, size, sort));
    }

    public ProductView findById(long productId) {
        return productQuery.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
