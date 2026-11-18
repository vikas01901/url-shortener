package dev.vikas.shortener.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/links}.
 *
 * @param alias optional caller-chosen code; when absent a random code is generated.
 *              Its format is checked by {@code AliasPolicy} so the rule lives in one place.
 */
public record CreateLinkRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,
        String alias) {
}
