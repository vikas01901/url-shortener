package dev.vikas.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlPolicyTest {

    private final UrlPolicy policy = new UrlPolicy(2048, true, "sho.rt");

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "http://example.com/path?query=1&other=two#fragment",
            "HTTPS://Example.COM/Mixed-Case",
            "https://sub.domain.example.co.uk:8443/a/b",
            "https://8.8.8.8/dns",
            "https://[2001:4860:4860::8888]/"
    })
    void acceptsPublicHttpAndHttpsUrls(String url) {
        assertEquals(url, policy.validate(url));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("https://example.com/a", policy.validate("  https://example.com/a  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<h1>x</h1>",
            "file:///etc/passwd",
            "ftp://example.com/file",
            "example.com/no-scheme",
            "//example.com/scheme-relative"
    })
    void rejectsNonWebSchemes(String url) {
        assertThrows(InvalidUrlException.class, () -> policy.validate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "https://",
            "https:///path-only",
            "https://exa mple.com",
            "https://user:secret@example.com/"
    })
    void rejectsBlankMalformedAndCredentialedUrls(String url) {
        assertThrows(InvalidUrlException.class, () -> policy.validate(url));
    }

    @Test
    void rejectsNull() {
        assertThrows(InvalidUrlException.class, () -> policy.validate(null));
    }

    @Test
    void rejectsUrlsLongerThanTheLimit() {
        String longUrl = "https://example.com/" + "a".repeat(2048);

        assertThrows(InvalidUrlException.class, () -> policy.validate(longUrl));
    }

    @Test
    void rejectsUrlsPointingBackAtThisService() {
        assertThrows(InvalidUrlException.class, () -> policy.validate("https://sho.rt/abc1234"));
        assertThrows(InvalidUrlException.class, () -> policy.validate("https://SHO.RT/abc1234"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/admin",
            "http://app.localhost/",
            "http://printer.local/",
            "http://vault.internal/secrets",
            "http://127.0.0.1:8080/",
            "http://10.0.0.5/",
            "http://172.16.4.4/",
            "http://192.168.1.1/router",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0/",
            "http://[::1]/",
            "http://[fd00::1]/",
            "http://[fe80::1]/",
            "http://2130706433/",
            "http://0x7f.0.0.1/"
    })
    void rejectsPrivateLoopbackAndNumericHosts(String url) {
        assertThrows(InvalidUrlException.class, () -> policy.validate(url));
    }

    @Test
    void allowsPrivateHostsWhenTheBlockIsDisabled() {
        UrlPolicy permissive = new UrlPolicy(2048, false, null);

        assertDoesNotThrow(() -> permissive.validate("http://192.168.1.1/router"));
        assertDoesNotThrow(() -> permissive.validate("http://localhost:3000/"));
    }
}
