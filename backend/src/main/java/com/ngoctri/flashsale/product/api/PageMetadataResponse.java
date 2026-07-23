package com.ngoctri.flashsale.product.api;

public record PageMetadataResponse(
        int number,
        int size,
        long totalElements,
        int totalPages) {}
