package com.ecommerce.user_service.dto.requests;

import jakarta.validation.constraints.*;

public class RefreshRequest {
    @NotBlank
    private String refreshToken;

    public String getRefreshToken() {
        return this.refreshToken;
    }

    public void setRefreshToken(final String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
