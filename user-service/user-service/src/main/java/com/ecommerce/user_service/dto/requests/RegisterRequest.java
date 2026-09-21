package com.ecommerce.user_service.dto.requests;

import jakarta.validation.constraints.*;

public class RegisterRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
    @NotBlank
    @Email
    private String email;
    @NotBlank
    @Pattern(regexp = "^[+]?[0-9]{7,15}$", message = "phone must contain 7-15 digits")
    private String phone;
    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    public String getName() {
        return this.name;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPhone() {
        return this.phone;
    }

    public String getPassword() {
        return this.password;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }

    public void setPassword(final String password) {
        this.password = password;
    }
}
