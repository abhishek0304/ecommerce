package com.ecommerce.user_service.controller;

import com.ecommerce.user_service.dto.requests.*;
import com.ecommerce.user_service.dto.response.*;
import com.ecommerce.user_service.service.UserService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {
    private final UserService service;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest request) {
        return service.login(r, Optional.ofNullable(request.getHeader("User-Agent")).orElse("unknown"), request.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest r) {
        return service.refresh(r.getRefreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgot(@Valid @RequestBody PasswordRequests.Forgot r) {
        service.forgot(r);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody PasswordRequests.Reset r) {
        service.reset(r);
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody PasswordRequests.Verify r) {
        service.verify(r);
    }

    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    public void resend(@Valid @RequestBody PasswordRequests.Forgot r) {service.resendVerification(r.getEmail());}

    public AuthController(final UserService service) {
        this.service = service;
    }
}
