package com.ecommerce.order_service.api;
import com.ecommerce.order_service.api.OrderDtos.*;
import com.ecommerce.order_service.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final OrderService service;
    public AdminOrderController(OrderService service) { this.service = service; }
    @GetMapping
    public org.springframework.data.domain.Page<OrderView> list(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int size) {
        return service.adminList(page, size);
    }
    @GetMapping("/{id}")
    public OrderView get(@PathVariable UUID id) { return service.adminGet(id.toString()); }
    @PatchMapping("/{id}/status")
    public OrderView fulfill(@PathVariable UUID id, @Valid @RequestBody Fulfillment request) {
        return service.fulfill(id.toString(), request);
    }
    @PostMapping("/{id}/refund/retry")
    public OrderView retryRefund(@PathVariable UUID id) { return service.retryRefund(id.toString()); }
}
