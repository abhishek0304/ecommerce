package com.ecommerce.user_service.entity;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false, length = 128)
    private String otp;
    @Column(nullable = false)
    private Instant expiresAt;
    private boolean used;

    public Long getId() {
        return this.id;
    }

    public User getUser() {
        return this.user;
    }

    public String getOtp() {
        return this.otp;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isUsed() {
        return this.used;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public void setOtp(final String otp) {
        this.otp = otp;
    }

    public void setExpiresAt(final Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setUsed(final boolean used) {
        this.used = used;
    }

    public PasswordResetToken() {
    }
}
