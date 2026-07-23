package com.ngoctri.flashsale.product.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Test-only mapping without a fetch optimization, used solely to reproduce N+1. */
@Entity
@Table(name = "orders")
class OrderLazyLabEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    ProductEntity getProduct() {
        return product;
    }
}
