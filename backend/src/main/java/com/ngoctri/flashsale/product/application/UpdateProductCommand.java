package com.ngoctri.flashsale.product.application;

import java.math.BigDecimal;

public record UpdateProductCommand(
        PatchField<String> name,
        PatchField<String> description,
        PatchField<BigDecimal> price,
        PatchField<Boolean> active) {}
