package com.ecommerce.order_service.commerce;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name = "coupons")
public class Coupon {
    @Id @Column(length = 40) public String code;
    @Column(nullable = false) public String type;
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2) public BigDecimal value;
    @Column(nullable = false, precision = 19, scale = 2) public BigDecimal minimumSpend;
    @Column(nullable = false) public Instant expiresAt;
    public int usageLimit;
    public int usedCount;
    public boolean active = true;
    @Version public Long version;
}
