package com.ecommerce.user_service.controller;

import com.ecommerce.user_service.dto.requests.PreferencesRequest;
import com.ecommerce.user_service.entity.UserPreferences;
import com.ecommerce.user_service.service.AccountSettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/preferences")
public class PreferencesController {
    private final AccountSettingsService service;

    @GetMapping
    public UserPreferences get(Authentication a) {
        return service.preferences(a.getName());
    }

    @PutMapping
    public UserPreferences update(Authentication a, @RequestBody PreferencesRequest r) {
        return service.updatePreferences(a.getName(), r);
    }

    public PreferencesController(final AccountSettingsService service) {
        this.service = service;
    }
}
