package dev.vikas.shortener.domain;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides which destination URLs may be shortened.
 *
 * <p>The service never fetches the destination, so this is not SSRF protection
 * for the server. It exists to stop the shortener being used to disguise links
 * to things that are never legitimate public destinations: non-web schemes
 * (javascript:, data:, file:), URLs with embedded credentials, redirect loops
 * back to this service, and addresses inside a private network.
 *
 * <p>Host checks are textual only. No DNS lookup is made, so a public name that
 * resolves to a private address is not detected (see the limitations in the docs).
 */
public final class UrlPolicy {

    private static final Pattern DOTTED_QUAD = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /** Hosts made only of decimal or hex numbers, which browsers read as IP addresses. */
    private static final Pattern NUMERIC_HOST =
            Pattern.compile("^(0x[0-9a-f]+|\\d+)(\\.(0x[0-9a-f]+|\\d+))*$");

    private final int maxLength;
    private final boolean blockPrivateHosts;
    private final String ownHost;

    /**
     * @param maxLength         maximum accepted URL length in characters
     * @param blockPrivateHosts reject loopback, private-range and internal-only hosts
     * @param ownHost           this service's public host, or null to skip the loop check
     */
    public UrlPolicy(int maxLength, boolean blockPrivateHosts, String ownHost) {
        this.maxLength = maxLength;
        this.blockPrivateHosts = blockPrivateHosts;
        this.ownHost = ownHost == null ? null : ownHost.toLowerCase(Locale.ROOT);
    }

    /**
     * Validates a submitted URL.
     *
     * @return the trimmed URL to store
     * @throws InvalidUrlException if the URL breaks any rule; the message is safe to show to the client
     */
    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidUrlException("url must not be blank");
        }
        String candidate = raw.trim();
        if (candidate.length() > maxLength) {
            throw new InvalidUrlException("url must be at most " + maxLength + " characters");
        }
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a valid URI");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("only http and https URLs are supported");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("url must include a valid host");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidUrlException("urls with embedded credentials are not allowed");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (ownHost != null && !ownHost.isBlank() && normalizedHost.equals(ownHost)) {
            throw new InvalidUrlException("url must not point at this service");
        }
        if (blockPrivateHosts && isPrivateHost(normalizedHost)) {
            throw new InvalidUrlException("url must not point at a private or loopback address");
        }
        return candidate;
    }

    static boolean isPrivateHost(String host) {
        if (host.equals("localhost")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")) {
            return true;
        }
        String literal = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        boolean dottedQuad = DOTTED_QUAD.matcher(literal).matches();
        boolean ipv6 = literal.contains(":");
        if (!dottedQuad && !ipv6) {
            // "2130706433" and "0x7f.1" are 127.0.0.1 to a browser. No public
            // site uses such a host, so reject every numeric-only form.
            return NUMERIC_HOST.matcher(literal).matches();
        }
        try {
            // The argument is always an IP literal here, so this never does a DNS lookup.
            InetAddress address = InetAddress.getByName(literal);
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()
                    || isUniqueLocalIpv6(address);
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /** fc00::/7, the IPv6 equivalent of the private IPv4 ranges. */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
