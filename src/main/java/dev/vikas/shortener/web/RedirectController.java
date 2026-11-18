package dev.vikas.shortener.web;

import dev.vikas.shortener.domain.LinkNotFoundException;
import dev.vikas.shortener.domain.Referrers;
import dev.vikas.shortener.service.LinkService;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** The public redirect endpoint: {@code GET /{code}}. */
@RestController
public class RedirectController {

    /** Matches the width of the {@code links.code} column. */
    private static final int MAX_CODE_LENGTH = 32;

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Redirects to the original URL.
     *
     * <p>Uses 302 with {@code Cache-Control: no-store} rather than 301. A 301 is
     * cached by browsers, so repeat visits would skip this service and never be
     * counted. The cost is one extra round trip per visit.
     *
     * <p>The path pattern only matches valid code characters, so requests such
     * as {@code /favicon.ico} never reach the database.
     */
    @GetMapping("/{code:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(
            @PathVariable("code") String code,
            @RequestHeader(value = HttpHeaders.REFERER, required = false) String referer) {
        if (code.length() > MAX_CODE_LENGTH) {
            throw new LinkNotFoundException(code.substring(0, MAX_CODE_LENGTH) + "...");
        }
        String target = linkService.resolve(code, Referrers.hostOf(referer));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
