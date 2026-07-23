package com.ngoctri.flashsale.shared.api;

import com.ngoctri.flashsale.product.application.ProductNotFoundException;
import com.ngoctri.flashsale.product.application.UnsupportedProductSortException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleProductNotFound(
            ProductNotFoundException exception,
            HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("https://flash-sale.local/problems/product-not-found"));
        problem.setTitle("Product not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "PRODUCT_NOT_FOUND");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        var fieldErrors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();

        var problem = validationProblem(request);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(UnsupportedProductSortException.class)
    ProblemDetail handleUnsupportedProductSort(
            UnsupportedProductSortException exception,
            HttpServletRequest request) {
        var problem = validationProblem(request);
        problem.setProperty("fieldErrors", java.util.List.of(new FieldError("sort", exception.getMessage())));
        return problem;
    }

    private ProblemDetail validationProblem(HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request parameters are invalid");
        problem.setType(URI.create("https://flash-sale.local/problems/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }

    private record FieldError(String field, String message) {
    }
}
