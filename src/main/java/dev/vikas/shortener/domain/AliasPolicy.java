package dev.vikas.shortener.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rules for caller-chosen short codes.
 *
 * <p>Aliases share one namespace with generated codes (the {@code links.code}
 * primary key), so the format is limited to characters the redirect route
 * accepts, and words that are or may become application paths are reserved.
 * Aliases are case-sensitive, like generated codes.
 */
public final class AliasPolicy {

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{4,32}$");

    private static final Set<String> DEFAULT_RESERVED = Set.of(
            "actuator", "admin", "api-docs", "assets", "error", "favicon",
            "health", "links", "login", "logout", "static", "stats", "swagger", "swagger-ui");

    private final Set<String> reserved;

    public AliasPolicy() {
        this(DEFAULT_RESERVED);
    }

    public AliasPolicy(Set<String> reserved) {
        this.reserved = reserved.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return the alias, unchanged
     * @throws InvalidAliasException if the alias has a bad format or is reserved;
     *         the message is safe to show to the client
     */
    public String validate(String alias) {
        if (alias == null || !FORMAT.matcher(alias).matches()) {
            throw new InvalidAliasException(
                    "alias must be 4 to 32 characters and contain only letters, digits, '-' or '_'");
        }
        if (reserved.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new InvalidAliasException("alias '" + alias + "' is reserved");
        }
        return alias;
    }
}
