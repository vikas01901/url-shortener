package dev.vikas.shortener.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/links}.
 *
 * @param alias optional caller-chosen code; when absent a random code is generated.
 *              Its format is checked by {@code AliasPolicy} so the rule lives in one place.
 * @param ttlSeconds optional lifetime in seconds, from one minute to one year. When absent
 *              the server default applies (no expiry unless configured). A relative TTL is
 *              used instead of an absolute timestamp so client clock skew cannot matter.
 */
public record CreateLinkRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,
        String alias,
        @Min(value = 60, message = "ttlSeconds must be at least 60")
        @Max(value = 31_536_000, message = "ttlSeconds must be at most 31536000 (365 days)")
        Long ttlSeconds) {
}
