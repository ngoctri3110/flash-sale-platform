package com.ngoctri.flashsale.shared.api;

public record PageMetadataResponse(
        int number,
        int size,
        long totalElements,
        int totalPages) {}
