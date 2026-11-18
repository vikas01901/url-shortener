package dev.vikas.shortener.domain;

import java.time.Instant;

/**
 * A stored short link.
 *
 * @param customAlias    true if the caller chose the code, false if it was generated
 * @param lastAccessedAt null until the first redirect
 */
public record Link(
        String code,
        String originalUrl,
        Instant createdAt,
        boolean customAlias,
        long clickCount,
        Instant lastAccessedAt) {
}
