package com.ecommerce.user_service.dto.requests;

import jakarta.validation.constraints.*;

public class LoginRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String password;

    public String getEmail() {
        return this.email;
    }

    public String getPassword() {
        return this.password;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setPassword(final String password) {
        this.password = password;
    }
}
