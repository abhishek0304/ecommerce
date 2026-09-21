package com.ecommerce.cart_service.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public final class CartRequests {
    private CartRequests() {}
    public record AddItem(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {}
    public record UpdateQuantity(@NotNull @Positive Integer quantity) {}
}
