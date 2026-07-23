CREATE INDEX ix_orders_created
    ON orders (created_at DESC, id DESC);
