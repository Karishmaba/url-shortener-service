package com.example.shortener.domain;

/** The committed number of successful GET redirects for a link. */
public record LinkStats(String code, long redirectCount) {
}
