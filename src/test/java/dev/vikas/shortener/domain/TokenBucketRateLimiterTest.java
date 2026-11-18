package dev.vikas.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    private static final long ONE_SECOND = 1_000_000_000L;

    /** Fake monotonic clock so the tests never sleep. */
    private final AtomicLong nanos = new AtomicLong();

    private TokenBucketRateLimiter limiter(int capacity, double refillPerSecond) {
        return new TokenBucketRateLimiter(new ConcurrentHashMap<>(), capacity, refillPerSecond, nanos::get);
    }

    @Test
    void allowsABurstUpToCapacityThenRejects() {
        TokenBucketRateLimiter limiter = limiter(3, 1.0);

        assertTrue(limiter.tryAcquire("client").allowed());
        assertTrue(limiter.tryAcquire("client").allowed());
        assertTrue(limiter.tryAcquire("client").allowed());

        TokenBucketRateLimiter.Decision rejected = limiter.tryAcquire("client");
        assertFalse(rejected.allowed());
        assertEquals(1, rejected.retryAfterSeconds());
    }

    @Test
    void refillsOverTime() {
        TokenBucketRateLimiter limiter = limiter(2, 1.0);
        limiter.tryAcquire("client");
        limiter.tryAcquire("client");
        assertFalse(limiter.tryAcquire("client").allowed());

        nanos.addAndGet(ONE_SECOND);

        assertTrue(limiter.tryAcquire("client").allowed());
        assertFalse(limiter.tryAcquire("client").allowed());
    }

    @Test
    void neverAccumulatesMoreThanCapacity() {
        TokenBucketRateLimiter limiter = limiter(2, 1.0);

        nanos.addAndGet(3600 * ONE_SECOND);

        assertTrue(limiter.tryAcquire("client").allowed());
        assertTrue(limiter.tryAcquire("client").allowed());
        assertFalse(limiter.tryAcquire("client").allowed());
    }

    @Test
    void tracksEachClientIndependently() {
        TokenBucketRateLimiter limiter = limiter(1, 1.0);

        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());
        assertTrue(limiter.tryAcquire("b").allowed());
    }

    @Test
    void reportsHowLongToWaitAtSlowRefillRates() {
        // One token per minute.
        TokenBucketRateLimiter limiter = limiter(1, 1.0 / 60);
        limiter.tryAcquire("client");

        assertEquals(60, limiter.tryAcquire("client").retryAfterSeconds());

        nanos.addAndGet(45 * ONE_SECOND);

        assertEquals(15, limiter.tryAcquire("client").retryAfterSeconds());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> limiter(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> limiter(1, 0.0));
    }
}
