package com.ecommerce.user_service.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 40)
    private RoleName name;

    public Role(RoleName name) {
        this.name = name;
    }

    public Long getId() {
        return this.id;
    }

    public RoleName getName() {
        return this.name;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void setName(final RoleName name) {
        this.name = name;
    }

    public Role() {
    }
}
