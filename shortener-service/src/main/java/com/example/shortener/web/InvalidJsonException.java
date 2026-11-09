package com.example.shortener.web;

public class InvalidJsonException extends RuntimeException {

    public InvalidJsonException() {
        super("Provide one valid JSON object containing a url field.");
    }
}
