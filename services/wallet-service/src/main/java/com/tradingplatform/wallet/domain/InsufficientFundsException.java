package com.tradingplatform.wallet.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, String currency,
                                      BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds in account %s: balance %s %s, requested %s %s"
                .formatted(accountId, balance, currency, requested, currency));
    }
}
