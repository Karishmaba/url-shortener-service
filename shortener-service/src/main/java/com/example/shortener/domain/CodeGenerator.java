package com.example.shortener.domain;

/** Generates candidate codes; database uniqueness must still enforce collisions. */
@FunctionalInterface
public interface CodeGenerator {

    /** Returns eight case-sensitive ASCII alphanumeric characters. */
    String nextCode();
}
