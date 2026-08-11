package com.tradingplatform.wallet.api.dto;

import com.tradingplatform.wallet.domain.LedgerEntry.EntryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param transactionId supplied by the caller, not generated here — it is what makes a retry
 *                      recognisable rather than applied twice
 * @param amount        always a positive magnitude; direction comes from the endpoint called
 */
public record LedgerPostingRequest(

        @NotNull
        UUID transactionId,

        @NotBlank
        String currency,

        @NotNull
        @DecimalMin(value = "0.00000001", message = "Amount must be positive")
        BigDecimal amount,

        @NotNull
        EntryType entryType
) {
}
