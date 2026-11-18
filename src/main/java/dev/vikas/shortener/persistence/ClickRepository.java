package dev.vikas.shortener.persistence;

import dev.vikas.shortener.domain.DailyClicks;
import dev.vikas.shortener.domain.ReferrerClicks;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL access for click recording and the analytics read queries. */
@Repository
public class ClickRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ClickRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records one click: bumps the link's counter and appends an event row.
     * The counter is incremented in SQL ({@code click_count = click_count + 1})
     * so concurrent clicks cannot lose updates. Callers run this inside a
     * transaction so the counter and the event log stay consistent.
     *
     * @return false if the link no longer exists, in which case nothing is written
     */
    public boolean recordClick(String code, Instant clickedAt, String referrerHost) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("clickedAt", Timestamp.from(clickedAt), Types.TIMESTAMP)
                .addValue("clickDay", Date.valueOf(LocalDate.ofInstant(clickedAt, ZoneOffset.UTC)), Types.DATE)
                .addValue("referrerHost", referrerHost, Types.VARCHAR);
        int updated = jdbc.update(
                "UPDATE links SET click_count = click_count + 1, last_accessed_at = :clickedAt "
                        + "WHERE code = :code",
                params);
        if (updated == 0) {
            return false;
        }
        jdbc.update(
                "INSERT INTO click_events (link_code, clicked_at, click_day, referrer_host) "
                        + "VALUES (:code, :clickedAt, :clickDay, :referrerHost)",
                params);
        return true;
    }

    /** Clicks per UTC day, newest first, for days on or after {@code since}. */
    public List<DailyClicks> dailyClicks(String code, LocalDate since) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("since", Date.valueOf(since), Types.DATE);
        return jdbc.query(
                "SELECT click_day, COUNT(*) AS clicks FROM click_events "
                        + "WHERE link_code = :code AND click_day >= :since "
                        + "GROUP BY click_day ORDER BY click_day DESC",
                params,
                (rs, rowNum) -> new DailyClicks(rs.getDate("click_day").toLocalDate(), rs.getLong("clicks")));
    }

    /** The referring hosts that sent the most clicks since {@code since}, busiest first. */
    public List<ReferrerClicks> topReferrers(String code, LocalDate since, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("since", Date.valueOf(since), Types.DATE)
                .addValue("limit", limit, Types.INTEGER);
        return jdbc.query(
                "SELECT referrer_host, COUNT(*) AS clicks FROM click_events "
                        + "WHERE link_code = :code AND click_day >= :since AND referrer_host IS NOT NULL "
                        + "GROUP BY referrer_host ORDER BY clicks DESC, referrer_host ASC LIMIT :limit",
                params,
                (rs, rowNum) -> new ReferrerClicks(rs.getString("referrer_host"), rs.getLong("clicks")));
    }
}
