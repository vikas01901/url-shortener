package dev.vikas.shortener.web;

import dev.vikas.shortener.domain.DailyClicks;
import dev.vikas.shortener.domain.LinkStats;
import dev.vikas.shortener.domain.ReferrerClicks;
import java.time.Instant;
import java.util.List;

/**
 * Analytics for one link. {@code totalClicks} is all-time; {@code clicksByDay}
 * and {@code topReferrers} cover the trailing {@code windowDays} days in UTC.
 */
public record StatsResponse(
        String code,
        String originalUrl,
        Instant createdAt,
        long totalClicks,
        Instant lastAccessedAt,
        int windowDays,
        List<DailyClicks> clicksByDay,
        List<ReferrerClicks> topReferrers) {

    static StatsResponse from(LinkStats stats) {
        return new StatsResponse(
                stats.link().code(),
                stats.link().originalUrl(),
                stats.link().createdAt(),
                stats.link().clickCount(),
                stats.link().lastAccessedAt(),
                stats.windowDays(),
                stats.clicksByDay(),
                stats.topReferrers());
    }
}
