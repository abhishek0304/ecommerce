package com.ecommerce.order_service.commerce;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "product_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "productId"}))
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public Long productId;
    @Column(nullable = false) public String orderId;
    public int rating;
    @Column(length = 2000) public String comment;
    public Instant createdAt = Instant.now();
}
