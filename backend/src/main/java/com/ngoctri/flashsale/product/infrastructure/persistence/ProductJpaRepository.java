package com.ngoctri.flashsale.product.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    @Query("""
            SELECT new com.ngoctri.flashsale.product.infrastructure.persistence.ProductListProjection(
                product.id,
                product.name,
                product.description,
                product.price,
                product.currency,
                product.active,
                product.createdAt,
                product.updatedAt)
            FROM ProductEntity product
            """)
    Page<ProductListProjection> findProductList(Pageable pageable);
}
