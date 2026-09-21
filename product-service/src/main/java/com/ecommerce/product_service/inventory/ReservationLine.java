package com.ecommerce.product_service.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Embeddable
public class ReservationLine {
    @Column(nullable = false) public Long productId;
    @Column(nullable = false) public String name;
    @Column(nullable = false, precision = 19, scale = 2) public BigDecimal price;
    @Column(nullable = false) public int quantity;
    protected ReservationLine() {}
    public ReservationLine(Long productId, String name, BigDecimal price, int quantity) {
        this.productId = productId; this.name = name; this.price = price; this.quantity = quantity;
    }
}
