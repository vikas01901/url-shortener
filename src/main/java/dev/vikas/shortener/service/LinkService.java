package dev.vikas.shortener.service;

import dev.vikas.shortener.config.AppProperties;
import dev.vikas.shortener.domain.CodeGenerationException;
import dev.vikas.shortener.domain.CodeGenerator;
import dev.vikas.shortener.domain.DailyClicks;
import dev.vikas.shortener.domain.Link;
import dev.vikas.shortener.domain.LinkNotFoundException;
import dev.vikas.shortener.domain.LinkStats;
import dev.vikas.shortener.domain.ReferrerClicks;
import dev.vikas.shortener.domain.ResolvedLink;
import dev.vikas.shortener.domain.UrlPolicy;
import dev.vikas.shortener.persistence.ClickRepository;
import dev.vikas.shortener.persistence.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Use cases of the shortener: create a link, resolve a code, read analytics. */
@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);
    private static final int TOP_REFERRER_LIMIT = 5;
    private static final int MAX_STATS_WINDOW_DAYS = 365;

    private final LinkRepository links;
    private final ClickRepository clicks;
    private final LinkCache cache;
    private final CodeGenerator codeGenerator;
    private final UrlPolicy urlPolicy;
    private final ClickRecorder clickRecorder;
    private final Clock clock;
    private final AppProperties props;

    public LinkService(
            LinkRepository links,
            ClickRepository clicks,
            LinkCache cache,
            CodeGenerator codeGenerator,
            UrlPolicy urlPolicy,
            ClickRecorder clickRecorder,
            Clock clock,
            AppProperties props) {
        this.links = links;
        this.clicks = clicks;
        this.cache = cache;
        this.codeGenerator = codeGenerator;
        this.urlPolicy = urlPolicy;
        this.clickRecorder = clickRecorder;
        this.clock = clock;
        this.props = props;
    }

    /**
     * Creates a short link for the URL. Submitting the same URL twice yields two
     * independent links, so each caller gets separate analytics.
     *
     * @throws dev.vikas.shortener.domain.InvalidUrlException if the URL is not allowed
     * @throws CodeGenerationException if no free code is found within the retry budget
     */
    public Link create(String rawUrl) {
        String url = urlPolicy.validate(rawUrl);
        // Millisecond precision so the value returned now equals what the database returns later.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        int maxAttempts = props.codes().maxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Link link = new Link(codeGenerator.generate(), url, now, 0L, null);
            try {
                links.insert(link);
                return link;
            } catch (DuplicateKeyException e) {
                // The primary key is the single source of truth for uniqueness, so
                // this is safe across instances with no check-then-insert race.
                log.warn("Short code collision on attempt {} of {}", attempt, maxAttempts);
            }
        }
        throw new CodeGenerationException("Could not allocate a unique short code after " + maxAttempts + " attempts");
    }

    /**
     * Returns the destination for a code and records the click.
     *
     * @param referrerHost host of the referring page, or null
     * @throws LinkNotFoundException if the code is unknown
     */
    public String resolve(String code, String referrerHost) {
        ResolvedLink resolved = cache.get(code);
        if (resolved == null) {
            resolved = links.findByCode(code)
                    .map(link -> new ResolvedLink(link.code(), link.originalUrl()))
                    .orElseThrow(() -> new LinkNotFoundException(code));
            cache.put(resolved);
        }
        clickRecorder.record(code, referrerHost);
        return resolved.targetUrl();
    }

    public Link getLink(String code) {
        return links.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }

    /**
     * Returns click analytics for the trailing {@code days} days (UTC), clamped to 1..365.
     */
    public LinkStats getStats(String code, int days) {
        Link link = getLink(code);
        int windowDays = Math.max(1, Math.min(days, MAX_STATS_WINDOW_DAYS));
        LocalDate since = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(windowDays - 1L);
        List<DailyClicks> byDay = clicks.dailyClicks(code, since);
        List<ReferrerClicks> referrers = clicks.topReferrers(code, since, TOP_REFERRER_LIMIT);
        return new LinkStats(link, windowDays, byDay, referrers);
    }
}
