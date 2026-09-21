package com.ecommerce.user_service.dto.requests;

import jakarta.validation.constraints.*;

public class AddressRequest {
    @NotBlank
    String line1;
    String line2;
    @NotBlank
    String city;
    @NotBlank
    String state;
    @NotBlank
    String postalCode;
    @NotBlank
    String country;

    public String getLine1() {
        return this.line1;
    }

    public String getLine2() {
        return this.line2;
    }

    public String getCity() {
        return this.city;
    }

    public String getState() {
        return this.state;
    }

    public String getPostalCode() {
        return this.postalCode;
    }

    public String getCountry() {
        return this.country;
    }

    public void setLine1(final String line1) {
        this.line1 = line1;
    }

    public void setLine2(final String line2) {
        this.line2 = line2;
    }

    public void setCity(final String city) {
        this.city = city;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
    }

    public void setCountry(final String country) {
        this.country = country;
    }
}
