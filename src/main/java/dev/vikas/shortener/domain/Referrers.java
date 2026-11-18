package dev.vikas.shortener.domain;

import java.net.URI;
import java.util.Locale;

/** Reduces a Referer header to its host, which is all the analytics keep. */
public final class Referrers {

    private static final int MAX_HOST_LENGTH = 255;

    private Referrers() {
    }

    /**
     * @return the lower-cased host of the referring page, or null when the header
     *         is absent, malformed, or too long to store
     */
    public static String hostOf(String refererHeader) {
        if (refererHeader == null || refererHeader.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(refererHeader.trim()).getHost();
            if (host == null || host.isBlank() || host.length() > MAX_HOST_LENGTH) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
