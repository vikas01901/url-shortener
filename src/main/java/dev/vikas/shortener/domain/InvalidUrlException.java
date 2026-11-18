package dev.vikas.shortener.domain;

/** The submitted URL breaks a rule. The message is safe to return to the client. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
