TRUNCATE orders RESTART IDENTITY;
UPDATE inventories SET available_quantity = 1 WHERE product_id = 1;
