package com.ecommerce.order_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "razorpay", url = "${razorpay.base-url:https://api.razorpay.com/v1}",
             configuration = ProviderFeignConfiguration.class)
public interface RazorpayApi {
    @PostMapping("/orders")
    JsonNode create(@RequestHeader("Authorization") String auth, @RequestBody Map<String, Object> body);
    @GetMapping("/orders")
    JsonNode find(@RequestHeader("Authorization") String auth, @RequestParam("receipt") String receipt, @RequestParam("count") int count);
    @GetMapping("/orders/{id}")
    JsonNode order(@RequestHeader("Authorization") String auth, @PathVariable("id") String id);
    @GetMapping("/orders/{id}/payments")
    JsonNode payments(@RequestHeader("Authorization") String auth, @PathVariable("id") String id);
    @PostMapping("/payments/{id}/refund")
    JsonNode createRefund(@RequestHeader("Authorization") String auth, @PathVariable("id") String id,
                          @RequestHeader("X-Refund-Idempotency") String attemptId, @RequestBody Map<String, Object> body);
    @GetMapping("/refunds/{id}")
    JsonNode refund(@RequestHeader("Authorization") String auth, @PathVariable("id") String id);
    @GetMapping("/payments/{id}")
    JsonNode payment(@RequestHeader("Authorization") String auth, @PathVariable("id") String id);
}
