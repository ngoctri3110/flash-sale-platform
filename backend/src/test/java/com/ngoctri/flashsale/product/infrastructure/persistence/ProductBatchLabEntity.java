package com.ngoctri.flashsale.product.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

/** Test-only product mapping with a batch-fetch policy. */
@Entity
@Table(name = "products")
@BatchSize(size = 4)
class ProductBatchLabEntity {

    @Id
    private Long id;

    private String name;

    String getName() {
        return name;
    }
}
