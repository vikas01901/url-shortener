package dev.vikas.shortener.service;

import dev.vikas.shortener.config.AppProperties;
import dev.vikas.shortener.persistence.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes links that expired longer ago than the retention period, together
 * with their click events (foreign key cascade).
 *
 * <p>Expired links are kept for the retention period so that visitors get an
 * accurate "410 Gone" instead of "404", owners can still read final analytics,
 * and a just-expired alias cannot be re-registered immediately by someone else.
 *
 * <p>Deletion runs in bounded batches so one run never holds a long lock. The
 * job is idempotent, so it is safe (if wasteful) for several instances to run it.
 */
@Component
public class ExpiredLinkPurger {

    private static final Logger log = LoggerFactory.getLogger(ExpiredLinkPurger.class);
    private static final int MAX_BATCHES_PER_RUN = 100;

    private final LinkRepository links;
    private final Clock clock;
    private final AppProperties props;

    public ExpiredLinkPurger(LinkRepository links, Clock clock, AppProperties props) {
        this.links = links;
        this.clock = clock;
        this.props = props;
    }

    /** Only fires when scheduling is enabled (see {@code SchedulingConfig}). */
    @Scheduled(
            initialDelayString = "${app.expiry.purge-initial-delay:PT1M}",
            fixedDelayString = "${app.expiry.purge-interval:PT1H}")
    public void purgeOnSchedule() {
        try {
            int purged = purgeOnce();
            if (purged > 0) {
                log.info("Purged {} expired link(s)", purged);
            }
        } catch (RuntimeException e) {
            // Never let one failed run kill the schedule; the next run retries.
            log.error("Expired link purge failed", e);
        }
    }

    /** @return the number of links deleted */
    public int purgeOnce() {
        Instant cutoff = clock.instant().minus(props.expiry().retention());
        int batchSize = props.expiry().purgeBatchSize();
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = links.deleteExpiredBefore(cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }
}
