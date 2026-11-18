package dev.vikas.shortener.domain;

import java.util.Random;

/**
 * Generates fixed-length base62 codes from a random source.
 *
 * <p>Random codes were chosen over an encoded counter because sequential codes
 * are guessable: anyone could enumerate every link in the system. With 7 base62
 * characters there are about 3.5 trillion codes, so collisions are rare and are
 * handled by retrying on the database's unique constraint.
 */
public final class RandomCodeGenerator implements CodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final Random random;
    private final int length;

    /**
     * @param random source of randomness; production passes a {@code SecureRandom}
     * @param length number of characters per code, between 4 and 32
     */
    public RandomCodeGenerator(Random random, int length) {
        if (length < 4 || length > 32) {
            throw new IllegalArgumentException("code length must be between 4 and 32");
        }
        this.random = random;
        this.length = length;
    }

    @Override
    public String generate() {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(chars);
    }
}
