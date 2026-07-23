package com.ngoctri.flashsale.inventory.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NonZeroValidator.class)
@interface NonZero {

    String message() default "must not be zero";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
