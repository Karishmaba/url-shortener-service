package com.example.shortener.unit;

import com.example.shortener.domain.CodeGenerator;
import com.example.shortener.domain.SecureRandomCodeGenerator;
import java.security.SecureRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecureRandomCodeGeneratorTest {

    @Test
    void mapsSecureRandomChoicesToUppercaseLowercaseAndDigits() {
        ScriptedRandom random = new ScriptedRandom(0, 25, 26, 51, 52, 61, 1, 27);
        CodeGenerator generator = new SecureRandomCodeGenerator(random);
        assertEquals("AZaz09Bb", generator.nextCode());
        assertEquals(8, random.calls);
    }

    @Test
    void makesEveryAsciiAlphanumericCharacterAvailable() {
        ScriptedRandom random = new ScriptedRandom(IntStream.range(0, 62).toArray());
        CodeGenerator generator = new SecureRandomCodeGenerator(random);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            String code = generator.nextCode();
            assertTrue(code.matches("[A-Za-z0-9]{8}"));
            result.append(code);
        }
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789AB", result.toString());
        assertEquals(64, random.calls);
    }

    @Test
    void permitsDuplicateCandidatesSoDatabaseCanEnforceUniqueness() {
        ScriptedRandom random = new ScriptedRandom(0);
        CodeGenerator generator = new SecureRandomCodeGenerator(random);
        assertEquals("AAAAAAAA", generator.nextCode());
        assertEquals("AAAAAAAA", generator.nextCode());
        assertEquals(16, random.calls);
    }

    @Test
    void defaultConstructorProducesEightAsciiAlphanumericCharacters() {
        CodeGenerator generator = new SecureRandomCodeGenerator();
        assertTrue(generator.nextCode().matches("[A-Za-z0-9]{8}"));
    }

    private static final class ScriptedRandom extends SecureRandom {
        private final int[] choices;
        private int calls;

        private ScriptedRandom(int... choices) {
            this.choices = choices;
        }

        @Override
        public int nextInt(int bound) {
            assertEquals(62, bound, "Select uniformly from the full 62-character alphabet");
            return choices[calls++ % choices.length];
        }
    }
}
