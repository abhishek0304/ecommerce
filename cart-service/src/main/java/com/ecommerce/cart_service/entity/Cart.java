package com.ecommerce.cart_service.entity;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "carts")
public class Cart {
    @Id
    private Long userId;
    @Version
    private Long version;
    @ElementCollection
    @CollectionTable(name = "cart_items", joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
    @MapKeyColumn(name = "product_id")
    @Column(name = "quantity", nullable = false)
    private Map<Long, Integer> items = new HashMap<>();

    protected Cart() {}
    public Cart(Long userId) { this.userId = userId; }
    public Long getVersion() { return version; }
    public Long getUserId() { return userId; }
    public Map<Long, Integer> getItems() { return items; }
}
