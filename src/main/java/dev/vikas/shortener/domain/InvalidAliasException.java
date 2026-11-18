package dev.vikas.shortener.domain;

/** The requested alias has a bad format or is reserved. The message is safe to return to the client. */
public class InvalidAliasException extends RuntimeException {

    public InvalidAliasException(String message) {
        super(message);
    }
}
