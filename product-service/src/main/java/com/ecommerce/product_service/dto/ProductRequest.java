package com.ecommerce.product_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, or hyphens") String sku,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull @PositiveOrZero @Max(1_000_000) Integer stockQuantity,
        @Size(max = 100) String category,
        Boolean active,
        @Size(max = 2048) @Pattern(regexp = "^$|https://[^\\s]+", message = "must be an HTTPS image URL") String imageUrl) {
    public ProductRequest(String sku, String name, String description, BigDecimal price, Integer stockQuantity, String category, Boolean active) {
        this(sku, name, description, price, stockQuantity, category, active, null);
    }
}
