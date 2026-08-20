package com.tradingplatform.auth.api;

import com.tradingplatform.auth.api.dto.LoginRequest;
import com.tradingplatform.auth.api.dto.RegisterRequest;
import com.tradingplatform.auth.api.dto.TokenResponse;
import com.tradingplatform.auth.api.dto.UserResponse;
import com.tradingplatform.auth.service.AuthService;
import com.tradingplatform.auth.token.InvalidTokenException;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.TokenPair tokens = authService.register(request);
        return respondWithTokens(tokens, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.TokenPair tokens = authService.login(request);
        return respondWithTokens(tokens, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {

        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh token cookie is missing");
        }
        return respondWithTokens(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {

        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    /**
     * The gateway validates the JWT and forwards the caller identity as X-User-Id,
     * so this service never parses the token itself.
     */
    @GetMapping("/me")
    public UserResponse me(@RequestHeader("X-User-Id") UUID userId) {
        return UserResponse.from(authService.findById(userId));
    }

    private ResponseEntity<TokenResponse> respondWithTokens(AuthService.TokenPair tokens, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
                .body(TokenResponse.bearer(tokens.accessToken(), tokens.accessTokenTtlSeconds()));
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)          // JavaScript cannot read it, so XSS cannot steal it
                .secure(true)
                .sameSite("Strict")      // not sent on cross-site requests, mitigating CSRF
                .path("/auth")           // only sent to auth endpoints
                .maxAge(Duration.ofDays(7))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
