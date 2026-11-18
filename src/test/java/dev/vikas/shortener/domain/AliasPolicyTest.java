package dev.vikas.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AliasPolicyTest {

    private final AliasPolicy policy = new AliasPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "Spring-Sale_2026", "a1b2c3d4", "____", "12345678901234567890123456789012"})
    void acceptsWellFormedAliases(String alias) {
        assertEquals(alias, policy.validate(alias));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "123456789012345678901234567890123", "has space", "bad/slash",
            "dots.not.ok", "emoji-😀", "percent%20", " padded "})
    void rejectsMalformedAliases(String alias) {
        assertThrows(InvalidAliasException.class, () -> policy.validate(alias));
    }

    @Test
    void rejectsNull() {
        assertThrows(InvalidAliasException.class, () -> policy.validate(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"actuator", "ACTUATOR", "Swagger-UI", "admin", "health"})
    void rejectsReservedWordsRegardlessOfCase(String alias) {
        assertThrows(InvalidAliasException.class, () -> policy.validate(alias));
    }

    @Test
    void supportsACustomReservedList() {
        AliasPolicy custom = new AliasPolicy(Set.of("Promo"));

        assertThrows(InvalidAliasException.class, () -> custom.validate("promo"));
        assertEquals("actuator", custom.validate("actuator"));
    }
}
