package com.ngoctri.flashsale.product.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UpdateProductRequestValidator.class)
@interface ValidProductUpdate {

    String message() default "must contain at least one field";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
