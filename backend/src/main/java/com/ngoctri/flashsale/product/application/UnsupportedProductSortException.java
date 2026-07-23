package com.ngoctri.flashsale.product.application;

public class UnsupportedProductSortException extends RuntimeException {

    public UnsupportedProductSortException(String value) {
        super("Unsupported Product sort: " + value);
    }
}
