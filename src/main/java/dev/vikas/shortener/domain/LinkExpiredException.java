package dev.vikas.shortener.domain;

/** The link exists but its expiry time has passed. */
public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String code) {
        super("Link '" + code + "' has expired");
    }
}
