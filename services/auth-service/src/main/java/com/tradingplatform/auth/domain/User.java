package com.tradingplatform.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public static User create(String email, String passwordHash) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email.toLowerCase();
        user.passwordHash = passwordHash;
        user.tier = Tier.FREE;
        user.status = Status.ACTIVE;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Tier getTier() {
        return tier;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum Tier {
        FREE, PREMIUM, PARTNER
    }

    public enum Status {
        PENDING_VERIFICATION, ACTIVE, SUSPENDED
    }
}
