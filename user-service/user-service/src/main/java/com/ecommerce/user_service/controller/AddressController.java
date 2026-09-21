package com.ecommerce.user_service.controller;

import com.ecommerce.user_service.dto.requests.AddressRequest;
import com.ecommerce.user_service.entity.Address;
import com.ecommerce.user_service.service.AccountSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AccountSettingsService service;

    @GetMapping
    public List<Address> list(Authentication a) {
        return service.addresses(a.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Address add(Authentication a, @Valid @RequestBody AddressRequest r) {
        return service.add(a.getName(), r);
    }

    @PutMapping("/{id}")
    public Address update(Authentication a, @PathVariable Long id, @Valid @RequestBody AddressRequest r) {
        return service.update(a.getName(), id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a, @PathVariable Long id) {
        service.delete(a.getName(), id);
    }

    @PatchMapping("/{id}/default")
    public Address defaultAddress(Authentication a, @PathVariable Long id) {
        return service.makeDefault(a.getName(), id);
    }

    public AddressController(final AccountSettingsService service) {
        this.service = service;
    }
}
