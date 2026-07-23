package com.ngoctri.flashsale.shared.api;

import com.ngoctri.flashsale.product.application.ProductNotFoundException;
import com.ngoctri.flashsale.product.application.UnsupportedProductSortException;
import com.ngoctri.flashsale.product.api.InvalidProductListParameterException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleProductNotFound(
            ProductNotFoundException exception,
            HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("https://flash-sale.local/problems/product-not-found"));
        problem.setTitle("Product not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "PRODUCT_NOT_FOUND");
        problem.setProperty("traceId", traceId(request));
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleRequestBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        var problem = validationProblem(request);
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequestBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        var problem = validationProblem(request);
        problem.setProperty(
                "fieldErrors",
                List.of(new FieldError("request", "must match the documented JSON schema")));
        return problem;
    }

    @ExceptionHandler(UnsupportedProductSortException.class)
    ProblemDetail handleUnsupportedProductSort(
            UnsupportedProductSortException exception,
            HttpServletRequest request) {
        var problem = validationProblem(request);
        problem.setProperty("fieldErrors", List.of(new FieldError("sort", exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(InvalidProductListParameterException.class)
    ProblemDetail handleInvalidProductListParameter(
            InvalidProductListParameterException exception,
            HttpServletRequest request) {
        var problem = validationProblem(request);
        problem.setProperty(
                "fieldErrors",
                List.of(new FieldError(exception.field(), exception.getMessage())));
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        var problem = validationProblem(request);
        problem.setProperty(
                "fieldErrors",
                List.of(new FieldError(
                        exception.getName(),
                        "must be a valid " + expectedTypeName(exception))));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        var traceId = traceId(request);
        LOGGER.error("Unexpected request failure traceId={}", traceId, exception);

        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setType(URI.create("https://flash-sale.local/problems/internal-error"));
        problem.setTitle("Internal server error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INTERNAL_ERROR");
        problem.setProperty("traceId", traceId);
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
        problem.setProperty("traceId", traceId(request));
        return problem;
    }

    private static String expectedTypeName(MethodArgumentTypeMismatchException exception) {
        return exception.getRequiredType() == null
                ? "value"
                : exception.getRequiredType().getSimpleName().toLowerCase();
    }

    private static String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestTraceFilter.REQUEST_ATTRIBUTE);
    }

    private record FieldError(String field, String message) {
    }
}
