package com.tradingplatform.auth.service;

public class AccountSuspendedException extends RuntimeException {

    public AccountSuspendedException() {
        super("Account is suspended");
    }
}
