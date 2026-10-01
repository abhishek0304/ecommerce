package com.ecommerce.product_service.inventory;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="stock_movements", indexes=@Index(columnList="productId,createdAt"))
public class StockMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long productId;
    @Column(nullable=false) public int delta;
    @Column(nullable=false) public int resultingQuantity;
    @Column(nullable=false,length=40) public String reason;
    @Column(length=150) public String reference;
    @Column(nullable=false) public Instant createdAt=Instant.now();
}
