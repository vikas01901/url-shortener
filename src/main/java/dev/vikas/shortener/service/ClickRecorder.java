package dev.vikas.shortener.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Hands click events to the analytics executor.
 *
 * <p>Design rule: a redirect must never fail or slow down because of analytics.
 * Clicks are written off the request thread, a full queue drops the click
 * rather than blocking, and a failed write is logged rather than rethrown. The
 * accepted cost is that click counts are eventually consistent and can
 * under-count during overload or if the process dies with clicks still queued.
 */
@Component
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);
    private static final long DROP_LOG_INTERVAL = 1000;

    private final ClickWriter writer;
    private final Executor executor;
    private final Clock clock;
    private final AtomicLong dropped = new AtomicLong();

    public ClickRecorder(ClickWriter writer, @Qualifier("clickExecutor") Executor executor, Clock clock) {
        this.writer = writer;
        this.executor = executor;
        this.clock = clock;
    }

    public void record(String code, String referrerHost) {
        // Capture the time now so a queued click keeps the moment it happened.
        Instant clickedAt = clock.instant();
        try {
            executor.execute(() -> write(code, clickedAt, referrerHost));
        } catch (RejectedExecutionException e) {
            long total = dropped.incrementAndGet();
            if (total == 1 || total % DROP_LOG_INTERVAL == 0) {
                log.warn("Click queue is full; {} click(s) dropped since startup", total);
            }
        }
    }

    /** Number of clicks dropped because the queue was full. */
    public long droppedCount() {
        return dropped.get();
    }

    private void write(String code, Instant clickedAt, String referrerHost) {
        try {
            writer.write(code, clickedAt, referrerHost);
        } catch (RuntimeException e) {
            log.error("Failed to record click for code {}", code, e);
        }
    }
}
