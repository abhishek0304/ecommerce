package com.ecommerce.product_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ProductNotFoundException ex, HttpServletRequest request) { return error(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of()); }
    @ExceptionHandler(DuplicateResourceException.class)
    ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) { return error(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of()); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) { return error(HttpStatus.CONFLICT, "The request conflicts with existing data", request, Map.of()); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) { return error(HttpStatus.CONFLICT, "The product was changed by another request; reload it and retry", request, Map.of()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) fields.put(fieldError.getField(), fieldError.getDefaultMessage());
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request, fields);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), fields));
    }
}
