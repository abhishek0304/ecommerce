package com.ecommerce.notification_service.delivery;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", url = "${services.user-url:http://localhost:8082}")
public interface UserContactApi {
    @GetMapping("/internal/users/{id}/contact")
    RecipientClient.Contact get(@PathVariable("id") Long id, @RequestHeader("X-Service-Key") String key);
}
