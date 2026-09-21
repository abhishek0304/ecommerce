package com.ecommerce.user_service.dto.response;
public record AuthResponse(String accessToken,String refreshToken,UserResponse user) {}
