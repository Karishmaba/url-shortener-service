package com.example.shortener.domain;

public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException() {
        super("Short code not found.");
    }
}
