package com.ecommerce.user_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @OneToOne(optional = false)
    private User user;
    private String language = "en";
    private String currency = "INR";
    private boolean darkMode;
    private boolean emailNotifications = true;
    private boolean smsNotifications = true;
    private boolean whatsAppNotifications;
    private boolean pushNotifications = true;
    private String communicationPreference = "EMAIL";

    public Long getId() {
        return this.id;
    }

    public User getUser() {
        return this.user;
    }

    public String getLanguage() {
        return this.language;
    }

    public String getCurrency() {
        return this.currency;
    }

    public boolean isDarkMode() {
        return this.darkMode;
    }

    public boolean isEmailNotifications() {
        return this.emailNotifications;
    }

    public boolean isSmsNotifications() {
        return this.smsNotifications;
    }

    public boolean isWhatsAppNotifications() {
        return this.whatsAppNotifications;
    }

    public boolean isPushNotifications() {
        return this.pushNotifications;
    }

    public String getCommunicationPreference() {
        return this.communicationPreference;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public void setDarkMode(final boolean darkMode) {
        this.darkMode = darkMode;
    }

    public void setEmailNotifications(final boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public void setSmsNotifications(final boolean smsNotifications) {
        this.smsNotifications = smsNotifications;
    }

    public void setWhatsAppNotifications(final boolean whatsAppNotifications) {
        this.whatsAppNotifications = whatsAppNotifications;
    }

    public void setPushNotifications(final boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    public void setCommunicationPreference(final String communicationPreference) {
        this.communicationPreference = communicationPreference;
    }

    public UserPreferences() {
    }
}
