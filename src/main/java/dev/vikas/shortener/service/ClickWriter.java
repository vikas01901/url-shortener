package dev.vikas.shortener.service;

import dev.vikas.shortener.persistence.ClickRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one click in a single transaction. This is a separate bean from
 * {@link ClickRecorder} so the {@code @Transactional} proxy is actually applied
 * (a call from within the same class would bypass it).
 */
@Component
public class ClickWriter {

    private final ClickRepository clicks;

    public ClickWriter(ClickRepository clicks) {
        this.clicks = clicks;
    }

    @Transactional
    public void write(String code, Instant clickedAt, String referrerHost) {
        clicks.recordClick(code, clickedAt, referrerHost);
    }
}
