package com.ecommerce.order_service.events;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "order_event_outbox", indexes = @Index(columnList = "publishedAt,id"))
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true) public String eventId;
    @Column(nullable = false) public String orderId;
    @Lob @Column(nullable = false, columnDefinition = "longtext") public String payload;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant publishedAt;
    @Column(length = 256) public String traceParent;
    @Column(length = 512) public String traceState;
}
