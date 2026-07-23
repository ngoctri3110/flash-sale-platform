package com.ngoctri.flashsale.order.application;

import com.ngoctri.flashsale.product.application.ProductNotFoundException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceOrder {

    private final OrderPlacementStore store;

    public PlaceOrder(OrderPlacementStore store) {
        this.store = store;
    }

    @Transactional
    public PlaceOrderResult place(PlaceOrderCommand command) {
        store.lockIdempotencyKey(command);
        var existingOrder = store.findOrderByCustomerAndIdempotencyKey(command);
        if (existingOrder.isPresent()) {
            var order = existingOrder.get();
            if (order.productId() != command.productId()
                    || order.quantity() != command.quantity()) {
                throw new IdempotencyKeyReusedException();
            }
            return PlaceOrderResult.replayed(order);
        }

        var product = store.findProduct(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));
        if (!product.active()) {
            throw new ProductNotAvailableException(command.productId());
        }
        if (!store.decrementAvailableQuantity(command.productId(), command.quantity())) {
            throw new InsufficientInventoryException(command.productId(), command.quantity());
        }

        var totalAmount = product.unitPrice().multiply(BigDecimal.valueOf(command.quantity()));
        return PlaceOrderResult.created(store.insert(command, product, totalAmount));
    }
}
