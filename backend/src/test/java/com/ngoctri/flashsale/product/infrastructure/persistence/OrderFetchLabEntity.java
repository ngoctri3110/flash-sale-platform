package com.ngoctri.flashsale.product.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Test-only mapping used to demonstrate fetching a real order-to-product association. */
@Entity
@Table(name = "orders")
class OrderFetchLabEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductBatchLabEntity product;

    Long getId() {
        return id;
    }

    ProductBatchLabEntity getProduct() {
        return product;
    }
}
