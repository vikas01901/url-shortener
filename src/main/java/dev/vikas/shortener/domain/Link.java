package dev.vikas.shortener.domain;

import java.time.Instant;

/** A stored short link. {@code lastAccessedAt} is null until the first redirect. */
public record Link(
        String code,
        String originalUrl,
        Instant createdAt,
        long clickCount,
        Instant lastAccessedAt) {
}
