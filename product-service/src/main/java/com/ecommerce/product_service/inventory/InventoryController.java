package com.ecommerce.product_service.inventory;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/inventory/reservations")
public class InventoryController {
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }
    @PutMapping("/{id}")
    public InventoryService.Result reserve(@PathVariable UUID id, @Valid @RequestBody InventoryService.Request request) {
        return service.reserve(id.toString(), request);
    }
    @PostMapping("/{id}/commit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void commit(@PathVariable UUID id) { service.commit(id.toString()); }
    public record ReturnReceipt(@jakarta.validation.constraints.NotNull Boolean restock) {}
    @PostMapping("/{id}/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveReturn(@PathVariable UUID id, @Valid @RequestBody ReturnReceipt receipt) { service.receiveReturn(id.toString(), receipt.restock()); }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID id) { service.release(id.toString()); }
}
