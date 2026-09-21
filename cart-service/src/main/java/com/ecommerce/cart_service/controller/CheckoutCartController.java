package com.ecommerce.cart_service.controller;

import com.ecommerce.cart_service.repository.CartRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/carts")
public class CheckoutCartController {
    private final CartRepository carts;
    public CheckoutCartController(CartRepository carts) { this.carts = carts; }
    public record Snapshot(Long userId, Long version, Map<Long, Integer> items) {}
    public record Consume(Long version) {}

    @GetMapping("/{userId}")
    @Transactional(readOnly = true)
    public Snapshot snapshot(@PathVariable Long userId) {
        var cart = carts.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty"));
        if (cart.getItems().isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        return new Snapshot(userId, cart.getVersion(), Map.copyOf(cart.getItems()));
    }
    @PostMapping("/{userId}/consume")
    @Transactional
    public Map<String, Boolean> consume(@PathVariable Long userId, @RequestBody Consume request) {
        var cart = carts.findLocked(userId);
        boolean cleared = cart.isPresent() && request.version() != null && request.version().equals(cart.get().getVersion());
        if (cleared) cart.get().getItems().clear();
        return Map.of("cleared", cleared);
    }
}
