package com.ngoctri.flashsale.product.api;

public class InvalidProductListParameterException extends RuntimeException {

    private final String field;

    public InvalidProductListParameterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
