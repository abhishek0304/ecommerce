package com.ecommerce.user_service.entity;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name = "device_sessions")
public class DeviceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false, unique = true)
    private String sessionId;
    private String device;
    private Instant lastActive;
    private boolean active = true;

    public Long getId() {
        return this.id;
    }

    public User getUser() {
        return this.user;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public String getDevice() {
        return this.device;
    }

    public Instant getLastActive() {
        return this.lastActive;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public void setDevice(final String device) {
        this.device = device;
    }

    public void setLastActive(final Instant lastActive) {
        this.lastActive = lastActive;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public DeviceSession() {
    }
}
