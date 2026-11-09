package com.example.shortener.domain;

/** Signals a duplicate code, allowing the service to retry only collisions. */
public class CodeCollisionException extends RuntimeException {

    public CodeCollisionException() {
        super("Generated short code already exists.");
    }

    public CodeCollisionException(Throwable cause) {
        super("Generated short code already exists.", cause);
    }
}
