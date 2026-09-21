package com.ecommerce.product_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String sku;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private Integer stockQuantity;
    @Column(length = 100)
    private String category;
    @Column(length = 2048)
    private String imageUrl;
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    @Column(nullable = false)
    private boolean active = true;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    @PrePersist
    void normalizeSku() {
        sku = sku.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public Long getId() {
        return this.id;
    }

    public void setSku(final String sku) {
        this.sku = sku;
    }

    public String getSku() {
        return this.sku;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setPrice(final BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public void setStockQuantity(final Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getStockQuantity() {
        return this.stockQuantity;
    }

    public String getCategory() {
        return this.category;
    }

    public void setCategory(final String category) {
        this.category = category;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return this.active;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }
}
