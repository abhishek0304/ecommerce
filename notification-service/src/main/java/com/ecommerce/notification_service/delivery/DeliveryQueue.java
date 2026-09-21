package com.ecommerce.notification_service.delivery;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DeliveryQueue {
    public record Request(@NotNull @Positive Long userId, @NotNull Delivery.Channel channel,
            @NotBlank @Email @Size(max=320) String recipient, @NotBlank @Size(max=160) String subject,
            @NotBlank @Size(max=2000) String body, @NotNull Instant expiresAt) {}
    private final DeliveryRepository deliveries;
    public DeliveryQueue(DeliveryRepository deliveries) { this.deliveries = deliveries; }
    @Transactional
    public String enqueue(String id, Request request) {
        if (request.channel() != Delivery.Channel.EMAIL) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account messages require email");
        String fingerprint = hash(request.userId()+"\n"+request.channel()+"\n"+request.recipient()+"\n"+request.subject()+"\n"+request.body()+"\n"+request.expiresAt());
        var existing = deliveries.findById(id);
        if (existing.isPresent()) {
            if (!existing.get().fingerprint.equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Message ID reused with different content");
            return existing.get().status;
        }
        if (!request.expiresAt().isAfter(Instant.now()) || request.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(1))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message expiry");
        Delivery d = new Delivery(); d.id=id; d.userId=request.userId(); d.channel=request.channel();
        d.recipient=request.recipient(); d.subject=request.subject(); d.body=request.body(); d.expiresAt=request.expiresAt();
        d.fingerprint=fingerprint; d.type="ACCOUNT_SECURITY"; deliveries.saveAndFlush(d); return d.status;
    }
    @Transactional
    public void order(String eventId, Long userId, String orderId, String type, String message) {
        for (Delivery.Channel channel : Delivery.Channel.values()) {
            String id=UUID.nameUUIDFromBytes((eventId+":"+channel).getBytes(StandardCharsets.UTF_8)).toString();
            if (deliveries.existsById(id)) continue;
            Delivery d=new Delivery(); d.id=id; d.userId=userId; d.channel=channel; d.orderId=orderId;
            d.type=type; d.subject="Your ecommerce order update"; d.body=message+" Order: "+orderId;
            d.expiresAt=Instant.now().plus(Duration.ofDays(1)); d.fingerprint=hash(eventId+channel); deliveries.save(d);
        }
    }
    private static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch (java.security.NoSuchAlgorithmException ex) {throw new IllegalStateException(ex);}
    }
}
