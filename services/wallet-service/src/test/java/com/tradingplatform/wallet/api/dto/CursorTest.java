package com.tradingplatform.wallet.api.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void survivesEncodeDecodeRoundTrip() {
        Cursor original = new Cursor(Instant.parse("2026-08-01T10:15:30.123456Z"), UUID.randomUUID());

        Cursor decoded = Cursor.decode(original.encode());

        assertThat(decoded).isEqualTo(original);
    }

    /** Must be safe in a query string without escaping. */
    @Test
    void encodesToUrlSafeCharactersOnly() {
        String encoded = new Cursor(Instant.now(), UUID.randomUUID()).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    /** A tampered or truncated cursor must surface as a 400, never a 500. */
    @Test
    void rejectsMalformedCursors() {
        assertThatThrownBy(() -> Cursor.decode("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursor.decode("dGhpcy1oYXMtbm8tc2VwYXJhdG9y"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursor.decode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
