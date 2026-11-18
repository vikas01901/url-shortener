package dev.vikas.shortener.web;

import dev.vikas.shortener.config.AppProperties;
import dev.vikas.shortener.domain.Link;
import dev.vikas.shortener.service.LinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Management API: create links and read their metadata and analytics. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final AppProperties props;

    public LinkController(LinkService linkService, AppProperties props) {
        this.linkService = linkService;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = linkService.create(request.url(), request.alias(), request.ttlSeconds());
        return ResponseEntity
                .created(URI.create("/api/v1/links/" + link.code()))
                .body(LinkResponse.from(link, props.baseUrl()));
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable("code") String code) {
        return LinkResponse.from(linkService.getLink(code), props.baseUrl());
    }

    @GetMapping("/{code}/stats")
    public StatsResponse stats(
            @PathVariable("code") String code,
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return StatsResponse.from(linkService.getStats(code, days));
    }
}
