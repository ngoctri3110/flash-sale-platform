package com.ngoctri.flashsale.product.api;

import com.ngoctri.flashsale.product.application.CreateProductCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

record CreateProductRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "^\\S(?:.*\\S)?$", message = "must not have leading or trailing whitespace")
        String name,
        @Size(max = 1000)
        String description,
        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,
        @NotNull
        Boolean active,
        @NotNull
        @Min(0)
        @Max(1_000_000)
        Long initialInventory) {

    CreateProductCommand toCommand() {
        return new CreateProductCommand(
                name,
                description,
                price,
                active,
                initialInventory);
    }
}
