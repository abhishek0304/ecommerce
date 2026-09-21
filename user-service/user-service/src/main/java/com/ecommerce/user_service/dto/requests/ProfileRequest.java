package com.ecommerce.user_service.dto.requests;

import jakarta.validation.constraints.*;
import java.time.*;

public class ProfileRequest {
    @NotBlank
    @Size(max = 100)
    private String name;
    private LocalDate dob;
    private String gender;
    @NotBlank
    @Pattern(regexp = "^[+]?[0-9]{7,15}$")
    private String phone;

    public String getName() {
        return this.name;
    }

    public LocalDate getDob() {
        return this.dob;
    }

    public String getGender() {
        return this.gender;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setDob(final LocalDate dob) {
        this.dob = dob;
    }

    public void setGender(final String gender) {
        this.gender = gender;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }
}
