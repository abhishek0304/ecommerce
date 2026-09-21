package com.ecommerce.user_service.messaging;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="account_message_outbox",indexes=@Index(columnList="status,nextAttemptAt"))
public class AccountMessage {
    @Id public String id;
    public Long userId;
    @Column(length=320) public String recipient;
    public String subject;
    @Column(length=2000) public String body;
    public Instant expiresAt;
    public String status="PENDING";
    public Instant nextAttemptAt=Instant.now();
    public int attempts;
}
