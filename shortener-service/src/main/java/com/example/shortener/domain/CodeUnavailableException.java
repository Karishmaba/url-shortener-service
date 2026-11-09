package com.example.shortener.domain;

public class CodeUnavailableException extends RuntimeException {

    public CodeUnavailableException() {
        super("Unable to generate a unique short code after ten attempts. Retry the request.");
    }
}
