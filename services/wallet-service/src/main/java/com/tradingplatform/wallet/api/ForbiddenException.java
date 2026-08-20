package com.tradingplatform.wallet.api;

/**
 * Deliberately says nothing about whether the requested resource exists — that would let a caller
 * probe for other users' accounts.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Access denied");
    }
}
