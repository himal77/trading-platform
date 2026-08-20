package com.tradingplatform.trade.wallet;

/** A definite rejection: nothing was applied, so there is nothing to compensate. */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String detail) {
        super(detail);
    }
}
