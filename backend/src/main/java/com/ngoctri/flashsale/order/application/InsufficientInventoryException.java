package com.ngoctri.flashsale.order.application;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(long productId, int quantity) {
        super("Product " + productId + " has insufficient inventory for quantity " + quantity);
    }
}
