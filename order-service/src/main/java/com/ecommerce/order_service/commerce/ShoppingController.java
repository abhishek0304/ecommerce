package com.ecommerce.order_service.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShoppingController {
    private final ShoppingService shopping;
    public ShoppingController(ShoppingService shopping) { this.shopping = shopping; }
    @GetMapping("/api/reviews/products/{productId}")
    public ShoppingService.Reviews reviews(@PathVariable @Positive Long productId,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return shopping.reviews(productId, page, size);
    }
    @PostMapping("/api/orders/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingService.ReviewView review(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ShoppingService.ReviewRequest request) {
        return shopping.review((Long) auth.getPrincipal(), id.toString(), request);
    }
    @GetMapping("/api/wishlist")
    public Page<WishlistEntry> wishlist(Authentication auth, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return shopping.wishlist((Long) auth.getPrincipal(), page, size); }
    @PutMapping("/api/wishlist/{productId}")
    public WishlistEntry save(Authentication auth, @PathVariable @Positive Long productId) { return shopping.save((Long) auth.getPrincipal(), productId); }
    @DeleteMapping("/api/wishlist/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable @Positive Long productId) { shopping.remove((Long) auth.getPrincipal(), productId); }
}
