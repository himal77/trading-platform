package com.tradingplatform.auth.api.dto;

/**
 * The refresh token is deliberately absent — it is delivered as an HttpOnly cookie
 * so JavaScript (and therefore XSS) cannot read it.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
