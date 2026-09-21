package com.ecommerce.notification_service;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "notifications", indexes = @Index(columnList = "userId,receivedAt"))
public class Notification {
    @Id public String eventId;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public String orderId;
    @Column(nullable = false) public long eventSequence;
    @Column(nullable = false) public String type;
    @Column(nullable = false, length = 512) public String message;
    @Column(nullable = false) public Instant occurredAt;
    @Column(nullable = false) public Instant receivedAt = Instant.now();
}
