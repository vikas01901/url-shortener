package dev.vikas.shortener.domain;

import java.time.LocalDate;

/** Number of clicks on one UTC calendar day. */
public record DailyClicks(LocalDate day, long clicks) {
}
