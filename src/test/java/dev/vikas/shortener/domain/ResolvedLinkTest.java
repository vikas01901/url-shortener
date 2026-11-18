package dev.vikas.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ResolvedLinkTest {

    private static final Instant EXPIRY = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void aLinkWithoutAnExpiryNeverExpires() {
        ResolvedLink link = new ResolvedLink("abc1234", "https://example.com", null);

        assertFalse(link.isExpiredAt(Instant.MAX));
    }

    @Test
    void aLinkIsValidUntilItsExpiryInstantAndExpiredFromThenOn() {
        ResolvedLink link = new ResolvedLink("abc1234", "https://example.com", EXPIRY);

        assertFalse(link.isExpiredAt(EXPIRY.minusMillis(1)));
        assertTrue(link.isExpiredAt(EXPIRY));
        assertTrue(link.isExpiredAt(EXPIRY.plusSeconds(1)));
    }
}
