package com.example.shortener.integration;

import com.example.shortener.persistence.JdbcLinkRepository;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Timeout(30)
class CollisionAndTransactionIT {
    private static final String ORIGINAL = "https://original.invalid/A%2fb?x=%20#Fragment";
    private static final String NEW_DESTINATION = "https://new.invalid/path";

    @ParameterizedTest
    @ValueSource(ints = {1, 9})
    void retriesRealPrimaryKeyCollisionsAndCanSucceedOnTenthAttempt(int collisions) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (TestApplication app = new TestApplication(TestApplication.memoryDatabase(),
                () -> attempts.incrementAndGet() <= collisions ? "Taken001" : "Fresh001")) {
            app.repository.insert("Taken001", ORIGINAL);
            app.jdbc.update("UPDATE short_links SET redirect_count = 7 WHERE code = ?", "Taken001");
            var created = app.create(NEW_DESTINATION);
            assertEquals("Fresh001", created.path("code").stringValue());
            assertEquals(NEW_DESTINATION, created.path("url").stringValue());
            assertEquals(collisions + 1, attempts.get());
            assertEquals(ORIGINAL, app.repository.find("Taken001").orElseThrow().destination());
            assertEquals(7L, app.count("Taken001"));
            assertEquals(0L, app.count("Fresh001"));
            assertEquals(2L, app.rowCount());
        }
    }

    @Test
    void tenRealCollisionsReturn503WithoutOverwritingOrCreatingRows() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (TestApplication app = new TestApplication(TestApplication.memoryDatabase(), () -> {
            attempts.incrementAndGet();
            return "Taken001";
        })) {
            app.repository.insert("Taken001", ORIGINAL);
            app.jdbc.update("UPDATE short_links SET redirect_count = 7 WHERE code = ?", "Taken001");
            app.error(app.creationRequest(NEW_DESTINATION), 503, "CODE_UNAVAILABLE");
            assertEquals(10, attempts.get());
            assertEquals(1L, app.rowCount());
            assertEquals(ORIGINAL, app.repository.find("Taken001").orElseThrow().destination());
            assertEquals(7L, app.count("Taken001"));
        }
    }

    @Test
    void unexpectedDatabaseFailureDoesNotConsumeTenCollisionAttempts() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (TestApplication app = new TestApplication(TestApplication.memoryDatabase(), () -> {
            attempts.incrementAndGet();
            return "Fresh001";
        })) {
            app.dataSource().close();
            app.error(app.creationRequest(NEW_DESTINATION), 500, "INTERNAL_ERROR");
            assertEquals(1, attempts.get());
        }
    }

    @Test
    void creationRetriesCommitIndependentlyOfAnOuterTransaction() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (TestApplication app = new TestApplication(TestApplication.memoryDatabase(),
                () -> attempts.incrementAndGet() == 1 ? "Taken001" : "Fresh001")) {
            app.repository.insert("Taken001", ORIGINAL);
            TransactionTemplate outer = new TransactionTemplate(app.context.getBean(PlatformTransactionManager.class));
            AtomicReference<String> createdCode = new AtomicReference<>();
            outer.executeWithoutResult(status -> {
                createdCode.set(assertDoesNotThrow(() -> app.create(NEW_DESTINATION)).path("code").stringValue());
                status.setRollbackOnly();
            });
            assertEquals("Fresh001", createdCode.get());
            assertEquals(2, attempts.get());
            assertEquals(NEW_DESTINATION, app.repository.find("Fresh001").orElseThrow().destination());
            assertEquals(ORIGINAL, app.repository.find("Taken001").orElseThrow().destination());
            assertEquals(2L, app.rowCount());
        }
    }

    @Test
    void aFailureReadingDestinationRollsBackTheRealDatabaseIncrement() throws Exception {
        try (TestApplication app = TestApplication.fresh()) {
            String code = app.create(ORIGINAL).path("code").stringValue();
            app.request(get("/{code}", code), 302);
            JdbcTemplate failingRead = new JdbcTemplate(app.dataSource()) {
                @Override
                public <T> T queryForObject(String sql, Class<T> type, Object... parameters) {
                    if (sql.startsWith("SELECT destination")) {
                        throw new DataAccessResourceFailureException("Injected read failure after update");
                    }
                    return super.queryForObject(sql, type, parameters);
                }
            };
            JdbcLinkRepository repository = new JdbcLinkRepository(failingRead,
                    app.context.getBean(PlatformTransactionManager.class));
            assertThrows(DataAccessResourceFailureException.class,
                    () -> repository.incrementAndGetDestination(code));
            assertEquals(1L, app.count(code));
            assertEquals(ORIGINAL, app.repository.find(code).orElseThrow().destination());
        }
    }
}
