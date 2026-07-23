package com.ngoctri.flashsale.inventory.application;

import java.util.List;

public record InventoryPage(
        List<InventoryView> content,
        int number,
        int size,
        long totalElements,
        int totalPages) {
}
