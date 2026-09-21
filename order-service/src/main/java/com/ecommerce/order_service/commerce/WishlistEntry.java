package com.ecommerce.order_service.commerce;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "wishlist_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}))
public class WishlistEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public Long productId;
    public Instant createdAt = Instant.now();
}
