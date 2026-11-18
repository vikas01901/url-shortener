package dev.vikas.shortener.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies the creation rate limit with a small bucket and a negligible refill rate. */
@SpringBootTest(properties = {
        "app.rate-limit.capacity=3",
        "app.rate-limit.refill-per-minute=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    private static final String BODY = "{\"url\":\"https://example.com/rate-limited\"}";

    @Autowired
    private MockMvc mvc;

    @Test
    void limitsLinkCreationPerClientButNotRedirectsOrReads() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated());
        }

        mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // A different client address has its own bucket.
        mvc.perform(post("/api/v1/links")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());

        // Reads are never rate limited.
        mvc.perform(get("/api/v1/links/zzzzzzz9")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
