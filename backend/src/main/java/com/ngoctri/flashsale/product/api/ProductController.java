package com.ngoctri.flashsale.product.api;

import com.ngoctri.flashsale.product.application.ProductCatalog;
import com.ngoctri.flashsale.product.application.ProductCreator;
import com.ngoctri.flashsale.product.application.ProductSort;
import com.ngoctri.flashsale.product.application.ProductUpdater;
import com.ngoctri.flashsale.product.application.UnsupportedProductSortException;
import com.ngoctri.flashsale.shared.api.ListParameters;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    private final ProductCatalog productCatalog;
    private final ProductCreator productCreator;
    private final ProductUpdater productUpdater;

    ProductController(
            ProductCatalog productCatalog,
            ProductCreator productCreator,
            ProductUpdater productUpdater) {
        this.productCatalog = productCatalog;
        this.productCreator = productCreator;
        this.productUpdater = productUpdater;
    }

    @GetMapping
    ProductPageResponse listProducts(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort) {
        var requestedSort = sort == null ? "id,asc" : sort;
        return ProductPageResponse.from(
                productCatalog.browse(
                        ListParameters.parseInteger("page", page, 0, 0, Integer.MAX_VALUE),
                        ListParameters.parseInteger("size", size, 20, 1, 100),
                        parseSort(requestedSort)));
    }

    @GetMapping("/{productId}")
    ProductResponse getProduct(@PathVariable long productId) {
        return ProductResponse.from(productCatalog.findById(productId));
    }

    @PostMapping
    ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        var created = ProductResponse.from(productCreator.create(request.toCommand()));
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{productId}")
    ProductResponse updateProduct(
            @PathVariable long productId, @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(productUpdater.update(productId, request.toCommand()));
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
