SELECT product_id, available_quantity FROM inventories;
SELECT COUNT(*) AS accepted_orders, COALESCE(SUM(quantity), 0) AS accepted_quantity FROM orders;
