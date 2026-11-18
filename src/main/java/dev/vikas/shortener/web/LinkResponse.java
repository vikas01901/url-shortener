package dev.vikas.shortener.web;

import dev.vikas.shortener.domain.Link;
import java.time.Instant;

/** A short link as returned by the API. */
public record LinkResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt) {

    static LinkResponse from(Link link, String baseUrl) {
        return new LinkResponse(
                link.code(),
                shortUrl(baseUrl, link.code()),
                link.originalUrl(),
                link.createdAt());
    }

    static String shortUrl(String baseUrl, String code) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + code;
    }
}
