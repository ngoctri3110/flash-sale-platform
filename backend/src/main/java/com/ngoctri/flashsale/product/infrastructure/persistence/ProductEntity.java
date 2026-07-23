package com.ngoctri.flashsale.product.infrastructure.persistence;

import com.ngoctri.flashsale.product.application.UpdateProductCommand;
import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {}

    static ProductEntity create(
            String name,
            String description,
            BigDecimal price,
            boolean active,
            Instant now) {
        var product = new ProductEntity();
        product.name = name;
        product.description = description;
        product.price = price;
        product.currency = "VND";
        product.active = active;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    void update(UpdateProductCommand command, Instant now) {
        if (command.name().present()) {
            name = command.name().value();
        }
        if (command.description().present()) {
            description = command.description().value();
        }
        if (command.price().present()) {
            price = command.price().value();
        }
        if (command.active().present()) {
            active = command.active().value();
        }
        updatedAt = now;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    BigDecimal getPrice() {
        return price;
    }

    String getCurrency() {
        return currency;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
