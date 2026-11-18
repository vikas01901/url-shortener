package dev.vikas.shortener.persistence;

import dev.vikas.shortener.domain.Link;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL access to the {@code links} table.
 *
 * <p>Plain SQL is used instead of an ORM: the redirect path is one primary-key
 * lookup and every statement the service runs is visible here.
 */
@Repository
public class LinkRepository {

    private static final String SELECT_COLUMNS =
            "SELECT code, original_url, created_at, click_count, last_accessed_at FROM links";

    private static final RowMapper<Link> LINK_MAPPER = (rs, rowNum) -> new Link(
            rs.getString("code"),
            rs.getString("original_url"),
            toInstant(rs.getTimestamp("created_at")),
            rs.getLong("click_count"),
            toInstant(rs.getTimestamp("last_accessed_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public LinkRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a new link.
     *
     * @throws org.springframework.dao.DuplicateKeyException if the code is already taken
     */
    public void insert(Link link) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", link.code())
                .addValue("url", link.originalUrl())
                .addValue("createdAt", Timestamp.from(link.createdAt()), Types.TIMESTAMP);
        jdbc.update(
                "INSERT INTO links (code, original_url, created_at, click_count) "
                        + "VALUES (:code, :url, :createdAt, 0)",
                params);
    }

    public Optional<Link> findByCode(String code) {
        List<Link> rows = jdbc.query(SELECT_COLUMNS + " WHERE code = :code", Map.of("code", code), LINK_MAPPER);
        return rows.stream().findFirst();
    }

    static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
