package com.ecommerce.user_service.entity;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String token;
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false)
    private Instant expiresAt;
    private boolean revoked;
    private String device;

    public Long getId() {
        return this.id;
    }

    public String getToken() {
        return this.token;
    }

    public User getUser() {
        return this.user;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isRevoked() {
        return this.revoked;
    }

    public String getDevice() {
        return this.device;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setRevoked(final boolean revoked) {
        this.revoked = revoked;
    }

    public void setDevice(final String device) {
        this.device = device;
    }

    public RefreshToken() {
    }
}
