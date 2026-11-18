package dev.vikas.shortener.domain;

import java.time.Instant;

/**
 * The minimal data the redirect path needs. This is what the cache holds.
 *
 * @param expiresAt when the link stops redirecting, or null if it never expires
 */
public record ResolvedLink(String code, String targetUrl, Instant expiresAt) {

    /**
     * A link is expired from its expiry instant onward. Because the expiry time
     * travels with the cached entry and is compared on every redirect, a cached
     * link stops working on time without any cache invalidation.
     */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
