package dev.vikas.shortener.domain;

/** Number of clicks that arrived from one referring host. */
public record ReferrerClicks(String host, long clicks) {
}
