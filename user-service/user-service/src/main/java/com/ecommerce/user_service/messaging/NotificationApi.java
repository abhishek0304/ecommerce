package com.ecommerce.user_service.messaging;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "notification-service", url = "${services.notification-url:http://localhost:8086}")
public interface NotificationApi {
    @PutMapping("/internal/messages/{id}")
    ResponseEntity<Void> enqueue(@PathVariable("id") String id, @RequestHeader("X-Service-Key") String key,
                                 @RequestBody Map<String, Object> body);
}
