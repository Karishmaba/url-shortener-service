package com.example.shortener.persistence;

import com.example.shortener.domain.ShortLink;
import java.util.Optional;

/** Persistence operations shared by link creation, redirects and statistics. */
public interface LinkRepository {

    /** Inserts in autocommit mode; duplicate codes throw CodeCollisionException. */
    void insert(String code, String destination);

    /** Looks up a case-sensitive code without changing its redirect count. */
    Optional<ShortLink> find(String code);

    /** Commits one increment and reads the destination in the same transaction. */
    Optional<String> incrementAndGetDestination(String code);

    /** Executes a database query without modifying stored links. */
    void checkConnectivity();
}
