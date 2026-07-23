package com.ngoctri.flashsale.order.application;

public interface OrderQuery {

    OrderPage findOrders(OrderPageQuery query);
}
