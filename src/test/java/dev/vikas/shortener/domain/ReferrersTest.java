package dev.vikas.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ReferrersTest {

    @Test
    void extractsTheLowerCasedHostAndDropsEverythingElse() {
        assertEquals("news.example.com", Referrers.hostOf("https://News.Example.com/article/42?utm=x"));
    }

    @Test
    void returnsNullForMissingOrUnparseableHeaders() {
        assertNull(Referrers.hostOf(null));
        assertNull(Referrers.hostOf("  "));
        assertNull(Referrers.hostOf("not a url"));
        assertNull(Referrers.hostOf("/relative/path"));
    }

    @Test
    void returnsNullWhenTheHostIsTooLongToStore() {
        String longHost = "a".repeat(60) + "." + "b".repeat(60) + "." + "c".repeat(60) + "."
                + "d".repeat(60) + "." + "e".repeat(60) + ".com";

        assertNull(Referrers.hostOf("https://" + longHost + "/"));
    }
}
