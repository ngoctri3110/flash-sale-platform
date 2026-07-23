package com.ngoctri.flashsale.order.application;

import java.time.Instant;
import java.util.UUID;

public record OrderPageQuery(
        int page,
        int size,
        OrderSort sort,
        Long productId,
        UUID customerId,
        Instant createdFrom,
        Instant createdTo) {
}
