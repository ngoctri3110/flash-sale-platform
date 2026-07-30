CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id UUID NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_orders_customer_idempotency_key
        UNIQUE (customer_id, idempotency_key)
);
