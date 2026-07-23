ALTER TABLE inventories
    ADD CONSTRAINT ck_inventories_available_quantity_maximum
        CHECK (available_quantity <= 1000000);
