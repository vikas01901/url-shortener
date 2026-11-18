package dev.vikas.shortener.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomCodeGeneratorTest {

    @Test
    void generatesCodesOfTheConfiguredLengthUsingOnlyBase62Characters() {
        RandomCodeGenerator generator = new RandomCodeGenerator(new SecureRandom(), 7);

        for (int i = 0; i < 1000; i++) {
            String code = generator.generate();
            assertEquals(7, code.length());
            assertTrue(code.matches("[0-9A-Za-z]{7}"), "unexpected character in " + code);
        }
    }

    @Test
    void producesNoDuplicatesInALargeSample() {
        RandomCodeGenerator generator = new RandomCodeGenerator(new SecureRandom(), 7);
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 20_000; i++) {
            seen.add(generator.generate());
        }

        // With 62^7 possible codes, a duplicate in 20,000 draws has a probability
        // of roughly 1 in 17,000, so this is a sanity check and not a proof.
        assertEquals(20_000, seen.size());
    }

    @Test
    void isDeterministicForAGivenRandomSource() {
        String first = new RandomCodeGenerator(new Random(42), 7).generate();
        String second = new RandomCodeGenerator(new Random(42), 7).generate();

        assertEquals(first, second);
    }

    @Test
    void rejectsLengthsOutsideTheSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> new RandomCodeGenerator(new Random(), 3));
        assertThrows(IllegalArgumentException.class, () -> new RandomCodeGenerator(new Random(), 33));
    }
}
