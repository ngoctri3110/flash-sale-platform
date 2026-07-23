package com.ngoctri.flashsale.order.application;

public class UnsupportedOrderSortException extends RuntimeException {

    public UnsupportedOrderSortException(String value) {
        super("Unsupported Order sort: " + value);
    }
}
