package com.ecommerce.user_service;

import com.ecommerce.user_service.dto.requests.LoginRequest;
import com.ecommerce.user_service.entity.*;
import com.ecommerce.user_service.exception.ApiException;
import com.ecommerce.user_service.repository.*;
import com.ecommerce.user_service.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegressionTests {
    @Autowired UserService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired RefreshTokenRepository refreshes;
    @Autowired MockMvc mvc;
    @Autowired com.ecommerce.user_service.service.AccountSettingsService settings;
    @Autowired AddressRepository addresses;
    @Autowired VerificationTokenRepository verifications;

    private User newUser() {
        User user = new User();
        user.setName("Test User");
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPhone(UUID.randomUUID().toString().substring(0, 20));
        user.setPassword(encoder.encode("correct-password"));
        return users.saveAndFlush(user);
    }

    private LoginRequest login(User user, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword(password);
        return request;
    }

    @Test
    void failedAttemptsPersistAndLockAccount() {
        User user = newUser();
        for (int i = 0; i < 5; i++) {
            assertThrows(ApiException.class, () -> service.login(login(user, "wrong"), "test", "127.0.0.1"));
        }
        User saved = users.findById(user.getId()).orElseThrow();
        assertEquals(5, saved.getFailedLoginAttempts());
        assertEquals(AccountStatus.LOCKED, saved.getStatus());
        assertNotNull(saved.getLockedUntil());
    }

    @Test
    void expiredLockAllowsLogin() {
        User user = newUser();
        user.setStatus(AccountStatus.LOCKED);
        user.setLockedUntil(Instant.now().minusSeconds(1));
        user.setFailedLoginAttempts(5);
        users.saveAndFlush(user);
        assertNotNull(service.login(login(user, "correct-password"), "test", "127.0.0.1"));
        User saved = users.findById(user.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, saved.getStatus());
        assertEquals(0, saved.getFailedLoginAttempts());
        assertNull(saved.getLockedUntil());
    }

    @Test
    void inactiveUserCannotRefresh() {
        User user = newUser();
        user.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(user);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plusSeconds(60));
        refreshes.saveAndFlush(token);
        assertThrows(ApiException.class, () -> service.refresh(token.getToken()));
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/auth/logout").header("X-Refresh-Token", "test")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestsAreClientErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/login")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void selectingDefaultAddressAgainPreservesDefault() {
        User user = newUser();
        Address address = new Address();
        address.setUser(user);
        address.setLine1("1 Test Street");
        address.setCity("Test City");
        address.setState("Test State");
        address.setPostalCode("123456");
        address.setCountry("IN");
        address.setDefaultAddress(true);
        addresses.saveAndFlush(address);
        settings.makeDefault(user.getEmail(), address.getId());
        assertTrue(addresses.findById(address.getId()).orElseThrow().isDefaultAddress());
    }

    @Test
    void emailVerificationDoesNotReactivateSuspendedAccount() {
        User user = newUser();
        user.setStatus(AccountStatus.SUSPENDED);
        users.saveAndFlush(user);
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setOtp("123456");
        token.setExpiresAt(Instant.now().plusSeconds(60));
        verifications.saveAndFlush(token);
        var request = new com.ecommerce.user_service.dto.requests.PasswordRequests.Verify();
        request.setEmail(user.getEmail());
        request.setOtp("123456");
        service.verify(request);
        User saved = users.findById(user.getId()).orElseThrow();
        assertTrue(saved.isEmailVerified());
        assertEquals(AccountStatus.SUSPENDED, saved.getStatus());
    }
}
