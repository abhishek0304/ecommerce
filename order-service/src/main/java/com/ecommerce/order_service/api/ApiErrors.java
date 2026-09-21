package com.ecommerce.order_service.api;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import feign.FeignException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail business(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason() == null ? "Request failed" : ex.getReason());
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class, CannotAcquireLockException.class})
    ProblemDetail conflict(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Checkout changed concurrently. Retry with the same Idempotency-Key.");
    }
    @ExceptionHandler(FeignException.class)
    ProblemDetail unavailable(FeignException ex) {
        if (ex.status() == 409)
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Cart is empty or checkout conflicts with current state");
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "A required service is unavailable. Retry with the same Idempotency-Key.");
    }
}
