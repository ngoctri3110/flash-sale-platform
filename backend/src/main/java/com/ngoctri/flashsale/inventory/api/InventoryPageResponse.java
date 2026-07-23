package com.ngoctri.flashsale.inventory.api;

import com.ngoctri.flashsale.inventory.application.InventoryPage;
import com.ngoctri.flashsale.product.api.PageMetadataResponse;
import java.util.List;

record InventoryPageResponse(
        List<InventoryResponse> content,
        PageMetadataResponse page) {

    static InventoryPageResponse from(InventoryPage inventory) {
        return new InventoryPageResponse(
                inventory.content().stream().map(InventoryResponse::from).toList(),
                new PageMetadataResponse(
                        inventory.number(),
                        inventory.size(),
                        inventory.totalElements(),
                        inventory.totalPages()));
    }
}
