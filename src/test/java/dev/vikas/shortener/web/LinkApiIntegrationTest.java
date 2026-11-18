package dev.vikas.shortener.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vikas.shortener.service.ExpiredLinkPurger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end tests through the full stack: filter, controllers, service,
 * SQL, Flyway-migrated in-memory H2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExpiredLinkPurger purger;

    @Test
    void createsAShortLinkAndRedirectsToTheOriginalUrl() throws Exception {
        String target = "https://example.com/landing?campaign=spring&x=1";

        MvcResult created = mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", target))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalUrl").value(target))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String code = body.get("code").asText();
        assertTrue(code.matches("[0-9A-Za-z]{7}"), "unexpected code " + code);
        assertEquals("http://localhost:8080/" + code, body.get("shortUrl").asText());
        assertEquals("/api/v1/links/" + code, created.getResponse().getHeader("Location"));

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", target))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void theSameUrlGetsADifferentCodeEachTime() throws Exception {
        String first = createLink("https://example.com/same");
        String second = createLink("https://example.com/same");

        assertNotEquals(first, second);
    }

    @Test
    void returnsLinkMetadata() throws Exception {
        String code = createLink("https://example.com/metadata");

        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/metadata"));
    }

    @Test
    void countsClicksAndReportsThemInStats() throws Exception {
        String code = createLink("https://example.com/tracked");

        mvc.perform(get("/api/v1/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.clicksByDay").isEmpty())
                .andExpect(jsonPath("$.lastAccessedAt").doesNotExist());

        mvc.perform(get("/" + code).header("Referer", "https://news.example.org/story/1"))
                .andExpect(status().isFound());
        mvc.perform(get("/" + code).header("Referer", "https://news.example.org/story/2"))
                .andExpect(status().isFound());
        mvc.perform(get("/" + code).header("Referer", "https://social.example.net/post"))
                .andExpect(status().isFound());
        mvc.perform(get("/" + code))
                .andExpect(status().isFound());

        mvc.perform(get("/api/v1/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.totalClicks").value(4))
                .andExpect(jsonPath("$.lastAccessedAt").isNotEmpty())
                .andExpect(jsonPath("$.windowDays").value(30))
                .andExpect(jsonPath("$.clicksByDay.length()").value(1))
                .andExpect(jsonPath("$.clicksByDay[0].clicks").value(4))
                .andExpect(jsonPath("$.topReferrers.length()").value(2))
                .andExpect(jsonPath("$.topReferrers[0].host").value("news.example.org"))
                .andExpect(jsonPath("$.topReferrers[0].clicks").value(2))
                .andExpect(jsonPath("$.topReferrers[1].host").value("social.example.net"));
    }

    @Test
    void clicksOnOneLinkDoNotAffectAnother() throws Exception {
        String clicked = createLink("https://example.com/clicked");
        String untouched = createLink("https://example.com/untouched");

        mvc.perform(get("/" + clicked)).andExpect(status().isFound());

        mvc.perform(get("/api/v1/links/" + untouched + "/stats"))
                .andExpect(jsonPath("$.totalClicks").value(0));
    }

    @Test
    void returns404ForAnUnknownCode() throws Exception {
        mvc.perform(get("/zzzzzzz9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Link not found"));
        mvc.perform(get("/api/v1/links/zzzzzzz9"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/links/zzzzzzz9/stats"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAMissingUrlWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").value("url is required"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDisallowedUrls() throws Exception {
        for (String url : new String[] {
                "javascript:alert(1)",
                "ftp://example.com/file",
                "http://127.0.0.1:8080/admin",
                "http://localhost:8080/loop",
                "https://user:pass@example.com/"}) {
            mvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("url", url))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid URL"));
        }
    }

    @Test
    void createsALinkWithACustomAlias() throws Exception {
        String alias = "spring-sale_2026";

        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://example.com/sale", "alias", alias))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(alias))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/" + alias))
                .andExpect(jsonPath("$.customAlias").value(true));

        mvc.perform(get("/" + alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/sale"));
    }

    @Test
    void generatedCodesAreNotFlaggedAsCustom() throws Exception {
        String code = createLink("https://example.com/generated");

        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(jsonPath("$.customAlias").value(false));
    }

    @Test
    void rejectsAnAliasThatIsAlreadyTaken() throws Exception {
        String body = json(Map.of("url", "https://example.com/first", "alias", "taken-alias"));

        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://example.com/second", "alias", "taken-alias"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Alias unavailable"));

        // The original link is untouched.
        mvc.perform(get("/taken-alias"))
                .andExpect(header().string("Location", "https://example.com/first"));
    }

    @Test
    void rejectsMalformedAndReservedAliases() throws Exception {
        for (String alias : new String[] {"abc", "has space", "bad/slash", "dots.not.ok", "actuator", "Swagger-UI"}) {
            mvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("url", "https://example.com/x", "alias", alias))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid alias"));
        }
    }

    @Test
    void treatsABlankAliasAsNoAlias() throws Exception {
        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://example.com/blank-alias", "alias", ""))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customAlias").value(false));
    }

    @Test
    void linksNeverExpireByDefault() throws Exception {
        String code = createLink("https://example.com/forever");

        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void aLinkWithATtlReportsItsExpiryAndRedirectsUntilThen() throws Exception {
        Instant before = Instant.now();

        MvcResult created = mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://example.com/temporary", "ttlSeconds", 3600))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        Instant expiresAt = Instant.parse(body.get("expiresAt").asText());
        long secondsFromNow = ChronoUnit.SECONDS.between(before, expiresAt);
        assertTrue(secondsFromNow >= 3599 && secondsFromNow <= 3601, "unexpected expiry " + expiresAt);

        mvc.perform(get("/" + body.get("code").asText()))
                .andExpect(status().isFound());
    }

    @Test
    void anExpiredLinkReturns410AndRecordsNoClick() throws Exception {
        String code = createLinkWithTtl("https://example.com/expired", 3600);
        setExpiry(code, Instant.now().minus(1, ChronoUnit.MINUTES));

        mvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Link expired"));

        // Metadata and analytics stay readable so the owner can see what happened.
        mvc.perform(get("/api/v1/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
        mvc.perform(get("/api/v1/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0));
    }

    @Test
    void anExpiredAliasCannotBeReusedDuringTheRetentionPeriod() throws Exception {
        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://example.com/a", "alias", "expiring-alias", "ttlSeconds", 60))))
                .andExpect(status().isCreated());
        setExpiry("expiring-alias", Instant.now().minus(1, ChronoUnit.DAYS));

        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", "https://example.com/b", "alias", "expiring-alias"))))
                .andExpect(status().isConflict());
    }

    @Test
    void thePurgeDeletesOnlyLinksExpiredBeyondTheRetentionPeriod() throws Exception {
        String longExpired = createLinkWithTtl("https://example.com/long-expired", 3600);
        String recentlyExpired = createLinkWithTtl("https://example.com/recently-expired", 3600);
        String active = createLinkWithTtl("https://example.com/active", 3600);
        String permanent = createLink("https://example.com/permanent");
        mvc.perform(get("/" + longExpired)).andExpect(status().isFound());
        setExpiry(longExpired, Instant.now().minus(31, ChronoUnit.DAYS));
        setExpiry(recentlyExpired, Instant.now().minus(29, ChronoUnit.DAYS));

        int purged = purger.purgeOnce();

        assertTrue(purged >= 1, "expected at least one purged link but got " + purged);
        mvc.perform(get("/api/v1/links/" + longExpired)).andExpect(status().isNotFound());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM click_events WHERE link_code = ?", Integer.class, longExpired));
        mvc.perform(get("/api/v1/links/" + recentlyExpired)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/links/" + active)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/links/" + permanent)).andExpect(status().isOk());
    }

    @Test
    void rejectsTtlValuesOutsideTheAllowedRange() throws Exception {
        for (long ttl : new long[] {0, 59, -5, 31_536_001L}) {
            mvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("url", "https://example.com/ttl", "ttlSeconds", ttl))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.ttlSeconds").exists());
        }
    }

    @Test
    void exposesAHealthEndpoint() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private String createLink(String url) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", url))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
    }

    private String createLinkWithTtl(String url, long ttlSeconds) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("url", url, "ttlSeconds", ttlSeconds))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asText();
    }

    private void setExpiry(String code, Instant expiresAt) {
        int updated = jdbcTemplate.update(
                "UPDATE links SET expires_at = ? WHERE code = ?", Timestamp.from(expiresAt), code);
        assertEquals(1, updated);
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
