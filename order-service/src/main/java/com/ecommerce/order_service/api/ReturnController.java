package com.ecommerce.order_service.api;
import com.ecommerce.order_service.api.OrderDtos.*;
import com.ecommerce.order_service.service.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReturnController {
    private final OrderLifecycle lifecycle;
    private final OrderService orders;
    public ReturnController(OrderLifecycle lifecycle, OrderService orders) { this.lifecycle = lifecycle; this.orders = orders; }
    @PostMapping("/api/orders/{id}/returns")
    public OrderView request(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ReturnRequest request) {
        lifecycle.requestReturn(id.toString(), (Long) auth.getPrincipal(), request);
        return orders.get(id.toString(), (Long) auth.getPrincipal());
    }
    @PatchMapping("/api/admin/orders/{id}/return") @PreAuthorize("hasRole('ADMIN')")
    public OrderView decide(@PathVariable UUID id, @Valid @RequestBody DecideReturn request) {
        lifecycle.decideReturn(id.toString(), request); return orders.adminGet(id.toString());
    }
    @PostMapping("/api/admin/orders/{id}/return/receive") @PreAuthorize("hasRole('ADMIN')")
    public OrderView receive(@PathVariable UUID id, @Valid @RequestBody ReceiveReturn request) {
        lifecycle.startReceiveReturn(id.toString(), request.restock()); orders.advance(id.toString()); return orders.adminGet(id.toString());
    }
    @PostMapping("/api/admin/orders/{id}/refund/manual") @PreAuthorize("hasRole('ADMIN')")
    public OrderView manual(@PathVariable UUID id, @Valid @RequestBody ManualRefund request) {
        lifecycle.manualRefund(id.toString(), request.reference()); return orders.adminGet(id.toString());
    }
}
