package com.ecommerce.order_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", url = "${user-service.url:}")
public interface UserApi {
    @GetMapping("/api/addresses")
    JsonNode addresses(@RequestHeader("Authorization") String bearer);
}
