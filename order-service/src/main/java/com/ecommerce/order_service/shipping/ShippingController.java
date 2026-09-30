package com.ecommerce.order_service.shipping;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.ecommerce.order_service.shipping.ShippingDtos.*;

@RestController
public class ShippingController {
    private final ShippingService service;
    public ShippingController(ShippingService service) { this.service=service; }
    @PostMapping("/api/admin/orders/{id}/shipments/{direction}") @PreAuthorize("hasRole('ADMIN')")
    public View book(@PathVariable UUID id,@PathVariable Direction direction,@Valid @RequestBody Booking request) {
        return service.book(id.toString(),direction,request);
    }
    @GetMapping("/api/orders/{id}/shipments")
    public List<View> list(@PathVariable UUID id,Authentication auth) {
        return service.list(id.toString(),(Long)auth.getPrincipal(),auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")));
    }
    @PostMapping("/api/admin/orders/{id}/shipments/{direction}/tracking") @PreAuthorize("hasRole('ADMIN')")
    public View tracking(@PathVariable UUID id,@PathVariable Direction direction) { return service.refresh(id.toString(),direction); }
    @PostMapping("/api/admin/orders/{id}/shipments/{direction}/label") @PreAuthorize("hasRole('ADMIN')")
    public Label label(@PathVariable UUID id,@PathVariable Direction direction) { return service.label(id.toString(),direction); }
    @PostMapping("/api/admin/orders/{id}/shipments/{direction}/pickup") @PreAuthorize("hasRole('ADMIN')")
    public View pickup(@PathVariable UUID id,@PathVariable Direction direction,@Valid @RequestBody Pickup request) {
        return service.pickup(id.toString(),direction,request);
    }
}
