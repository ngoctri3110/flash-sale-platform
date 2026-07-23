package com.ngoctri.flashsale.order.infrastructure.persistence;

import com.ngoctri.flashsale.order.application.OrderPage;
import com.ngoctri.flashsale.order.application.OrderPageQuery;
import com.ngoctri.flashsale.order.application.OrderQuery;
import com.ngoctri.flashsale.order.application.OrderSort;
import com.ngoctri.flashsale.order.application.OrderView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderQuery implements OrderQuery {

    private static final Map<OrderSort, String> ORDER_BY = Map.of(
            OrderSort.ID_ASC, "id ASC",
            OrderSort.ID_DESC, "id DESC",
            OrderSort.CREATED_AT_ASC, "created_at ASC, id ASC",
            OrderSort.CREATED_AT_DESC, "created_at DESC, id DESC");

    private final JdbcClient jdbcClient;

    JdbcOrderQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public OrderPage findOrders(OrderPageQuery query) {
        var predicates = new ArrayList<String>();
        var parameters = new HashMap<String, Object>();
        if (query.productId() != null) {
            predicates.add("product_id = :productId");
            parameters.put("productId", query.productId());
        }
        if (query.customerId() != null) {
            predicates.add("customer_id = :customerId");
            parameters.put("customerId", query.customerId());
        }
        if (query.createdFrom() != null) {
            predicates.add("created_at >= :createdFrom");
            parameters.put(
                    "createdFrom",
                    OffsetDateTime.ofInstant(query.createdFrom(), ZoneOffset.UTC));
        }
        if (query.createdTo() != null) {
            predicates.add("created_at < :createdTo");
            parameters.put(
                    "createdTo",
                    OffsetDateTime.ofInstant(query.createdTo(), ZoneOffset.UTC));
        }
        var whereClause = predicates.isEmpty()
                ? ""
                : " WHERE " + String.join(" AND ", predicates);

        var totalElements = jdbcClient
                .sql("SELECT COUNT(*) FROM orders" + whereClause)
                .params(parameters)
                .query(Long.class)
                .single();
        var content = jdbcClient
                .sql(("""
                        SELECT
                            id,
                            customer_id,
                            product_id,
                            product_name,
                            quantity,
                            unit_price,
                            currency,
                            total_amount,
                            created_at
                        FROM orders
                        %s
                        ORDER BY %s
                        LIMIT :size OFFSET :offset
                        """).formatted(whereClause, ORDER_BY.get(query.sort())))
                .params(parameters)
                .param("size", query.size())
                .param("offset", (long) query.page() * query.size())
                .query(OrderView.class)
                .list();
        var totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / query.size());
        return new OrderPage(
                content,
                query.page(),
                query.size(),
                totalElements,
                totalPages);
    }
}
