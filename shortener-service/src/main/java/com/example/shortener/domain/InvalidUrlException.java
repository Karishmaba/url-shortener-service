package com.example.shortener.domain;

public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException() {
        super("Provide an absolute HTTP or HTTPS URL with a host, without credentials "
                + "or whitespace (maximum 2048 characters).");
    }
}
