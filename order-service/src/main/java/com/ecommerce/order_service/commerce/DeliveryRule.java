package com.ecommerce.order_service.commerce;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="delivery_rules")
public class DeliveryRule {
    @Id @Column(length=6) public String postalPrefix;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal fee;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal freeAbove;
    @Column(nullable=false) public int minDays;
    @Column(nullable=false) public int maxDays;
    @Column(nullable=false) public boolean active=true;
}
