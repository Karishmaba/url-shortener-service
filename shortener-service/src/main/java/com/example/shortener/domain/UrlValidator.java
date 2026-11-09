package com.example.shortener.domain;

import java.net.URI;
import java.net.URISyntaxException;

/** Validates destination syntax without fetching, resolving or rewriting it. */
public final class UrlValidator {

    public static final int MAX_URL_LENGTH = 2048;

    /** Returns the original input, including its path, query and fragment. */
    public String validate(String url) {
        if (url == null || url.isEmpty() || url.length() > MAX_URL_LENGTH
                || url.codePoints().anyMatch(UrlValidator::isForbiddenCharacter)) {
            throw new InvalidUrlException();
        }

        final URI destination;
        try {
            destination = new URI(url).parseServerAuthority();
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException();
        }

        String scheme = destination.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || destination.getHost() == null || destination.getHost().isEmpty()
                || destination.getRawUserInfo() != null
                || destination.getPort() > 65535) {
            throw new InvalidUrlException();
        }

        return url;
    }

    private static boolean isForbiddenCharacter(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || Character.isISOControl(codePoint);
    }
}
