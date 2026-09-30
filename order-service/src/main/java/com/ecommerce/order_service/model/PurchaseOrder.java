package com.ecommerce.order_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_orders", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "key_hash"}),
        @UniqueConstraint(columnNames = {"user_id", "cart_version"})})
public class PurchaseOrder {
    @Id public String id;
    @Version public Long version;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "key_hash", nullable = false, length = 64) public String keyHash;
    @Column(name = "cart_version", nullable = false) public Long cartVersion;
    @Column(nullable = false) public Long addressId;
    @Column(nullable = false) public String paymentMethod;
    @Column(nullable = false) public String status = "CREATING";
    @Column(nullable = false) public String paymentStatus = "UNPAID";
    @Lob @Column(nullable = false, columnDefinition = "longtext") public String addressJson;
    @Lob @Column(nullable = false, columnDefinition = "longtext") public String cartJson;
    @Lob public String itemsJson;
    @Column(precision = 19, scale = 2) public BigDecimal total;
    @Column(precision = 19, scale = 2) public BigDecimal subtotal;
    @Column(precision = 19, scale = 2) public BigDecimal discount;
    @Column(length = 40) public String couponCode;
    @Column(columnDefinition = "boolean default false") public boolean couponRedeemed;
    public String returnStatus;
    @Column(length = 1000) public String returnReason;
    @Column(length = 1000) public String returnDecisionNote;
    public Instant returnRequestedAt;
    public Instant returnReceivedAt;
    public Boolean returnRestock;
    @Column(length = 150) public String manualRefundReference;
    @Column(nullable = false) public String currency = "INR";
    @Column(unique = true) public String razorpayOrderId;
    @Column(unique = true) public String razorpayPaymentId;
    public boolean paymentCreationAttempted;
    public boolean cartCleanupDone;
    public boolean cartCleared;
    public String failureReason;
    public Instant expiresAt;
    public String closureReason;
    @Column(columnDefinition = "boolean default false") public boolean stockReleased;
    @Column(columnDefinition = "boolean default false") public boolean inventoryCommitted;
    public String refundStatus = "NONE";
    public String refundAttemptId;
    public String razorpayRefundId;
    public String refundFailure;
    public String carrier;
    @Column(columnDefinition = "boolean default false") public boolean providerShipmentRequested;
    public String trackingNumber;
    public Instant shippedAt;
    public Instant deliveredAt;
    @Column(columnDefinition = "bigint default 0") public long eventSequence;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
