package com.tradingplatform.auth.token;

import com.tradingplatform.auth.domain.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-algorithm-ok";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7)));

    @Test
    void generatesTokenCarryingUserClaims() {
        User user = User.create("trader@example.com", "irrelevant-hash");

        Claims claims = jwtService.parse(jwtService.generateAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email")).isEqualTo("trader@example.com");
        assertThat(claims.get("tier")).isEqualTo("FREE");
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService attacker = new JwtService(
                new JwtProperties("a-completely-different-secret-also-long-enough-yes", Duration.ofMinutes(15), Duration.ofDays(7)));
        String forged = attacker.generateAccessToken(User.create("attacker@example.com", "hash"));

        assertThatThrownBy(() -> jwtService.parse(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(User.create("trader@example.com", "hash"));
        String tampered = token.substring(0, token.lastIndexOf('.')) + ".tampered-signature";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService shortLived = new JwtService(
                new JwtProperties(SECRET, Duration.ofSeconds(-1), Duration.ofDays(7)));
        String expired = shortLived.generateAccessToken(User.create("trader@example.com", "hash"));

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(InvalidTokenException.class);
    }
}
