package com.tradingplatform.auth.service;

/**
 * Deliberately generic: never reveal whether the email exists or the password was wrong.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
