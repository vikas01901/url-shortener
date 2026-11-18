package dev.vikas.shortener.domain;

/** A unique short code could not be allocated within the retry budget. */
public class CodeGenerationException extends RuntimeException {

    public CodeGenerationException(String message) {
        super(message);
    }
}
