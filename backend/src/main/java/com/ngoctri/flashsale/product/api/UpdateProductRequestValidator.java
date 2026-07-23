package com.ngoctri.flashsale.product.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class UpdateProductRequestValidator
        implements ConstraintValidator<ValidProductUpdate, UpdateProductRequest> {

    @Override
    public boolean isValid(UpdateProductRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        var valid = true;
        context.disableDefaultConstraintViolation();
        if (!request.hasAnyField()) {
            addViolation(context, "request", "must contain at least one field");
            valid = false;
        }
        if (request.namePresent() && request.name() == null) {
            addViolation(context, "name", "must not be null");
            valid = false;
        }
        if (request.pricePresent() && request.price() == null) {
            addViolation(context, "price", "must not be null");
            valid = false;
        }
        if (request.activePresent() && request.active() == null) {
            addViolation(context, "active", "must not be null");
            valid = false;
        }
        return valid;
    }

    private static void addViolation(
            ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
