package com.example.shortener.domain;

import java.security.SecureRandom;
import java.util.Objects;

/** Produces candidate codes without maintaining an in-memory uniqueness cache. */
public final class SecureRandomCodeGenerator implements CodeGenerator {

    public static final int CODE_LENGTH = 8;
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random;

    public SecureRandomCodeGenerator() {
        this(new SecureRandom());
    }

    public SecureRandomCodeGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public String nextCode() {
        char[] code = new char[CODE_LENGTH];
        for (int index = 0; index < code.length; index++) {
            code[index] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
