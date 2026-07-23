package com.ngoctri.flashsale.order.application;

import java.math.BigDecimal;

public record OrderProductSnapshot(
        long id,
        String name,
        BigDecimal unitPrice,
        String currency,
        boolean active) {
}
