package com.ngoctri.flashsale.product.infrastructure.persistence;

import com.ngoctri.flashsale.product.application.ProductPage;
import com.ngoctri.flashsale.product.application.ProductPageQuery;
import com.ngoctri.flashsale.product.application.ProductQuery;
import com.ngoctri.flashsale.product.application.ProductSort;
import com.ngoctri.flashsale.product.application.ProductView;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class JpaProductQuery implements ProductQuery {

    private final ProductJpaRepository repository;

    JpaProductQuery(ProductJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ProductPage findActiveProducts(ProductPageQuery query) {
        var primaryOrder = query.sort().direction() == ProductSort.Direction.ASC
                ? Sort.Order.asc(query.sort().property())
                : Sort.Order.desc(query.sort().property());
        var sort = Sort.by(primaryOrder);
        if (!query.sort().property().equals("id")) {
            sort = sort.and(Sort.by("id").ascending());
        }
        var pageable = PageRequest.of(query.page(), query.size(), sort);
        var result = repository.findAllByActiveTrue(pageable);
        var content = result.getContent().stream().map(JpaProductQuery::toView).toList();

        return new ProductPage(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<ProductView> findById(long productId) {
        return repository.findById(productId).map(JpaProductQuery::toView);
    }

    private static ProductView toView(ProductEntity product) {
        return new ProductView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
