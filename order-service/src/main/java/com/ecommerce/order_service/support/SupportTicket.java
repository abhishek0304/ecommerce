package com.ecommerce.order_service.support;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="support_tickets",indexes=@Index(columnList="userId,createdAt"))
public class SupportTicket {
    @Id public String id=UUID.randomUUID().toString();
    @Version public Long version;
    @Column(nullable=false) public Long userId;
    @Column(length=36) public String orderId;
    @Column(nullable=false,length=150) public String subject;
    @Column(nullable=false,length=20) public String status="OPEN";
    @Column(nullable=false) public Instant createdAt=Instant.now();
    @ElementCollection @CollectionTable(name="support_messages",joinColumns=@JoinColumn(name="ticket_id")) @OrderColumn(name="message_number")
    public List<Message> messages=new ArrayList<>();
    @Embeddable public static class Message {
        @Column(nullable=false,length=2000) public String text;
        @Column(nullable=false) public boolean admin;
        @Column(nullable=false) public Instant createdAt=Instant.now();
        public Message() {} public Message(String text,boolean admin){this.text=text;this.admin=admin;}
    }
}
