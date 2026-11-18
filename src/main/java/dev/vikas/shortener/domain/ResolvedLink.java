package dev.vikas.shortener.domain;

/** The minimal data the redirect path needs. This is what the cache holds. */
public record ResolvedLink(String code, String targetUrl) {
}
