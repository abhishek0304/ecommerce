package com.ecommerce.user_service.controller;

import com.ecommerce.user_service.service.UserService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    private final UserService service;

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication a, @RequestHeader("X-Refresh-Token") String refresh) {
        service.logout(a.getName(), refresh);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(Authentication a) {
        service.logoutAll(a.getName());
    }

    public SessionController(final UserService service) {
        this.service = service;
    }
}
