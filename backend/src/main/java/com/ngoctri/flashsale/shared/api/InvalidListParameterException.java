package com.ngoctri.flashsale.shared.api;

public class InvalidListParameterException extends RuntimeException {

    private final String field;

    public InvalidListParameterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
