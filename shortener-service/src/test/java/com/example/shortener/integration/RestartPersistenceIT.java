package com.example.shortener.integration;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Timeout(45)
class RestartPersistenceIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void linksAndCountsSurviveClosingAndReopeningTwoApplicationContexts() throws Exception {
        String database = "jdbc:h2:file:" + temporaryDirectory.resolve("links").toAbsolutePath();
        String destination = "HTTPS://Example.invalid/A%2fb/../C?x=%20&x=2#Fragment";
        String secondDestination = "https://other.invalid/path";
        String firstCode;
        String secondCode;
        HikariDataSource firstPool;
        try (TestApplication first = new TestApplication(database, null)) {
            firstPool = first.dataSource();
            firstCode = first.create(destination).path("code").stringValue();
            secondCode = first.create(secondDestination).path("code").stringValue();
            for (int i = 0; i < 4; i++) first.request(get("/{code}", firstCode), 302);
            for (int i = 0; i < 2; i++) first.request(get("/{code}", secondCode), 302);
            first.request(head("/{code}", firstCode), 302);
            first.request(get("/api/health"), 200);
            assertEquals(4L, first.count(firstCode));
            assertEquals(2L, first.count(secondCode));
            assertEquals(2L, first.rowCount());
        }
        assertTrue(firstPool.isClosed(), "First application must release its database connections");
        assertTrue(Files.exists(temporaryDirectory.resolve("links.mv.db")));
        try (TestApplication second = new TestApplication(database, null)) {
            assertEquals(2L, second.rowCount(), "Startup schema initialization must preserve existing rows");
            assertEquals(4L, second.count(firstCode));
            assertEquals(2L, second.count(secondCode));
            assertEquals(destination, second.request(head("/{code}", firstCode), 302).getHeader("Location"));
            assertEquals(secondDestination, second.request(head("/{code}", secondCode), 302).getHeader("Location"));
            second.request(get("/api/health"), 200);
            assertEquals(4L, second.count(firstCode));
            assertEquals(destination, second.request(get("/{code}", firstCode), 302).getHeader("Location"));
            assertEquals(5L, second.count(firstCode));
            String newCode = second.create(destination).path("code").stringValue();
            assertNotEquals(firstCode, newCode);
            assertNotEquals(secondCode, newCode);
            assertEquals(0L, second.count(newCode));
            assertEquals(3L, second.rowCount());
            assertEquals(5L, second.count(firstCode));
            assertEquals(2L, second.count(secondCode));
        }
    }
}
