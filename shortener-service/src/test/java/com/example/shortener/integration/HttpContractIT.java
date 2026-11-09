package com.example.shortener.integration;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Timeout(30)
class HttpContractIT {
    private static final String DESTINATION = "HTTPS://Example.com/A%2fb/../C?x=%20&x=2#Fragment";
    private TestApplication app;

    @BeforeEach
    void start() {
        app = TestApplication.fresh();
    }

    @AfterEach
    void stop() {
        if (app != null) app.close();
    }

    @Test
    void createsExactContractAndUsesConfiguredPublicOrigin() throws Exception {
        MockHttpServletResponse response = app.request(post("/api/urls")
                .contentType("application/json; charset=UTF-8")
                .content(app.json.writeValueAsString(Map.of("url", DESTINATION, "ignored", true)))
                .header("Host", "untrusted.example")
                .header("X-Forwarded-Host", "untrusted.example")
                .header("X-Forwarded-Proto", "http"), 201);
        JsonNode body = app.body(response);
        String code = body.path("code").stringValue();
        assertEquals(Set.of("code", "url", "shortUrl"), new HashSet<>(body.propertyNames()));
        assertTrue(code.matches("[A-Za-z0-9]{8}"));
        assertEquals(DESTINATION, body.path("url").stringValue());
        assertEquals("https://sho.rt:9443/" + code, body.path("shortUrl").stringValue());
        assertEquals(body.path("shortUrl").stringValue(), response.getHeader("Location"));
        assertEquals(0L, app.count(code));
        assertEquals(1L, app.rowCount());
    }

    @Test
    void repeatedDestinationCreatesSeparateZeroCountLinks() throws Exception {
        JsonNode first = app.create(DESTINATION);
        JsonNode second = app.create(DESTINATION);
        String firstCode = first.path("code").stringValue();
        String secondCode = second.path("code").stringValue();
        assertNotEquals(firstCode, secondCode);
        assertEquals(first.path("url"), second.path("url"));
        assertEquals(0L, app.count(firstCode));
        assertEquals(0L, app.count(secondCode));
        assertEquals(2L, app.rowCount());
    }

    @Test
    void getCountsOnceAndHeadReturnsSameHeadersWithoutCounting() throws Exception {
        String code = app.create(DESTINATION).path("code").stringValue();
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse head = app.request(head("/{code}", code), 302);
            assertEquals(DESTINATION, head.getHeader("Location"));
            assertEquals("no-store", head.getHeader("Cache-Control"));
            assertEquals(0, head.getContentAsByteArray().length);
            assertEquals(0L, app.count(code));
        }
        MockHttpServletResponse redirect = app.request(get("/{code}", code), 302);
        assertEquals(DESTINATION, redirect.getHeader("Location"));
        assertEquals("no-store", redirect.getHeader("Cache-Control"));
        assertEquals(1L, app.count(code));
        app.request(get("/{code}", code), 302);
        assertEquals(2L, app.count(code));
    }

    @Test
    void statsAndHealthAreReadOnlyAndSupportLongCounts() throws Exception {
        String code = app.create(DESTINATION).path("code").stringValue();
        app.jdbc.update("UPDATE short_links SET redirect_count = ? WHERE code = ?", 3_000_000_000L, code);
        for (int i = 0; i < 3; i++) {
            assertEquals(Map.of("status", "UP"), app.json.readValue(
                    app.request(get("/api/health"), 200).getContentAsString(), Map.class));
            assertEquals(3_000_000_000L, app.count(code));
        }
        assertEquals(1L, app.rowCount());
    }

    @Test
    void healthChecksLiveDatabaseAndUnexpectedFailuresHideDetails() throws Exception {
        assertEquals(0L, app.rowCount());
        app.request(get("/api/health"), 200);
        assertEquals(0L, app.rowCount());
        app.dataSource().close();
        app.error(get("/api/health"), 500, "INTERNAL_ERROR");
        app.error(app.creationRequest(DESTINATION), 500, "INTERNAL_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing1", "short", "123456789", "aB3dE6g_"})
    void unknownOrMalformedCodesReturn404AndDoNotMutate(String code) throws Exception {
        String existing = app.create(DESTINATION).path("code").stringValue();
        app.error(get("/{code}", code), 404, "NOT_FOUND");
        app.error(get("/api/urls/{code}/stats", code), 404, "NOT_FOUND");
        assertEquals(0, app.request(head("/{code}", code), 404).getContentAsByteArray().length);
        assertEquals(0L, app.count(existing));
        assertEquals(1L, app.rowCount());
    }

    @Test
    void codeLookupsAreCaseSensitiveAndBothVariantsCanExist() throws Exception {
        app.repository.insert("aB3dE6gH", DESTINATION);
        app.error(get("/Ab3dE6Gh"), 404, "NOT_FOUND");
        app.error(get("/api/urls/Ab3dE6Gh/stats"), 404, "NOT_FOUND");
        app.repository.insert("Ab3dE6Gh", "https://other.invalid/");
        assertEquals(DESTINATION, app.request(get("/aB3dE6gH"), 302).getHeader("Location"));
        assertEquals("https://other.invalid/", app.request(head("/Ab3dE6Gh"), 302).getHeader("Location"));
        assertEquals(1L, app.count("aB3dE6gH"));
        assertEquals(0L, app.count("Ab3dE6Gh"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{", "[]", "null", "123", "\"string\"", "{} {}",
            "{} true", "{} junk", "{\"url\":\"https://example.com/\",}", "{'url':'https://example.com/'}"})
    void missingMalformedNonObjectOrTrailingJsonReturnsInvalidJson(String body) throws Exception {
        app.error(post("/api/urls").contentType("application/json").content(body), 400, "INVALID_JSON");
        assertEquals(0L, app.rowCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"url\":null}", "{\"url\":123}", "{\"url\":true}",
            "{\"url\":[]}", "{\"url\":{}}"})
    void missingOrNonStringFieldReturnsInvalidUrl(String body) throws Exception {
        app.error(post("/api/urls").contentType("application/json").content(body), 400, "INVALID_URL");
        assertEquals(0L, app.rowCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "relative", "ftp://example.com/", "https:///path",
            "https://user:password@example.com/", "https://user@example.com/",
            "https://example.com/a b", "https://example.com/\n", "https://example.com/\u00a0",
            "https://example.com:65536/", "https://example.com/%GG"})
    void invalidDestinationsReturnFieldErrorAndCreateNoRows(String destination) throws Exception {
        app.error(app.creationRequest(destination), 400, "INVALID_URL");
        assertEquals(0L, app.rowCount());
    }

    @Test
    void accepts2048CharactersAndRejects2049WithoutChangingExistingLink() throws Exception {
        String prefix = "https://does-not-exist.invalid/";
        String atLimit = prefix + "a".repeat(2048 - prefix.length());
        JsonNode accepted = app.create(atLimit);
        String code = accepted.path("code").stringValue();
        assertEquals(atLimit, accepted.path("url").stringValue());
        assertEquals(atLimit, app.request(head("/{code}", code), 302).getHeader("Location"));
        app.error(app.creationRequest(atLimit + "a"), 400, "INVALID_URL");
        assertEquals(1L, app.rowCount());
        assertEquals(0L, app.count(code));
    }

    @Test
    void permitsMaximumPortAndPreservesEscapesQueryAndFragment() throws Exception {
        String destination = "https://does-not-exist.invalid:65535/A%2fb?q=%20&x=2#Fragment";
        JsonNode created = app.create(destination);
        assertEquals(destination, created.path("url").stringValue());
        assertEquals(destination, app.request(get("/{code}", created.path("code").stringValue()), 302).getHeader("Location"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded"})
    void unsupportedMediaTypesReturn415(String type) throws Exception {
        var request = post("/api/urls").content("{}");
        if (type != null) request.contentType(type);
        app.error(request, 415, "UNSUPPORTED_MEDIA_TYPE");
        assertEquals(0L, app.rowCount());
    }

    @Test
    void unmappedResourcesUseJsonNotFound() throws Exception {
        app.error(get("/"), 404, "NOT_FOUND");
        app.error(get("/unknown/path"), 404, "NOT_FOUND");
    }
}
