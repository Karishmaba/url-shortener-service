package com.example.shortener.config;

import com.example.shortener.domain.InvalidUrlException;
import com.example.shortener.domain.UrlValidator;
import java.net.URI;

/** A configured public origin, independent of request Host or forwarded headers. */
public final class PublicBaseUrl {

    private final String origin;

    public PublicBaseUrl(String value) {
        try {
            new UrlValidator().validate(value);
        } catch (InvalidUrlException invalid) {
            throw new IllegalArgumentException("shortener.public-base-url must be an HTTP(S) origin.");
        }
        URI uri = URI.create(value);
        if (!(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("shortener.public-base-url must have no path, query or fragment.");
        }
        this.origin = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public String shortUrl(String code) {
        return origin + "/" + code;
    }
}
