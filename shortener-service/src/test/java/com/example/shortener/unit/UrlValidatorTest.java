package com.example.shortener.unit;

import com.example.shortener.domain.InvalidUrlException;
import com.example.shortener.domain.UrlValidator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @MethodSource("validDestinations")
    void acceptsAbsoluteHttpDestinationsWithoutChangingThem(String destination) {
        assertSame(destination, validator.validate(destination));
    }

    static Stream<String> validDestinations() {
        return Stream.of(
                "http://example.com",
                "https://example.com/articles?source=email#overview",
                "HTTPS://Example.COM/A%2fb/../C?x=%20&x=2#Fragment",
                "http://localhost:8080/",
                "http://127.0.0.1/path",
                "https://[::1]:65535/path",
                "http://example.com:0/",
                "http://example.com:65535/",
                "https://example.com/a%40b?q=a%20b#part",
                "https://example.com/path?empty=&repeated=1&repeated=2",
                "https://does-not-exist.invalid/path",
                "https://example.com:443/");
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {
            "/relative", "//example.com/path", "example.com", "ftp://example.com/",
            "mailto:person@example.com", "https:example.com", "https:///path",
            "https://", "https://?query", "https://#fragment", "https://:443/",
            "https://user:password@example.com/", "https://user@example.com/",
            "https://@example.com/", " https://example.com/", "https://example.com/ ",
            "https://example.com/a b", "http://example.com:65536/",
            "http://example.com:2147483648/", "http://example.com:999999999999999/",
            "http://example.com:-1/", "http://example.com:abc/",
            "http://example.com/%", "http://example.com/%GG",
            "http://[::1/", "http://example.com/a|b", "http://example.com/a\\b"
    })
    void rejectsMissingOrInvalidDestination(String destination) {
        assertThrows(InvalidUrlException.class, () -> validator.validate(destination));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, 10, 13, 31, 32, 127, 133, 160, 8195, 8232, 8239})
    void rejectsWhitespaceAndControlCharactersAnywhere(int codePoint) {
        String character = Character.toString(codePoint);
        assertAll(
                () -> assertThrows(InvalidUrlException.class,
                        () -> validator.validate(character + "https://example.com/")),
                () -> assertThrows(InvalidUrlException.class,
                        () -> validator.validate("https://example.com/a" + character + "b")),
                () -> assertThrows(InvalidUrlException.class,
                        () -> validator.validate("https://example.com/" + character)));
    }

    @Test
    void acceptsExactly2048CharactersAndRejects2049() {
        String prefix = "https://example.com/";
        String atLimit = prefix + "a".repeat(2048 - prefix.length());
        assertSame(atLimit, validator.validate(atLimit));
        assertThrows(InvalidUrlException.class, () -> validator.validate(atLimit + "a"));
    }
}
