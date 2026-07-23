package com.ngoctri.flashsale.inventory.api;

import com.ngoctri.flashsale.inventory.application.AdjustInventoryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record AdjustInventoryRequest(
        @NotNull @NonZero Long quantityDelta,
        @NotBlank @Size(min = 3, max = 200) String reason) {

    AdjustInventoryCommand toCommand(long productId) {
        return new AdjustInventoryCommand(productId, quantityDelta, reason);
    }
}
