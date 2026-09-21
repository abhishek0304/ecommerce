package com.ecommerce.user_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false, unique = true, length = 30)
    private String phone;
    @JsonIgnore
    @Column(nullable = false)
    private String password;
    private String gender;
    private LocalDate dob;
    private String profileImage;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;
    @Column(nullable = false)
    private boolean active = true;
    private boolean emailVerified;
    private boolean phoneVerified;
    private Instant lastLogin;
    private int loginCount;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant passwordChangedOn;
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getEmail() {
        return this.email;
    }

    public String getPhone() {
        return this.phone;
    }

    public String getPassword() {
        return this.password;
    }

    public String getGender() {
        return this.gender;
    }

    public LocalDate getDob() {
        return this.dob;
    }

    public String getProfileImage() {
        return this.profileImage;
    }

    public AccountStatus getStatus() {
        return this.status;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isEmailVerified() {
        return this.emailVerified;
    }

    public boolean isPhoneVerified() {
        return this.phoneVerified;
    }

    public Instant getLastLogin() {
        return this.lastLogin;
    }

    public int getLoginCount() {
        return this.loginCount;
    }

    public int getFailedLoginAttempts() {
        return this.failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return this.lockedUntil;
    }

    public Instant getPasswordChangedOn() {
        return this.passwordChangedOn;
    }

    public Set<Role> getRoles() {
        return this.roles;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public void setGender(final String gender) {
        this.gender = gender;
    }

    public void setDob(final LocalDate dob) {
        this.dob = dob;
    }

    public void setProfileImage(final String profileImage) {
        this.profileImage = profileImage;
    }

    public void setStatus(final AccountStatus status) {
        this.status = status;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public void setEmailVerified(final boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public void setPhoneVerified(final boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public void setLastLogin(final Instant lastLogin) {
        this.lastLogin = lastLogin;
    }

    public void setLoginCount(final int loginCount) {
        this.loginCount = loginCount;
    }

    public void setFailedLoginAttempts(final int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public void setLockedUntil(final Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public void setPasswordChangedOn(final Instant passwordChangedOn) {
        this.passwordChangedOn = passwordChangedOn;
    }

    public void setRoles(final Set<Role> roles) {
        this.roles = roles;
    }

    public User() {
    }
}
