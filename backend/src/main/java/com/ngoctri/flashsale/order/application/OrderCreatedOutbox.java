package com.ngoctri.flashsale.order.application;

public interface OrderCreatedOutbox {

    void append(OrderView order);
}
