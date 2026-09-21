package com.ecommerce.order_service.commerce;
public class CouponRejectedException extends org.springframework.web.server.ResponseStatusException {
    public CouponRejectedException(String detail) { super(org.springframework.http.HttpStatus.CONFLICT, detail); }
}
