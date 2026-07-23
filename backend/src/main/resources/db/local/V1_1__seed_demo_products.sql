WITH product AS (
    INSERT INTO products (name, description, price)
    VALUES (
        'Mechanical Keyboard',
        'A compact mechanical keyboard with tactile switches.',
        2490000.00
    )
    RETURNING id
)
INSERT INTO inventories (product_id, available_quantity)
SELECT id, 18 FROM product;

WITH product AS (
    INSERT INTO products (name, description, price)
    VALUES (
        'Noise-Cancelling Headphones',
        'Wireless over-ear headphones for focused work.',
        3890000.00
    )
    RETURNING id
)
INSERT INTO inventories (product_id, available_quantity)
SELECT id, 12 FROM product;

WITH product AS (
    INSERT INTO products (name, description, price)
    VALUES (
        'Portable SSD 1TB',
        'A pocket-sized USB-C solid-state drive.',
        2190000.00
    )
    RETURNING id
)
INSERT INTO inventories (product_id, available_quantity)
SELECT id, 25 FROM product;

WITH product AS (
    INSERT INTO products (name, description, price, active)
    VALUES (
        'Smart Desk Lamp',
        'An adjustable desk lamp currently unavailable for ordering.',
        1290000.00,
        FALSE
    )
    RETURNING id
)
INSERT INTO inventories (product_id, available_quantity)
SELECT id, 0 FROM product;

WITH product AS (
    INSERT INTO products (name, description, price)
    VALUES (
        'Ergonomic Mouse',
        'A wireless mouse shaped for comfortable long sessions.',
        2490000.00
    )
    RETURNING id
)
INSERT INTO inventories (product_id, available_quantity)
SELECT id, 20 FROM product;
