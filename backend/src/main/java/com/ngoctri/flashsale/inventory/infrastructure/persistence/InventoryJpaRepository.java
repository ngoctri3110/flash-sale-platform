package com.ngoctri.flashsale.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface InventoryJpaRepository extends JpaRepository<InventoryEntity, Long> {
}
