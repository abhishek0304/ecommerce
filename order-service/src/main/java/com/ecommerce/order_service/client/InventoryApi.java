package com.ecommerce.order_service.client;

import com.ecommerce.order_service.api.OrderDtos.Reservation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service", url = "${product-service.url:}")
public interface InventoryApi {
    @PutMapping("/internal/inventory/reservations/{id}")
    Reservation reserve(@PathVariable("id") String id, @RequestHeader("X-Service-Key") String key,
                        @RequestBody Map<String, Object> body);
    @DeleteMapping("/internal/inventory/reservations/{id}")
    void release(@PathVariable("id") String id, @RequestHeader("X-Service-Key") String key);
    @PostMapping("/internal/inventory/reservations/{id}/commit")
    void commit(@PathVariable("id") String id, @RequestHeader("X-Service-Key") String key);
    @PostMapping("/internal/inventory/reservations/{id}/return")
    void returnInventory(@PathVariable("id") String id, @RequestHeader("X-Service-Key") String key,
                         @RequestBody Map<String, Boolean> body);
    @GetMapping("/api/v1/products/{id}")
    JsonNode product(@PathVariable("id") Long id);
}
