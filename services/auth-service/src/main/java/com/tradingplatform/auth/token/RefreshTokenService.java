package com.tradingplatform.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Refresh tokens are opaque random strings held in Redis, not JWTs.
 *
 * A JWT cannot be revoked before it expires, so a stolen refresh token would grant
 * access for its full lifetime. Keeping server-side state means logout is a DELETE
 * and takes effect immediately — the trade-off being a Redis lookup per refresh.
 */
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redis;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redis.opsForValue().set(key(token), userId.toString(), properties.refreshTokenTtl());
        return token;
    }

    /**
     * Consumes the token and issues a replacement, so a refresh token is single-use.
     * If a stolen token is replayed after the legitimate client has refreshed, the
     * lookup fails and the attacker is locked out.
     *
     * @throws InvalidTokenException if the token is unknown, expired, or already used
     */
    public Rotation rotate(String presentedToken) {
        String userId = redis.opsForValue().getAndDelete(key(presentedToken));
        if (userId == null) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }
        UUID id = UUID.fromString(userId);
        return new Rotation(id, issue(id));
    }

    public void revoke(String token) {
        redis.delete(key(token));
    }

    public long refreshTokenTtlSeconds() {
        return properties.refreshTokenTtl().toSeconds();
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }

    public record Rotation(UUID userId, String newRefreshToken) {
    }
}
