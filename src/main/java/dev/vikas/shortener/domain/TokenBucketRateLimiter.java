package dev.vikas.shortener.domain;

import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Token bucket rate limiter keyed by client.
 *
 * <p>Each key gets a bucket of {@code capacity} tokens that refills continuously
 * at {@code refillPerSecond}. A request spends one token; with none left it is
 * rejected with the number of seconds until the next token. This allows short
 * bursts up to {@code capacity} while holding the long-run rate at the refill rate.
 *
 * <p>State is in-memory and per instance. Behind several instances the effective
 * limit is multiplied by the instance count; a shared store such as Redis would
 * be needed for a global limit.
 */
public final class TokenBucketRateLimiter {

    /** Outcome of one attempt. {@code retryAfterSeconds} is 0 when allowed. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /** Mutable state for one client. Guarded by its own monitor. */
    public static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        Bucket(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }

    private final ConcurrentMap<String, Bucket> buckets;
    private final int capacity;
    private final double refillPerSecond;
    private final LongSupplier nanoClock;

    /**
     * @param buckets         backing map; the caller controls its size and eviction
     * @param capacity        maximum burst size, at least 1
     * @param refillPerSecond tokens added per second, greater than 0
     * @param nanoClock       monotonic time source, normally {@code System::nanoTime}
     */
    public TokenBucketRateLimiter(
            ConcurrentMap<String, Bucket> buckets, int capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be greater than 0");
        }
        this.buckets = buckets;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.nanoClock = nanoClock;
    }

    public Decision tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (bucket) {
            long elapsedNanos = Math.max(0L, now - bucket.lastRefillNanos);
            bucket.tokens = Math.min(capacity, bucket.tokens + (elapsedNanos / 1_000_000_000.0) * refillPerSecond);
            bucket.lastRefillNanos = Math.max(bucket.lastRefillNanos, now);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return new Decision(true, 0L);
            }
            long retryAfter = (long) Math.ceil((1.0 - bucket.tokens) / refillPerSecond);
            return new Decision(false, Math.max(1L, retryAfter));
        }
    }
}
