package dev.vikas.shortener.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} jobs. Guarded by a property so the purge job can
 * be switched off per environment (it is off in tests, which call it directly).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.expiry", name = "purge-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
