package com.ecommerce.user_service.controller;

import com.ecommerce.user_service.dto.requests.PasswordRequests;
import com.ecommerce.user_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Compatibility endpoint retained for clients using POST /forgot-password.
 */
@RestController
public class LegacyPasswordController {
    private final UserService service;

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody PasswordRequests.Forgot request) {
        service.forgot(request);
    }

    public LegacyPasswordController(final UserService service) {
        this.service = service;
    }
}
