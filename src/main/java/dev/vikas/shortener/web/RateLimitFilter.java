package dev.vikas.shortener.web;

import dev.vikas.shortener.config.AppProperties;
import dev.vikas.shortener.domain.TokenBucketRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limits link creation per client address. Redirects are deliberately not
 * limited: they are the product, they are cheap (cached primary-key lookup),
 * and creation is the endpoint that can be abused to fill the database.
 *
 * <p>The client is identified by the connection's remote address. Behind a load
 * balancer that is the balancer's address unless the platform is configured to
 * trust forwarded headers ({@code server.forward-headers-strategy}). The filter
 * does not read {@code X-Forwarded-For} itself because any client can forge it.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String CREATE_PATH = "/api/v1/links";
    private static final int TOO_MANY_REQUESTS = 429;

    private final TokenBucketRateLimiter rateLimiter;
    private final boolean enabled;

    public RateLimitFilter(TokenBucketRateLimiter rateLimiter, AppProperties props) {
        this.rateLimiter = rateLimiter;
        this.enabled = props.rateLimit().enabled();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !CREATE_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TokenBucketRateLimiter.Decision decision = rateLimiter.tryAcquire(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(TOO_MANY_REQUESTS);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429,"
                        + "\"detail\":\"Rate limit exceeded. Retry after "
                        + decision.retryAfterSeconds() + " second(s).\"}");
    }
}
