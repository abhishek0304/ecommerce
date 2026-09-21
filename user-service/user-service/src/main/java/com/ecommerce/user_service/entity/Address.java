package com.ecommerce.user_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class Address extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(optional = false)
    private User user;
    @Column(nullable = false)
    private String line1;
    private String line2;
    @Column(nullable = false)
    private String city;
    @Column(nullable = false)
    private String state;
    @Column(nullable = false)
    private String postalCode;
    @Column(nullable = false)
    private String country;
    private boolean defaultAddress;

    public Long getId() {
        return this.id;
    }

    public User getUser() {
        return this.user;
    }

    public String getLine1() {
        return this.line1;
    }

    public String getLine2() {
        return this.line2;
    }

    public String getCity() {
        return this.city;
    }

    public String getState() {
        return this.state;
    }

    public String getPostalCode() {
        return this.postalCode;
    }

    public String getCountry() {
        return this.country;
    }

    public boolean isDefaultAddress() {
        return this.defaultAddress;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public void setLine1(final String line1) {
        this.line1 = line1;
    }

    public void setLine2(final String line2) {
        this.line2 = line2;
    }

    public void setCity(final String city) {
        this.city = city;
    }

    public void setState(final String state) {
        this.state = state;
    }

    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
    }

    public void setCountry(final String country) {
        this.country = country;
    }

    public void setDefaultAddress(final boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    public Address() {
    }
}
