package com.ngoctri.flashsale.inventory.infrastructure.persistence;

import com.ngoctri.flashsale.inventory.application.AdjustInventoryCommand;
import com.ngoctri.flashsale.inventory.application.InventoryAdjustmentResult;
import com.ngoctri.flashsale.inventory.application.InventoryAdjustmentStore;
import com.ngoctri.flashsale.inventory.application.InventoryView;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInventoryAdjustmentStore implements InventoryAdjustmentStore {

    private final JdbcClient jdbcClient;

    JdbcInventoryAdjustmentStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public InventoryAdjustmentResult adjust(AdjustInventoryCommand command) {
        var updated = jdbcClient
                .sql("""
                        UPDATE inventories
                        SET available_quantity = available_quantity + :quantityDelta,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE product_id = :productId
                          AND available_quantity + :quantityDelta >= 0
                        RETURNING available_quantity, updated_at
                        """)
                .param("productId", command.productId())
                .param("quantityDelta", command.quantityDelta())
                .query((resultSet, rowNumber) -> new UpdatedInventory(
                        resultSet.getLong("available_quantity"),
                        resultSet.getObject("updated_at", OffsetDateTime.class)))
                .optional();

        if (updated.isEmpty()) {
            return inventoryExists(command.productId())
                    ? InventoryAdjustmentResult.wouldBeNegative()
                    : InventoryAdjustmentResult.inventoryNotFound();
        }

        var value = updated.orElseThrow();
        jdbcClient
                .sql("""
                        INSERT INTO inventory_adjustments (
                            product_id,
                            quantity_delta,
                            reason,
                            resulting_available_quantity,
                            created_at
                        )
                        VALUES (
                            :productId,
                            :quantityDelta,
                            :reason,
                            :resultingQuantity,
                            :createdAt
                        )
                        """)
                .param("productId", command.productId())
                .param("quantityDelta", command.quantityDelta())
                .param("reason", command.reason())
                .param("resultingQuantity", value.availableQuantity())
                .param("createdAt", value.updatedAt())
                .update();

        var productName = jdbcClient
                .sql("SELECT name FROM products WHERE id = :productId")
                .param("productId", command.productId())
                .query(String.class)
                .single();
        return InventoryAdjustmentResult.accepted(new InventoryView(
                command.productId(),
                productName,
                value.availableQuantity(),
                value.updatedAt().toInstant()));
    }

    private boolean inventoryExists(long productId) {
        return jdbcClient
                .sql("SELECT EXISTS (SELECT 1 FROM inventories WHERE product_id = :productId)")
                .param("productId", productId)
                .query(Boolean.class)
                .single();
    }

    private record UpdatedInventory(long availableQuantity, OffsetDateTime updatedAt) {}
}
