package dev.vikas.shortener.domain;

/** The requested alias is already used by another link. */
public class AliasUnavailableException extends RuntimeException {

    public AliasUnavailableException(String alias) {
        super("Alias '" + alias + "' is already in use");
    }
}
