package com.ngoctri.flashsale.product.infrastructure.persistence;

import com.ngoctri.flashsale.product.application.CreateProductCommand;
import com.ngoctri.flashsale.product.application.ProductPage;
import com.ngoctri.flashsale.product.application.ProductPageQuery;
import com.ngoctri.flashsale.product.application.ProductQuery;
import com.ngoctri.flashsale.product.application.ProductSort;
import com.ngoctri.flashsale.product.application.ProductStore;
import com.ngoctri.flashsale.product.application.ProductView;
import com.ngoctri.flashsale.product.application.UpdateProductCommand;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class JpaProductRepositoryAdapter implements ProductQuery, ProductStore {

    private final ProductJpaRepository repository;
    private final Clock clock;

    JpaProductRepositoryAdapter(ProductJpaRepository repository) {
        this.repository = repository;
        this.clock = Clock.systemUTC();
    }

    @Override
    public ProductPage findProducts(ProductPageQuery query) {
        var primaryOrder = toOrder(query.sort());
        var sort = Sort.by(primaryOrder);
        if (!primaryOrder.getProperty().equals("id")) {
            sort = sort.and(Sort.by("id").ascending());
        }
        var pageable = PageRequest.of(query.page(), query.size(), sort);
        var result = repository.findAll(pageable);
        var content = result.getContent().stream().map(JpaProductRepositoryAdapter::toView).toList();

        return new ProductPage(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<ProductView> findById(long productId) {
        return repository.findById(productId).map(JpaProductRepositoryAdapter::toView);
    }

    @Override
    public ProductView create(CreateProductCommand command) {
        var now = Instant.now(clock);
        var product = ProductEntity.create(
                command.name(),
                command.description(),
                command.price(),
                command.active(),
                now);
        return toView(repository.save(product));
    }

    @Override
    public Optional<ProductView> update(long productId, UpdateProductCommand command) {
        return repository.findById(productId).map(product -> {
            product.update(command, Instant.now(clock));
            return toView(product);
        });
    }

    private static Sort.Order toOrder(ProductSort sort) {
        return switch (sort) {
            case ID_ASC -> Sort.Order.asc("id");
            case ID_DESC -> Sort.Order.desc("id");
            case NAME_ASC -> Sort.Order.asc("name");
            case NAME_DESC -> Sort.Order.desc("name");
            case PRICE_ASC -> Sort.Order.asc("price");
            case PRICE_DESC -> Sort.Order.desc("price");
            case CREATED_AT_ASC -> Sort.Order.asc("createdAt");
            case CREATED_AT_DESC -> Sort.Order.desc("createdAt");
        };
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
