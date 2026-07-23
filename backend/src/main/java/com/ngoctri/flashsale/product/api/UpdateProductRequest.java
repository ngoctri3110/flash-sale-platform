package com.ngoctri.flashsale.product.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.ngoctri.flashsale.product.application.PatchField;
import com.ngoctri.flashsale.product.application.UpdateProductCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@ValidProductUpdate
final class UpdateProductRequest {

    @Size(min = 1, max = 120)
    @Pattern(regexp = "\\S(?:.*\\S)?", message = "must not have leading or trailing whitespace")
    private String name;

    @Size(max = 1000)
    private String description;

    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 17, fraction = 2)
    private BigDecimal price;

    private Boolean active;
    private boolean namePresent;
    private boolean descriptionPresent;
    private boolean pricePresent;
    private boolean activePresent;

    @JsonSetter("name")
    void setName(String name) {
        namePresent = true;
        this.name = name;
    }

    @JsonSetter("description")
    void setDescription(String description) {
        descriptionPresent = true;
        this.description = description;
    }

    @JsonSetter("price")
    void setPrice(BigDecimal price) {
        pricePresent = true;
        this.price = price;
    }

    @JsonSetter("active")
    void setActive(Boolean active) {
        activePresent = true;
        this.active = active;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    BigDecimal price() {
        return price;
    }

    Boolean active() {
        return active;
    }

    boolean namePresent() {
        return namePresent;
    }

    boolean pricePresent() {
        return pricePresent;
    }

    boolean activePresent() {
        return activePresent;
    }

    boolean hasAnyField() {
        return namePresent || descriptionPresent || pricePresent || activePresent;
    }

    UpdateProductCommand toCommand() {
        return new UpdateProductCommand(
                new PatchField<>(namePresent, name),
                new PatchField<>(descriptionPresent, description),
                new PatchField<>(pricePresent, price),
                new PatchField<>(activePresent, active));
    }
}
