package dev.vikas.shortener.domain;

import java.util.List;

/** Analytics for one link over a trailing window of days. */
public record LinkStats(
        Link link,
        int windowDays,
        List<DailyClicks> clicksByDay,
        List<ReferrerClicks> topReferrers) {
}
