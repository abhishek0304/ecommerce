package com.ecommerce.notification_service.delivery;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message_deliveries", indexes = {@Index(columnList = "status,nextAttemptAt"), @Index(columnList = "userId,createdAt")})
public class Delivery {
    public enum Channel { EMAIL, SMS, WHATSAPP }
    @Id public String id;
    @Version public Long version;
    @Column(nullable = false) public Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Channel channel;
    @Column(nullable = false, length = 64) public String fingerprint;
    @Column(length = 320) public String recipient;
    @Column(nullable = false, length = 160) public String subject;
    @Column(nullable = false, length = 2000) public String body;
    public String orderId;
    public String type;
    public String providerId;
    public String status = "PENDING";
    public String failure;
    public int attempts;
    public Instant createdAt = Instant.now();
    public Instant nextAttemptAt = Instant.now();
    public Instant expiresAt;
}
