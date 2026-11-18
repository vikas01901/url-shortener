package dev.vikas.shortener.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vikas.shortener.domain.AliasPolicy;
import dev.vikas.shortener.domain.CodeGenerator;
import dev.vikas.shortener.domain.RandomCodeGenerator;
import dev.vikas.shortener.domain.TokenBucketRateLimiter;
import dev.vikas.shortener.domain.UrlPolicy;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free domain classes into the Spring context. Keeping the
 * domain classes free of Spring annotations lets them be unit tested without a
 * container.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class BeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CodeGenerator codeGenerator(AppProperties props) {
        return new RandomCodeGenerator(new SecureRandom(), props.codes().length());
    }

    @Bean
    public UrlPolicy urlPolicy(AppProperties props) {
        String ownHost = URI.create(props.baseUrl()).getHost();
        return new UrlPolicy(props.urlRules().maxLength(), props.urlRules().blockPrivateHosts(), ownHost);
    }

    @Bean
    public AliasPolicy aliasPolicy() {
        return new AliasPolicy();
    }

    @Bean
    public TokenBucketRateLimiter rateLimiter(AppProperties props) {
        // Bounded and self-expiring so a flood of distinct client addresses
        // cannot grow the bucket map without limit.
        Cache<String, TokenBucketRateLimiter.Bucket> buckets = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofMinutes(30))
                .build();
        double refillPerSecond = props.rateLimit().refillPerMinute() / 60.0;
        return new TokenBucketRateLimiter(
                buckets.asMap(), props.rateLimit().capacity(), refillPerSecond, System::nanoTime);
    }

    /**
     * Executor for click recording. Async mode uses a small pool with a bounded
     * queue: when the queue is full new clicks are rejected (and counted as
     * dropped by the recorder) instead of slowing redirects or exhausting memory.
     * Sync mode runs on the request thread and is used by the tests.
     */
    @Bean(name = "clickExecutor")
    public Executor clickExecutor(AppProperties props) {
        AppProperties.Analytics analytics = props.analytics();
        if (!analytics.async()) {
            return Runnable::run;
        }
        AtomicInteger threadNumber = new AtomicInteger();
        return new ThreadPoolExecutor(
                analytics.workerThreads(),
                analytics.workerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(analytics.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "click-recorder-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
