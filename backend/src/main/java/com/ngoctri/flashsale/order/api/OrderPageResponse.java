package com.ngoctri.flashsale.order.api;

import com.ngoctri.flashsale.order.application.OrderPage;
import com.ngoctri.flashsale.shared.api.PageMetadataResponse;
import java.util.List;

record OrderPageResponse(
        List<OrderResponse> content,
        PageMetadataResponse page) {

    static OrderPageResponse from(OrderPage orders) {
        return new OrderPageResponse(
                orders.content().stream().map(OrderResponse::from).toList(),
                new PageMetadataResponse(
                        orders.number(),
                        orders.size(),
                        orders.totalElements(),
                        orders.totalPages()));
    }
}
