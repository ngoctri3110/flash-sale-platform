CREATE TABLE inventories (
    product_id BIGINT PRIMARY KEY,
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0)
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES inventories(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO inventories (product_id, available_quantity) VALUES (1, 1);
