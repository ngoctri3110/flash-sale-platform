package com.ngoctri.flashsale.product.api;

import com.ngoctri.flashsale.product.application.ProductCatalog;
import com.ngoctri.flashsale.product.application.ProductSort;
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
            @RequestParam(defaultValue = "id,asc") String sort) {
        return ProductPageResponse.from(
                productCatalog.browse(page, size, ProductSort.fromApiValue(sort)));
    }

    @GetMapping("/{productId}")
    ProductResponse getProduct(@PathVariable long productId) {
        return ProductResponse.from(productCatalog.findById(productId));
    }
}
