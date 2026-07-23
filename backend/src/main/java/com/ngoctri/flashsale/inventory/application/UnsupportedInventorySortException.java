package com.ngoctri.flashsale.inventory.application;

public class UnsupportedInventorySortException extends RuntimeException {

    public UnsupportedInventorySortException(String value) {
        super("Unsupported Inventory sort: " + value);
    }
}
