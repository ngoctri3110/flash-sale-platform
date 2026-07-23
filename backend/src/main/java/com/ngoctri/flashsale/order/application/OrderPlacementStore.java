package com.ngoctri.flashsale.order.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface OrderPlacementStore {

    Optional<OrderProductSnapshot> findProduct(long productId);

    boolean decrementAvailableQuantity(long productId, int quantity);

    OrderView insert(
            PlaceOrderCommand command,
            OrderProductSnapshot product,
            BigDecimal totalAmount);
}
