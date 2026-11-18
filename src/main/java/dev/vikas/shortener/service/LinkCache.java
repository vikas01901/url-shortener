package dev.vikas.shortener.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vikas.shortener.config.AppProperties;
import dev.vikas.shortener.domain.ResolvedLink;
import org.springframework.stereotype.Component;

/**
 * Bounded in-memory cache of code to destination for the redirect hot path.
 *
 * <p>Caching is safe because a link's destination never changes after creation.
 * Only successful lookups are cached; caching misses would hide a link created
 * moments later on another instance.
 */
@Component
public class LinkCache {

    private final Cache<String, ResolvedLink> cache;

    public LinkCache(AppProperties props) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(props.cache().maxEntries())
                .expireAfterWrite(props.cache().ttl())
                .build();
    }

    /** @return the cached link, or null if absent */
    public ResolvedLink get(String code) {
        return cache.getIfPresent(code);
    }

    public void put(ResolvedLink link) {
        cache.put(link.code(), link);
    }

    public void evict(String code) {
        cache.invalidate(code);
    }
}
