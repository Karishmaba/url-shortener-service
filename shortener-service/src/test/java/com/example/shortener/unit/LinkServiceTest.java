package com.example.shortener.unit;

import com.example.shortener.domain.*;
import com.example.shortener.persistence.LinkRepository;
import com.example.shortener.service.LinkService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LinkServiceTest {

    private static final String DESTINATION = "HTTPS://Example.com/A%2fb/../C?x=%20&x=2#Fragment";
    private static final String CODE = "aB3dE6gH";
    private final RecordingRepository repository = new RecordingRepository();

    @Test
    void createsNewLinkWithExactDestinationAndZeroRedirects() {
        ScriptedGenerator generator = new ScriptedGenerator(CODE);
        ShortLink link = service(generator).create(DESTINATION);
        assertEquals(new ShortLink(CODE, DESTINATION, 0L), link);
        assertEquals(link, repository.rows.get(CODE));
        assertEquals(1, generator.calls);
        assertEquals(1, repository.inserts);
        assertEquals(0, repository.finds);
        assertEquals(0, repository.increments);
    }

    @Test
    void createsSeparateLinksForTheSameDestination() {
        LinkService service = service(new ScriptedGenerator(CODE, "Next0001"));
        ShortLink first = service.create(DESTINATION);
        ShortLink second = service.create(DESTINATION);
        assertNotEquals(first.code(), second.code());
        assertEquals(first.destination(), second.destination());
        assertEquals(2, repository.rows.size());
        assertEquals(0, first.redirectCount());
        assertEquals(0, second.redirectCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 9})
    void retriesCollisionsAndAllowsSuccessOnTheTenthAttempt(int collisions) {
        ShortLink original = new ShortLink("Taken001", "https://original.example/", 7L);
        repository.rows.put(original.code(), original);
        String[] codes = new String[collisions + 1];
        java.util.Arrays.fill(codes, "Taken001");
        codes[collisions] = CODE;
        ScriptedGenerator generator = new ScriptedGenerator(codes);
        ShortLink result = service(generator).create(DESTINATION);
        assertEquals(new ShortLink(CODE, DESTINATION, 0L), result);
        assertEquals(original, repository.rows.get(original.code()));
        assertEquals(collisions + 1, generator.calls);
        assertEquals(collisions + 1, repository.inserts);
        assertEquals(2, repository.rows.size());
    }

    @Test
    void stopsAfterTenCollisionsWithoutTryingAnEleventhCode() {
        ShortLink original = new ShortLink("Taken001", "https://original.example/", 7L);
        repository.rows.put(original.code(), original);
        String[] codes = new String[11];
        java.util.Arrays.fill(codes, "Taken001");
        codes[10] = CODE;
        ScriptedGenerator generator = new ScriptedGenerator(codes);
        assertThrows(CodeUnavailableException.class, () -> service(generator).create(DESTINATION));
        assertEquals(10, generator.calls);
        assertEquals(10, repository.inserts);
        assertEquals(Map.of(original.code(), original), repository.rows);
        assertEquals(0, repository.finds);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"relative", "ftp://example.com/", "https://user:password@example.com/", "https://example.com/ "})
    void rejectsInvalidDestinationsBeforeGeneratingOrInserting(String destination) {
        ScriptedGenerator generator = new ScriptedGenerator(CODE);
        assertThrows(InvalidUrlException.class, () -> service(generator).create(destination));
        assertEquals(0, generator.calls);
        assertEquals(0, repository.inserts);
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void propagatesUnexpectedInsertFailureWithoutRetrying() {
        RuntimeException failure = new IllegalStateException("storage unavailable");
        repository.failure = failure;
        ScriptedGenerator generator = new ScriptedGenerator(CODE, "Next0001");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service(generator).create(DESTINATION)));
        assertEquals(1, generator.calls);
        assertEquals(1, repository.inserts);
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void propagatesGeneratorFailureWithoutInserting() {
        RuntimeException failure = new IllegalStateException("entropy unavailable");
        CodeGenerator generator = () -> { throw failure; };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service(generator).create(DESTINATION)));
        assertEquals(0, repository.inserts);
    }

    @Test
    void redirectsUsingExactlyOneIncrementAndReturnsUnchangedDestination() {
        repository.rows.put(CODE, new ShortLink(CODE, DESTINATION, 12L));
        assertEquals(DESTINATION, service(new ScriptedGenerator(CODE)).redirect(CODE));
        assertEquals(1, repository.increments);
        assertEquals(0, repository.finds);
        assertEquals(13L, repository.rows.get(CODE).redirectCount());
    }

    @Test
    void resolvesHeadWithoutIncrementing() {
        ShortLink original = new ShortLink(CODE, DESTINATION, 12L);
        repository.rows.put(CODE, original);
        assertEquals(DESTINATION, service(new ScriptedGenerator(CODE)).resolve(CODE));
        assertEquals(1, repository.finds);
        assertEquals(0, repository.increments);
        assertEquals(original, repository.rows.get(CODE));
    }

    @Test
    void statsReturnsStoredCodeAndLongCounterWithoutIncrementing() {
        ShortLink original = new ShortLink(CODE, DESTINATION, 3_000_000_000L);
        repository.rows.put(CODE, original);
        assertEquals(new LinkStats(CODE, 3_000_000_000L), service(new ScriptedGenerator(CODE)).stats(CODE));
        assertEquals(1, repository.finds);
        assertEquals(0, repository.increments);
        assertEquals(original, repository.rows.get(CODE));
    }

    @Test
    void healthChecksConnectivityWithoutReadingOrMutatingLinks() {
        ShortLink original = new ShortLink(CODE, DESTINATION, 3L);
        repository.rows.put(CODE, original);
        service(new ScriptedGenerator(CODE)).health();
        assertEquals(1, repository.healthChecks);
        assertEquals(0, repository.inserts);
        assertEquals(0, repository.finds);
        assertEquals(0, repository.increments);
        assertEquals(original, repository.rows.get(CODE));
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"short", "123456789", "aB3dE6g_", "aB3dE6gH\n", "éB3dE6gH", " aB3dE6gH"})
    void rejectsMalformedCodesBeforeRepositoryAccess(String code) {
        LinkService service = service(new ScriptedGenerator(CODE));
        assertAll(
                () -> assertThrows(LinkNotFoundException.class, () -> service.redirect(code)),
                () -> assertThrows(LinkNotFoundException.class, () -> service.resolve(code)),
                () -> assertThrows(LinkNotFoundException.class, () -> service.stats(code)));
        assertEquals(0, repository.finds);
        assertEquals(0, repository.increments);
        assertEquals(0, repository.inserts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing1", "Ab3dE6Gh"})
    void rejectsUnknownCodesWithoutChangingExistingLinks(String unknown) {
        ShortLink original = new ShortLink(CODE, DESTINATION, 3L);
        repository.rows.put(CODE, original);
        LinkService service = service(new ScriptedGenerator(CODE));
        assertThrows(LinkNotFoundException.class, () -> service.redirect(unknown));
        assertThrows(LinkNotFoundException.class, () -> service.resolve(unknown));
        assertThrows(LinkNotFoundException.class, () -> service.stats(unknown));
        assertEquals(Map.of(CODE, original), repository.rows);
        assertEquals(1, repository.increments);
        assertEquals(2, repository.finds);
    }

    @Test
    void preservesCaseWhenBothCodeVariantsExist() {
        repository.rows.put(CODE, new ShortLink(CODE, DESTINATION, 1L));
        repository.rows.put("Ab3dE6Gh", new ShortLink("Ab3dE6Gh", "https://other.example/", 2L));
        LinkService service = service(new ScriptedGenerator(CODE));
        assertEquals(DESTINATION, service.resolve(CODE));
        assertEquals("https://other.example/", service.resolve("Ab3dE6Gh"));
        assertEquals(1L, service.stats(CODE).redirectCount());
        assertEquals(2L, service.stats("Ab3dE6Gh").redirectCount());
        assertEquals(0, repository.increments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"redirect", "resolve", "stats", "health"})
    void propagatesUnexpectedReadOrConnectivityFailures(String operation) {
        RuntimeException failure = new IllegalStateException("database unavailable");
        repository.failure = failure;
        LinkService service = service(new ScriptedGenerator(CODE));
        Runnable action = switch (operation) {
            case "redirect" -> () -> service.redirect(CODE);
            case "resolve" -> () -> service.resolve(CODE);
            case "stats" -> () -> service.stats(CODE);
            case "health" -> service::health;
            default -> throw new AssertionError(operation);
        };
        assertSame(failure, assertThrows(IllegalStateException.class, action::run));
        assertEquals(0, repository.inserts);
    }

    private LinkService service(CodeGenerator generator) {
        return new LinkService(repository, new UrlValidator(), generator);
    }

    private static final class ScriptedGenerator implements CodeGenerator {
        private final String[] codes;
        private int calls;

        private ScriptedGenerator(String... codes) {
            this.codes = codes;
        }

        @Override
        public String nextCode() {
            if (calls >= codes.length) {
                throw new AssertionError("Unexpected extra generation attempt");
            }
            return codes[calls++];
        }
    }

    /** Controlled collaborator; database atomicity is covered by integration tests. */
    private static final class RecordingRepository implements LinkRepository {
        private final Map<String, ShortLink> rows = new HashMap<>();
        private int inserts;
        private int finds;
        private int increments;
        private int healthChecks;
        private RuntimeException failure;

        @Override
        public void insert(String code, String destination) {
            inserts++;
            failIfRequested();
            if (rows.containsKey(code)) {
                throw new CodeCollisionException();
            }
            rows.put(code, new ShortLink(code, destination, 0L));
        }

        @Override
        public Optional<ShortLink> find(String code) {
            finds++;
            failIfRequested();
            return Optional.ofNullable(rows.get(code));
        }

        @Override
        public Optional<String> incrementAndGetDestination(String code) {
            increments++;
            failIfRequested();
            ShortLink existing = rows.get(code);
            if (existing == null) {
                return Optional.empty();
            }
            rows.put(code, new ShortLink(code, existing.destination(), existing.redirectCount() + 1));
            return Optional.of(existing.destination());
        }

        @Override
        public void checkConnectivity() {
            healthChecks++;
            failIfRequested();
        }

        private void failIfRequested() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
