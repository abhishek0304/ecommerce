package com.ecommerce.order_service.client;

import com.ecommerce.order_service.api.OrderDtos.CartSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "cart-service", url = "${cart-service.url:}")
public interface CartApi {
    @GetMapping("/internal/carts/{id}")
    CartSnapshot get(@PathVariable("id") Long id, @RequestHeader("X-Service-Key") String key);
    @PostMapping("/internal/carts/{id}/consume")
    JsonNode consume(@PathVariable("id") Long id, @RequestHeader("X-Service-Key") String key,
                     @RequestBody Map<String, Object> body);
}
