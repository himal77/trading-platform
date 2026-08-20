package com.tradingplatform.wallet.api.dto;

import com.tradingplatform.wallet.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String type,
        Instant createdAt
) {
    public static TransactionResponse from(LedgerEntry entry) {
        return new TransactionResponse(
                entry.getTransactionId(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getEntryType().name(),
                entry.getCreatedAt()
        );
    }
}
