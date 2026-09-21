package com.ecommerce.product_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String category,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        String imageUrl) {
    public ProductResponse(Long id, String sku, String name, String description, BigDecimal price, Integer stockQuantity, String category, boolean active, Instant createdAt, Instant updatedAt) {
        this(id, sku, name, description, price, stockQuantity, category, active, createdAt, updatedAt, null);
    }
}
