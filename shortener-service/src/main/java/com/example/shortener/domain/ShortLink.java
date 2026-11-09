package com.example.shortener.domain;

/** A stored link with its original destination and committed redirect count. */
public record ShortLink(String code, String destination, long redirectCount) {
}
