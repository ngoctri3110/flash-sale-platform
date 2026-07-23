package com.ngoctri.flashsale.inventory.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class NonZeroValidator implements ConstraintValidator<NonZero, Long> {

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        return value == null || value != 0;
    }
}
