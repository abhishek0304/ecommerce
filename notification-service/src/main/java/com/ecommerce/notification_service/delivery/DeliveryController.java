package com.ecommerce.notification_service.delivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController
public class DeliveryController {
    private final DeliveryQueue queue; private final DeliveryRepository deliveries;
    public DeliveryController(DeliveryQueue queue, DeliveryRepository deliveries) {this.queue=queue;this.deliveries=deliveries;}
    @PutMapping("/internal/messages/{id}") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> send(@PathVariable UUID id, @Valid @RequestBody DeliveryQueue.Request request) {
        return Map.of("id",id.toString(),"status",queue.enqueue(id.toString(),request));
    }
    public record View(String id, Delivery.Channel channel, String type, String orderId, String status, int attempts, String failure, Instant createdAt) {
        static View of(Delivery d) {return new View(d.id,d.channel,d.type,d.orderId,d.status,d.attempts,d.failure,d.createdAt);}
    }
    @GetMapping("/api/notifications/deliveries")
    public Page<View> list(Authentication auth, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return deliveries.findByUserIdOrderByCreatedAtDesc((Long)auth.getPrincipal(),PageRequest.of(page,size)).map(View::of);
    }
    @GetMapping("/api/notifications/deliveries/{id}")
    public View get(Authentication auth,@PathVariable UUID id) {
        return deliveries.findById(id.toString()).filter(d -> d.userId.equals(auth.getPrincipal())).map(View::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
