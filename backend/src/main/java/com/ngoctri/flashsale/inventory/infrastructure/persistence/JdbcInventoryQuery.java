package com.ngoctri.flashsale.inventory.infrastructure.persistence;

import com.ngoctri.flashsale.inventory.application.InventoryPage;
import com.ngoctri.flashsale.inventory.application.InventoryQuery;
import com.ngoctri.flashsale.inventory.application.InventorySort;
import com.ngoctri.flashsale.inventory.application.InventoryView;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInventoryQuery implements InventoryQuery {

    private static final Map<InventorySort, String> ORDER_BY = Map.of(
            InventorySort.PRODUCT_ID_ASC, "i.product_id ASC",
            InventorySort.PRODUCT_ID_DESC, "i.product_id DESC",
            InventorySort.PRODUCT_NAME_ASC, "p.name ASC, i.product_id ASC",
            InventorySort.PRODUCT_NAME_DESC, "p.name DESC, i.product_id ASC",
            InventorySort.AVAILABLE_QUANTITY_ASC, "i.available_quantity ASC, i.product_id ASC",
            InventorySort.AVAILABLE_QUANTITY_DESC, "i.available_quantity DESC, i.product_id ASC");

    private final JdbcClient jdbcClient;

    JdbcInventoryQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public InventoryPage findInventory(int page, int size, InventorySort sort) {
        var totalElements = jdbcClient
                .sql("SELECT COUNT(*) FROM inventories")
                .query(Long.class)
                .single();
        var content = jdbcClient
                .sql("""
                        SELECT i.product_id, p.name AS product_name,
                               i.available_quantity, i.updated_at
                        FROM inventories i
                        JOIN products p ON p.id = i.product_id
                        ORDER BY %s
                        LIMIT :size OFFSET :offset
                        """.formatted(ORDER_BY.get(sort)))
                .param("size", size)
                .param("offset", (long) page * size)
                .query((resultSet, rowNumber) -> new InventoryView(
                        resultSet.getLong("product_id"),
                        resultSet.getString("product_name"),
                        resultSet.getLong("available_quantity"),
                        resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .list();
        var totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new InventoryPage(content, page, size, totalElements, totalPages);
    }
}
