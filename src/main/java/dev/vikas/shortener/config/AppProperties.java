package dev.vikas.shortener.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed, immutable application settings bound from the {@code app.*} properties.
 * Every value has a default so the service starts with no configuration at all.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue Codes codes,
        @DefaultValue CacheSettings cache,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Analytics analytics,
        @DefaultValue UrlRules urlRules) {

    /** Short-code generation settings. */
    public record Codes(
            @DefaultValue("7") int length,
            @DefaultValue("5") int maxAttempts) {
    }

    /** In-memory cache for the redirect hot path. */
    public record CacheSettings(
            @DefaultValue("100000") long maxEntries,
            @DefaultValue("10m") Duration ttl) {
    }

    /** Per-client token bucket applied to link creation. */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("20") int capacity,
            @DefaultValue("60") int refillPerMinute) {
    }

    /** Click recording settings. */
    public record Analytics(
            @DefaultValue("true") boolean async,
            @DefaultValue("2") int workerThreads,
            @DefaultValue("10000") int queueCapacity) {
    }

    /** Rules applied to submitted destination URLs. */
    public record UrlRules(
            @DefaultValue("2048") int maxLength,
            @DefaultValue("true") boolean blockPrivateHosts) {
    }
}
