package com.ecommerce.cart_service.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="guest_merges")
public class GuestMerge {
    @Id @Column(length=170) public String id;
    @Column(nullable=false,length=64) public String bodyHash;
    @Column(nullable=false) public Instant createdAt=Instant.now();
}
