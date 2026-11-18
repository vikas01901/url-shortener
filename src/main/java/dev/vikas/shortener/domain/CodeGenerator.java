package dev.vikas.shortener.domain;

/** Produces candidate short codes. Uniqueness is enforced by the database. */
public interface CodeGenerator {

    String generate();
}
