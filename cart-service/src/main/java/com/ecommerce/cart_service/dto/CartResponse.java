package com.ecommerce.cart_service.dto;
import java.math.BigDecimal;
import java.util.List;
public record CartResponse(Long userId, List<Item> items, long totalQuantity, BigDecimal totalPrice, Long version) {
    public CartResponse(Long userId, List<Item> items, long totalQuantity, BigDecimal totalPrice) {
        this(userId, items, totalQuantity, totalPrice, null);
    }
    public record Item(Long productId, String name, int quantity, BigDecimal unitPrice,
                       BigDecimal subtotal, boolean available) {}
}
