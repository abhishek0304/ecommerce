package com.ecommerce.user_service.controller;

import com.ecommerce.user_service.dto.requests.*;
import com.ecommerce.user_service.dto.response.*;
import com.ecommerce.user_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest r) {
        return service.register(r);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication a) {
        return service.me(a.getName());
    }

    @GetMapping("/{id}")
    public UserResponse get(Authentication authentication, @PathVariable Long id) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin && !service.me(authentication.getName()).id().equals(id)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only view your own profile");
        }
        return service.get(id);
    }

    @PutMapping({"/me", "/profile"})
    public UserResponse update(Authentication a, @Valid @RequestBody ProfileRequest r) {
        return service.update(a.getName(), r);
    }

    @PutMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Authentication a, @Valid @RequestBody PasswordRequests.Change r) {
        service.changePassword(a.getName(), r);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a) {
        service.delete(a.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteById(Authentication a, @PathVariable Long id) {
        if (!service.me(a.getName()).id().equals(id)) throw new com.ecommerce.user_service.exception.ApiException("You can only delete your own account");
        service.delete(a.getName());
    }

    public UserController(final UserService service) {
        this.service = service;
    }
}
