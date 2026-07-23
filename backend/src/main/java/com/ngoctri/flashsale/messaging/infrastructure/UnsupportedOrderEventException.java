package com.ngoctri.flashsale.messaging.infrastructure;

class UnsupportedOrderEventException extends RuntimeException {

    UnsupportedOrderEventException(String message) {
        super(message);
    }
}
