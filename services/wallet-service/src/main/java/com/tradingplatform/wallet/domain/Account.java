package com.tradingplatform.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false)
    private BigDecimal balance;

    /** Hibernate appends "AND version = ?" to every UPDATE, so a concurrent write fails instead of silently overwriting. */
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public static Account open(UUID userId, String currency) {
        Account account = new Account();
        account.id = UUID.randomUUID();
        account.userId = userId;
        account.currency = currency;
        account.balance = BigDecimal.ZERO;
        account.createdAt = Instant.now();
        account.updatedAt = account.createdAt;
        return account;
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id, currency, balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    /** Callers pass magnitudes; direction is chosen by which method they call. */
    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive, was: " + amount);
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public long getVersion() {
        return version;
    }
}
