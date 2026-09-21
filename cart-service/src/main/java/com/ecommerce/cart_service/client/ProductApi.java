package com.ecommerce.cart_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service", url = "${product-service.url:}")
public interface ProductApi {
    @GetMapping("/api/v1/products/{id}")
    ProductClient.Product get(@PathVariable("id") Long id);
}
