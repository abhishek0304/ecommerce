package com.ecommerce.user_service.service;

import com.ecommerce.user_service.dto.requests.*;
import com.ecommerce.user_service.dto.response.*;
import com.ecommerce.user_service.entity.*;
import com.ecommerce.user_service.exception.ApiException;
import com.ecommerce.user_service.repository.*;
import com.ecommerce.user_service.util.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshes;
    private final VerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final UserPreferencesRepository preferences;
    private final LoginHistoryRepository history;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EventPublisher events;
    private final com.ecommerce.user_service.messaging.AccountMessages messages;

    public UserResponse register(RegisterRequest r) {
        String email = r.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw new ApiException("Email is already registered");
        if (users.existsByPhone(r.getPhone())) throw new ApiException("Phone is already registered");
        User u = new User();
        u.setName(r.getName());
        u.setEmail(email);
        u.setPhone(r.getPhone());
        u.setPassword(encoder.encode(r.getPassword()));
        u.getRoles().add(roles.findByName(RoleName.ROLE_USER).orElseThrow());
        users.save(u);
        UserPreferences p = new UserPreferences();
        p.setUser(u);
        preferences.save(p);
        createVerificationOtp(u);
        events.publish("UserRegisteredEvent", u.getId(), u.getEmail());
        return UserResponse.from(u);
    }

    @Transactional(dontRollbackOn = ApiException.class)
    public AuthResponse login(LoginRequest r, String device, String ip) {
        User u = byEmail(r.getEmail());
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now())) throw new ApiException("Account is locked. Try again later.");
        if (u.getStatus() == AccountStatus.LOCKED && u.getLockedUntil() != null) {
            u.setStatus(AccountStatus.ACTIVE);
            u.setLockedUntil(null);
            u.setFailedLoginAttempts(0);
        }
        if (!u.isActive() || u.getStatus() != AccountStatus.ACTIVE) throw new ApiException("Account is not active");
        if (!encoder.matches(r.getPassword(), u.getPassword())) {
            u.setFailedLoginAttempts(u.getFailedLoginAttempts() + 1);
            if (u.getFailedLoginAttempts() >= 5) {
                u.setStatus(AccountStatus.LOCKED);
                u.setLockedUntil(Instant.now().plus(Duration.ofMinutes(30)));
            }
            recordLogin(u, device, ip, false);
            throw new ApiException("Invalid email or password");
        }
        u.setFailedLoginAttempts(0);
        u.setLastLogin(Instant.now());
        u.setLoginCount(u.getLoginCount() + 1);
        recordLogin(u, device, ip, true);
        return new AuthResponse(jwt.accessToken(u), createRefresh(u, device), UserResponse.from(u));
    }

    public AuthResponse refresh(String token) {
        RefreshToken rt = refreshes.findByToken(token).orElseThrow(() -> new ApiException("Invalid refresh token"));
        if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) throw new ApiException("Expired refresh token");
        if (!rt.getUser().isActive() || rt.getUser().getStatus() != AccountStatus.ACTIVE) throw new ApiException("Account is not active");
        rt.setRevoked(true);
        return new AuthResponse(jwt.accessToken(rt.getUser()), createRefresh(rt.getUser(), rt.getDevice()), UserResponse.from(rt.getUser()));
    }

    public UserResponse get(Long id) {
        return UserResponse.from(users.findById(id).orElseThrow(() -> new ApiException("User not found")));
    }

    public UserResponse me(String email) {
        return UserResponse.from(byEmail(email));
    }

    public UserResponse update(String email, ProfileRequest r) {
        User u = byEmail(email);
        if (!u.getPhone().equals(r.getPhone()) && users.existsByPhone(r.getPhone())) throw new ApiException("Phone is already registered");
        u.setName(r.getName());
        u.setPhone(r.getPhone());
        u.setDob(r.getDob());
        u.setGender(r.getGender());
        events.publish("UserUpdatedEvent", u.getId(), u.getEmail());
        return UserResponse.from(u);
    }

    public void changePassword(String email, PasswordRequests.Change r) {
        User u = byEmail(email);
        if (!encoder.matches(r.getOldPassword(), u.getPassword())) throw new ApiException("Old password is incorrect");
        setPassword(u, r.getNewPassword());
        events.publish("PasswordChangedEvent", u.getId(), u.getEmail());
    }

    public void forgot(PasswordRequests.Forgot r) {
        User u = users.lockByEmail(r.getEmail().trim()).orElseThrow(() -> new ApiException("User not found"));
        resetTokens.findFirstByUserOrderByIdDesc(u).ifPresent(t -> requireOtpCooldown(t.getExpiresAt()));
        PasswordResetToken t = new PasswordResetToken();
        t.setUser(u);
        t.setOtp(otp());
        t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        resetTokens.save(t);
        messages.otp(u,t.getOtp(),t.getExpiresAt(),true);
        events.publish("PasswordResetOtpRequested", u.getId(), u.getEmail());
    }

    public void resendVerification(String email) {
        User u=users.lockByEmail(email.trim()).orElseThrow(() -> new ApiException("User not found"));
        if(u.isEmailVerified()) return;
        verificationTokens.findFirstByUserOrderByIdDesc(u).ifPresent(t -> requireOtpCooldown(t.getExpiresAt()));
        createVerificationOtp(u);
    }

    private void requireOtpCooldown(Instant expiry) {
        if(expiry.minus(Duration.ofMinutes(9)).isAfter(Instant.now())) throw new ApiException("Wait 60 seconds before requesting another code");
    }

    public void reset(PasswordRequests.Reset r) {
        User u = byEmail(r.getEmail());
        PasswordResetToken t = resetTokens.findFirstByUserAndOtpAndUsedFalseOrderByIdDesc(u, r.getOtp()).orElseThrow(() -> new ApiException("Invalid OTP"));
        if (t.getExpiresAt().isBefore(Instant.now())) throw new ApiException("OTP has expired");
        t.setUsed(true);
        setPassword(u, r.getNewPassword());
        refreshes.revokeAll(u);
        events.publish("PasswordChangedEvent", u.getId(), u.getEmail());
    }

    public void verify(PasswordRequests.Verify r) {
        User u = byEmail(r.getEmail());
        VerificationToken t = verificationTokens.findFirstByUserAndOtpAndUsedFalseOrderByIdDesc(u, r.getOtp()).orElseThrow(() -> new ApiException("Invalid OTP"));
        if (t.getExpiresAt().isBefore(Instant.now())) throw new ApiException("OTP has expired");
        t.setUsed(true);
        u.setEmailVerified(true);
    }

    public void delete(String email) {
        User u = byEmail(email);
        u.setActive(false);
        u.setStatus(AccountStatus.DELETED);
        refreshes.revokeAll(u);
        events.publish("AccountDeletedEvent", u.getId(), u.getEmail());
    }

    public void logout(String email, String token) {
        refreshes.findByToken(token).filter(t -> t.getUser().getEmail().equalsIgnoreCase(email)).ifPresent(t -> t.setRevoked(true));
    }

    public void logoutAll(String email) {
        refreshes.revokeAll(byEmail(email));
    }

    private User byEmail(String e) {
        return users.findByEmailIgnoreCase(e.trim()).orElseThrow(() -> new ApiException("User not found"));
    }

    private String createRefresh(User u, String device) {
        RefreshToken t = new RefreshToken();
        t.setUser(u);
        t.setToken(UUID.randomUUID().toString());
        t.setDevice(device);
        t.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        refreshes.save(t);
        return t.getToken();
    }

    private void createVerificationOtp(User u) {
        VerificationToken t = new VerificationToken();
        t.setUser(u);
        t.setOtp(otp());
        t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        verificationTokens.save(t);
        messages.otp(u,t.getOtp(),t.getExpiresAt(),false);
        events.publish("EmailVerificationOtpRequested", u.getId(), u.getEmail());
    }

    private String otp() {
        return String.format(Locale.ROOT, "%06d", new java.security.SecureRandom().nextInt(1000000));
    }

    private void setPassword(User u, String p) {
        u.setPassword(encoder.encode(p));
        u.setPasswordChangedOn(Instant.now());
    }

    private void recordLogin(User u, String d, String ip, boolean ok) {
        LoginHistory h = new LoginHistory();
        h.setUser(u);
        h.setDevice(d);
        h.setIpAddress(ip);
        h.setSuccessful(ok);
        h.setLoginAt(Instant.now());
        history.save(h);
    }

    public UserService(final UserRepository users, final RoleRepository roles, final RefreshTokenRepository refreshes, final VerificationTokenRepository verificationTokens, final PasswordResetTokenRepository resetTokens, final UserPreferencesRepository preferences, final LoginHistoryRepository history, final PasswordEncoder encoder, final JwtService jwt, final EventPublisher events, final com.ecommerce.user_service.messaging.AccountMessages messages) {
        this.users = users;
        this.roles = roles;
        this.refreshes = refreshes;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.preferences = preferences;
        this.history = history;
        this.encoder = encoder;
        this.jwt = jwt;
        this.events = events;
        this.messages = messages;
    }
}
