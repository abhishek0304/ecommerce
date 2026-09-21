package com.ecommerce.cart_service.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail dependencyOrBusinessError(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason() == null ? "Request failed" : ex.getReason());
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail concurrentUpdate(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Cart changed concurrently. Reload the cart and retry.");
    }
}
