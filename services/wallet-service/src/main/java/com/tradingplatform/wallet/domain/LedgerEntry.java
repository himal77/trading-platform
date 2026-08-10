package com.tradingplatform.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only. Every column is {@code updatable = false}, so Hibernate can never emit an UPDATE
 * for this table: a posted entry is permanently immutable. Mistakes are corrected by posting a
 * REVERSAL entry, leaving both the error and the correction visible in history.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    /** Groups the entries that make up one logical movement of money. */
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** Negative = debit (money out), positive = credit (money in). */
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    /** The account's balance immediately after this entry, so a statement needs no recomputation. */
    @Column(name = "balance_after", nullable = false, updatable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private EntryType entryType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public static LedgerEntry post(UUID transactionId, Account account,
                                   BigDecimal signedAmount, EntryType entryType) {
        LedgerEntry entry = new LedgerEntry();
        entry.id = UUID.randomUUID();
        entry.transactionId = transactionId;
        entry.accountId = account.getId();
        entry.amount = signedAmount;
        entry.balanceAfter = account.getBalance();
        entry.entryType = entryType;
        entry.createdAt = Instant.now();
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public enum EntryType {
        DEPOSIT, WITHDRAWAL, TRADE_BUY, TRADE_SELL, FEE, REVERSAL
    }
}
