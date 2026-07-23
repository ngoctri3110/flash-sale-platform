package com.ngoctri.flashsale.order.infrastructure.persistence;

import com.ngoctri.flashsale.order.application.OrderPlacementStore;
import com.ngoctri.flashsale.order.application.OrderProductSnapshot;
import com.ngoctri.flashsale.order.application.OrderView;
import com.ngoctri.flashsale.order.application.PlaceOrderCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderPlacementStore implements OrderPlacementStore {

    private final JdbcClient jdbcClient;

    JdbcOrderPlacementStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<OrderProductSnapshot> findProduct(long productId) {
        return jdbcClient
                .sql("""
                        SELECT id, name, price, currency, active
                        FROM products
                        WHERE id = :productId
                        FOR SHARE
                        """)
                .param("productId", productId)
                .query((resultSet, rowNumber) -> new OrderProductSnapshot(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getBigDecimal("price"),
                        resultSet.getString("currency"),
                        resultSet.getBoolean("active")))
                .optional();
    }

    @Override
    public boolean decrementAvailableQuantity(long productId, int quantity) {
        return jdbcClient
                        .sql("""
                                UPDATE inventories
                                SET available_quantity = available_quantity - :quantity,
                                    updated_at = CURRENT_TIMESTAMP
                                WHERE product_id = :productId
                                  AND available_quantity >= :quantity
                                """)
                        .param("productId", productId)
                        .param("quantity", quantity)
                        .update()
                == 1;
    }

    @Override
    public OrderView insert(
            PlaceOrderCommand command,
            OrderProductSnapshot product,
            BigDecimal totalAmount) {
        return jdbcClient
                .sql("""
                        INSERT INTO orders (
                            customer_id,
                            product_id,
                            product_name,
                            quantity,
                            unit_price,
                            currency,
                            total_amount,
                            idempotency_key
                        )
                        VALUES (
                            :customerId,
                            :productId,
                            :productName,
                            :quantity,
                            :unitPrice,
                            :currency,
                            :totalAmount,
                            :idempotencyKey
                        )
                        RETURNING id, created_at
                        """)
                .param("customerId", command.customerId())
                .param("productId", product.id())
                .param("productName", product.name())
                .param("quantity", command.quantity())
                .param("unitPrice", product.unitPrice())
                .param("currency", product.currency())
                .param("totalAmount", totalAmount)
                .param("idempotencyKey", command.idempotencyKey())
                .query((resultSet, rowNumber) -> new OrderView(
                        resultSet.getLong("id"),
                        command.customerId(),
                        product.id(),
                        product.name(),
                        command.quantity(),
                        product.unitPrice(),
                        product.currency(),
                        totalAmount,
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant()))
                .single();
    }
}
