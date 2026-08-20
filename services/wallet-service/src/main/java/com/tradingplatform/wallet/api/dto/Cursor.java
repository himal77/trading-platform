package com.tradingplatform.wallet.api.dto;

import com.tradingplatform.wallet.domain.LedgerEntry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * A page cursor, exposed to clients as a single opaque base64 string.
 *
 * <p>Clients echo it back without interpreting it, so the encoding can change without breaking
 * them. Base64 is used for opacity only — there is nothing secret here, and it is not encryption.
 */
public record Cursor(Instant createdAt, UUID id) {

    private static final String SEPARATOR = "|";

    public static Cursor of(LedgerEntry entry) {
        return new Cursor(entry.getCreatedAt(), entry.getId());
    }

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws IllegalArgumentException if the cursor is malformed, which surfaces as a 400 rather
     *                                 than a 500
     */
    public static Cursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\" + SEPARATOR, 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed cursor");
        }
    }
}
