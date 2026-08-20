package com.tradingplatform.wallet.api.dto;

import com.tradingplatform.wallet.domain.Account;

import java.math.BigDecimal;

public record BalanceResponse(
        String currency,
        BigDecimal balance
) {
    public static BalanceResponse from(Account account) {
        return new BalanceResponse(account.getCurrency(), account.getBalance());
    }
}
