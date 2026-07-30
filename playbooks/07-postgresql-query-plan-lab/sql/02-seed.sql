CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO orders (customer_id, product_id, total_amount, created_at)
SELECT
    gen_random_uuid(),
    1 + floor(random() * 100)::BIGINT,
    100000.00,
    CURRENT_TIMESTAMP - (random() * INTERVAL '90 days')
FROM generate_series(1, 200000);

ANALYZE orders;
