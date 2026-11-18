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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
