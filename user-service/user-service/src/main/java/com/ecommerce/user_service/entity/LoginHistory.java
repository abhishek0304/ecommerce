package com.ecommerce.user_service.entity;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "login_history")
public class LoginHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User user;
    private Instant loginAt;
    private String device;
    private String ipAddress;
    private boolean successful;

    public Long getId() {
        return this.id;
    }

    public User getUser() {
        return this.user;
    }

    public Instant getLoginAt() {
        return this.loginAt;
    }

    public String getDevice() {
        return this.device;
    }

    public String getIpAddress() {
        return this.ipAddress;
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public void setLoginAt(final Instant loginAt) {
        this.loginAt = loginAt;
    }

    public void setDevice(final String device) {
        this.device = device;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setSuccessful(final boolean successful) {
        this.successful = successful;
    }

    public LoginHistory() {
    }
}
