package com.example.shortener.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@Timeout(45)
class ConcurrencyIT {

    @Test
    void concurrentCreationsRetainDistinctCodesAndEachExactDestination() throws Exception {
        try (TestApplication app = TestApplication.fresh()) {
            List<Callable<JsonNode>> creations = new ArrayList<>();
            for (int i = 0; i < 48; i++) {
                String destination = "https://example.invalid/A%2fb?request=" + i + "#Fragment";
                creations.add(() -> app.create(destination));
            }
            List<JsonNode> results = concurrently(creations);
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < results.size(); i++) {
                JsonNode result = results.get(i);
                String code = result.path("code").stringValue();
                String expectedDestination = "https://example.invalid/A%2fb?request=" + i + "#Fragment";
                assertTrue(code.matches("[A-Za-z0-9]{8}"));
                assertTrue(codes.add(code), "Concurrent creations must not share a code");
                assertEquals(expectedDestination, result.path("url").stringValue());
                assertEquals(expectedDestination, app.repository.find(code).orElseThrow().destination());
                assertEquals(0L, app.count(code));
            }
            assertEquals(48, codes.size());
            assertEquals(48L, app.rowCount());
        }
    }

    @Test
    void concurrentGetHeadAndStatsCountOnlySuccessfulGetsForTheirOwnLink() throws Exception {
        try (TestApplication app = TestApplication.fresh()) {
            String destination = "https://does-not-exist.invalid/A%2fb?q=%20#Fragment";
            String code = app.create(destination).path("code").stringValue();
            String other = app.create("https://other.invalid/").path("code").stringValue();
            List<Callable<Void>> requests = new ArrayList<>();
            for (int i = 0; i < 160; i++) requests.add(() -> {
                assertEquals(destination, app.request(get("/{code}", code), 302).getHeader("Location"));
                return null;
            });
            for (int i = 0; i < 80; i++) requests.add(() -> {
                assertEquals(destination, app.request(head("/{code}", code), 302).getHeader("Location"));
                return null;
            });
            for (int i = 0; i < 80; i++) requests.add(() -> {
                long observed = app.count(code);
                assertTrue(observed >= 0 && observed <= 160);
                return null;
            });
            for (int i = 0; i < 16; i++) requests.add(() -> {
                app.request(get("/{code}", other), 302);
                return null;
            });
            // Interleave request types so reads and HEADs overlap with GET increments.
            java.util.Collections.shuffle(requests, new java.util.Random(3129L));
            concurrently(requests);
            assertEquals(160L, app.count(code));
            assertEquals(16L, app.count(other));
            assertEquals(2L, app.rowCount());
        }
    }

    private static <T> List<T> concurrently(List<Callable<T>> operations) throws Exception {
        int workers = Math.min(8, operations.size());
        var executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (Callable<T> operation : operations) futures.add(executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS), "Start gate timed out");
                return operation.call();
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0, "Concurrent requests exceeded their deadline");
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            start.countDown();
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Workers did not terminate");
        }
    }
}
