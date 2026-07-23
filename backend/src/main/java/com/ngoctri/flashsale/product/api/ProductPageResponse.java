package com.ngoctri.flashsale.product.api;

import java.util.List;

import com.ngoctri.flashsale.product.application.ProductPage;

public record ProductPageResponse(
        List<ProductResponse> content,
        PageMetadataResponse page) {

    static ProductPageResponse from(ProductPage products) {
        return new ProductPageResponse(
                products.content().stream().map(ProductResponse::from).toList(),
                new PageMetadataResponse(
                        products.number(),
                        products.size(),
                        products.totalElements(),
                        products.totalPages()));
    }
}
