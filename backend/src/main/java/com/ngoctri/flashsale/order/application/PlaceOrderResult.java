package com.ngoctri.flashsale.order.application;

public record PlaceOrderResult(OrderView order, boolean created) {

    public static PlaceOrderResult created(OrderView order) {
        return new PlaceOrderResult(order, true);
    }

    public static PlaceOrderResult replayed(OrderView order) {
        return new PlaceOrderResult(order, false);
    }
}
