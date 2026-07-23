package com.ngoctri.flashsale.inventory.infrastructure.persistence;

import com.ngoctri.flashsale.inventory.application.InitialInventoryStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class JpaInitialInventoryStore implements InitialInventoryStore {

    private final InventoryJpaRepository repository;
    private final Clock clock;

    JpaInitialInventoryStore(InventoryJpaRepository repository) {
        this.repository = repository;
        this.clock = Clock.systemUTC();
    }

    @Override
    public void create(long productId, long availableQuantity) {
        repository.save(InventoryEntity.initial(productId, availableQuantity, Instant.now(clock)));
    }
}
