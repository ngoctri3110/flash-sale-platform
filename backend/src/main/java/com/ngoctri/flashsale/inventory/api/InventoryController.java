package com.ngoctri.flashsale.inventory.api;

import com.ngoctri.flashsale.inventory.application.InventoryCatalog;
import com.ngoctri.flashsale.inventory.application.InventoryAdjuster;
import com.ngoctri.flashsale.inventory.application.InventorySort;
import com.ngoctri.flashsale.inventory.application.UnsupportedInventorySortException;
import com.ngoctri.flashsale.shared.api.ListParameters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventories")
class InventoryController {

    private final InventoryCatalog inventoryCatalog;
    private final InventoryAdjuster inventoryAdjuster;

    InventoryController(InventoryCatalog inventoryCatalog, InventoryAdjuster inventoryAdjuster) {
        this.inventoryCatalog = inventoryCatalog;
        this.inventoryAdjuster = inventoryAdjuster;
    }

    @GetMapping
    InventoryPageResponse listInventory(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {
        return InventoryPageResponse.from(inventoryCatalog.browse(
                ListParameters.parseInteger("page", page, 0, 0, Integer.MAX_VALUE),
                ListParameters.parseInteger("size", size, 20, 1, 100),
                parseSort(sort == null ? "productId,asc" : sort)));
    }

    @PostMapping("/{productId}/adjustments")
    InventoryResponse adjustInventory(
            @PathVariable @Min(1) long productId,
            @Valid @RequestBody AdjustInventoryRequest request) {
        return InventoryResponse.from(inventoryAdjuster.adjust(request.toCommand(productId)));
    }

    private static InventorySort parseSort(String value) {
        return switch (value) {
            case "productId,asc" -> InventorySort.PRODUCT_ID_ASC;
            case "productId,desc" -> InventorySort.PRODUCT_ID_DESC;
            case "productName,asc" -> InventorySort.PRODUCT_NAME_ASC;
            case "productName,desc" -> InventorySort.PRODUCT_NAME_DESC;
            case "availableQuantity,asc" -> InventorySort.AVAILABLE_QUANTITY_ASC;
            case "availableQuantity,desc" -> InventorySort.AVAILABLE_QUANTITY_DESC;
            default -> throw new UnsupportedInventorySortException(value);
        };
    }

}
