package com.ngoctri.flashsale.product.api;

import com.ngoctri.flashsale.product.application.ProductCatalog;
import com.ngoctri.flashsale.product.application.ProductSort;
import com.ngoctri.flashsale.product.application.UnsupportedProductSortException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    private final ProductCatalog productCatalog;

    ProductController(ProductCatalog productCatalog) {
        this.productCatalog = productCatalog;
    }

    @GetMapping
    ProductPageResponse listProducts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort) {
        var requestedSort = sort == null ? "id,asc" : sort;
        return ProductPageResponse.from(
                productCatalog.browse(page, size, parseSort(requestedSort)));
    }

    @GetMapping("/{productId}")
    ProductResponse getProduct(@PathVariable long productId) {
        return ProductResponse.from(productCatalog.findById(productId));
    }

    private static ProductSort parseSort(String value) {
        return switch (value) {
            case "id,asc" -> ProductSort.ID_ASC;
            case "id,desc" -> ProductSort.ID_DESC;
            case "name,asc" -> ProductSort.NAME_ASC;
            case "name,desc" -> ProductSort.NAME_DESC;
            case "price,asc" -> ProductSort.PRICE_ASC;
            case "price,desc" -> ProductSort.PRICE_DESC;
            case "createdAt,asc" -> ProductSort.CREATED_AT_ASC;
            case "createdAt,desc" -> ProductSort.CREATED_AT_DESC;
            default -> throw new UnsupportedProductSortException(value);
        };
    }
}
