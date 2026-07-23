package com.ngoctri.flashsale.order.application;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was already used with different Order input");
    }
}
