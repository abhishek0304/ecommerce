package com.ecommerce.product_service.exception;

public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) { super(message); }
}
