package com.ngoctri.flashsale.product.application;

import java.util.List;

public record ProductPage(
        List<ProductView> content,
        int number,
        int size,
        long totalElements,
        int totalPages) {}
