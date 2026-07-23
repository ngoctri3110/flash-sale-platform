package com.ngoctri.flashsale.order.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderCatalog {

    private final OrderQuery orderQuery;

    public OrderCatalog(OrderQuery orderQuery) {
        this.orderQuery = orderQuery;
    }

    public OrderPage browse(
            int page,
            int size,
            OrderSort sort,
            Long productId,
            UUID customerId,
            Instant createdFrom,
            Instant createdTo) {
        return orderQuery.findOrders(new OrderPageQuery(
                page,
                size,
                sort,
                productId,
                customerId,
                createdFrom,
                createdTo));
    }
}
