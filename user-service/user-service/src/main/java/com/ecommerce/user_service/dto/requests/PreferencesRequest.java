package com.ecommerce.user_service.dto.requests;


public class PreferencesRequest {
    String language;
    String currency;
    Boolean darkMode;
    Boolean emailNotifications;
    Boolean smsNotifications;
    Boolean whatsAppNotifications;
    Boolean pushNotifications;
    String communicationPreference;

    public String getLanguage() {
        return this.language;
    }

    public String getCurrency() {
        return this.currency;
    }

    public Boolean getDarkMode() {
        return this.darkMode;
    }

    public Boolean getEmailNotifications() {
        return this.emailNotifications;
    }

    public Boolean getSmsNotifications() {
        return this.smsNotifications;
    }

    public Boolean getWhatsAppNotifications() {
        return this.whatsAppNotifications;
    }

    public Boolean getPushNotifications() {
        return this.pushNotifications;
    }

    public String getCommunicationPreference() {
        return this.communicationPreference;
    }

    public void setLanguage(final String language) {
        this.language = language;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public void setDarkMode(final Boolean darkMode) {
        this.darkMode = darkMode;
    }

    public void setEmailNotifications(final Boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public void setSmsNotifications(final Boolean smsNotifications) {
        this.smsNotifications = smsNotifications;
    }

    public void setWhatsAppNotifications(final Boolean whatsAppNotifications) {
        this.whatsAppNotifications = whatsAppNotifications;
    }

    public void setPushNotifications(final Boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
    }

    public void setCommunicationPreference(final String communicationPreference) {
        this.communicationPreference = communicationPreference;
    }
}
