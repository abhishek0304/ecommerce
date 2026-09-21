package com.ecommerce.user_service.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdDate;
    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedDate;
    @Column(updatable = false)
    private String createdBy;
    private String updatedBy;

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public Instant getUpdatedDate() {
        return this.updatedDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public String getUpdatedBy() {
        return this.updatedBy;
    }

    public void setCreatedDate(final Instant createdDate) {
        this.createdDate = createdDate;
    }

    public void setUpdatedDate(final Instant updatedDate) {
        this.updatedDate = updatedDate;
    }

    public void setCreatedBy(final String createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdatedBy(final String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
