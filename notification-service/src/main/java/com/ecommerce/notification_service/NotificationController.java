package com.ecommerce.notification_service;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notifications;
    public NotificationController(NotificationRepository notifications) { this.notifications = notifications; }
    @GetMapping
    public Page<Notification> list(Authentication auth,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.findByUserIdOrderByReceivedAtDesc((Long) auth.getPrincipal(), PageRequest.of(page, size));
    }
}
