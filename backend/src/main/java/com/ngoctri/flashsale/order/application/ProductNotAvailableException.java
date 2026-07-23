package com.ngoctri.flashsale.order.application;

public class ProductNotAvailableException extends RuntimeException {

    public ProductNotAvailableException(long productId) {
        super("Product " + productId + " is not available for ordering");
    }
}
