package org.arquillian.website.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the public freshness, revalidation, refresh, and fallback behavior of the cache client. */
class CachedHttpClientTest {

    @TempDir
    Path cacheDirectory;

    /** Confirms that a fresh response is served without a second network request. */
    @Test
    void freshHitAvoidsNetwork() throws Exception {
        try (TestServer server = TestServer.body(200, "fresh")) {
            CachedHttpClient client = client(RefreshPolicy.NORMAL);
            CachePolicy policy = new CachePolicy("fresh", Duration.ofHours(1), true);

            CachedHttpClient.CachedResponse first = client.fetch(server.uri(), policy);
            CachedHttpClient.CachedResponse second = client.fetch(server.uri(), policy);

            assertEquals("fresh", first.entry().body());
            assertTrue(second.fromCache());
            assertEquals(1, server.requests());
        }
    }

    /** Confirms that a stale entry is revalidated and refreshed after an HTTP 304 response. */
    @Test
    void staleEntryUsesNotModified() throws Exception {
        try (TestServer server = TestServer.etagged()) {
            CachedHttpClient client = client(RefreshPolicy.NORMAL);
            CachePolicy policy = new CachePolicy("stale", Duration.ZERO, true);

            client.fetch(server.uri(), policy);
            CachedHttpClient.CachedResponse second = client.fetch(server.uri(), policy);

            assertTrue(second.fromCache());
            assertTrue(second.revalidated());
            assertEquals("cached", second.entry().body());
            assertEquals(2, server.requests());
            assertEquals("\"v1\"", server.lastIfNoneMatch());
        }
    }

    /** Confirms that forced refresh bypasses both cache reads and conditional request headers. */
    @Test
    void forcedRefreshBypassesCache() throws Exception {
        try (TestServer server = TestServer.body(200, "live")) {
            CachePolicy policy = new CachePolicy("forced", Duration.ofHours(1), true);
            client(RefreshPolicy.NORMAL).fetch(server.uri(), policy);
            CachedHttpClient.CachedResponse response = client(RefreshPolicy.FORCE).fetch(server.uri(), policy);

            assertFalse(response.fromCache());
            assertEquals(2, server.requests());
            assertNull(server.lastIfNoneMatch());
        }
    }

    /** Confirms that provider caches cannot be redirected into tracked data directories. */
    @Test
    void rejectsTrackedDataCacheDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> new CachedHttpClient(Path.of("data/generated"), HttpClient.newHttpClient(), RefreshPolicy.NORMAL));
    }

    /** Confirms that a blank property does not mask a force-refresh environment setting. */
    @Test
    void refreshPolicyUsesEnvironmentWhenPropertyIsBlank() {
        assertEquals(RefreshPolicy.FORCE, RefreshPolicy.from(Map.of("roq.refresh", ""), Map.of("ROQ_REFRESH", "true")));
    }

    /** Confirms offline mode reads an existing cache entry without contacting the provider. */
    @Test
    void offlineModeUsesCacheOnly() throws Exception {
        try (TestServer server = TestServer.body(200, "cached")) {
            CachePolicy policy = new CachePolicy("offline", Duration.ZERO, true);
            client(RefreshPolicy.NORMAL).fetch(server.uri(), policy);
            CachedHttpClient.CachedResponse response = client(RefreshPolicy.OFFLINE).fetch(server.uri(), policy);

            assertTrue(response.fromCache());
            assertEquals(1, server.requests());
        }
    }

    /** Confirms that a provider may explicitly serve stale data when the live request fails. */
    @Test
    void providerFailureMayUseStaleEntry() throws Exception {
        TestServer server = TestServer.body(200, "stale");
        try {
            URI uri = server.uri();
            client(RefreshPolicy.NORMAL).fetch(uri, new CachePolicy("fallback", Duration.ZERO, true));
            server.stop();

            CachedHttpClient.CachedResponse response = client(RefreshPolicy.NORMAL).fetch(uri,
                    new CachePolicy("fallback", Duration.ZERO, true, true));

            assertTrue(response.fromCache());
            assertEquals("stale", response.entry().body());
        } finally {
            server.stop();
        }
    }

    /** Confirms that a missing cache entry produces an actionable provider failure. */
    @Test
    void providerFailureNamesUriAndCacheKey() throws Exception {
        try (TestServer server = TestServer.body(503, "unavailable")) {
            IOException failure = assertThrows(IOException.class,
                    () -> client(RefreshPolicy.NORMAL).fetch(server.uri(),
                            new CachePolicy("missing", Duration.ZERO, true)));

            assertTrue(failure.getMessage().contains(server.uri().toString()));
            assertTrue(failure.getMessage().contains("missing"));
        }
    }

    /** Creates a client backed by the test cache directory and JDK HTTP client. */
    private CachedHttpClient client(RefreshPolicy refreshPolicy) {
        return new CachedHttpClient(cacheDirectory, HttpClient.newHttpClient(), refreshPolicy);
    }

    /** Provides a small deterministic HTTP endpoint for cache contract tests. */
    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final int status;
        private final String body;
        private final boolean etagged;
        private volatile String lastIfNoneMatch;

        private TestServer(int status, String body, boolean etagged) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            this.status = status;
            this.body = body;
            this.etagged = etagged;
            this.server.createContext("/data", this::handle);
            this.server.start();
        }

        /** Creates a response endpoint with a fixed status and body. */
        static TestServer body(int status, String body) throws IOException {
            return new TestServer(status, body, false);
        }

        /** Creates an endpoint that answers conditional requests with HTTP 304. */
        static TestServer etagged() throws IOException {
            return new TestServer(200, "cached", true);
        }

        /** Returns the endpoint URI used by the test. */
        URI uri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/data");
        }

        /** Returns the number of requests received by the endpoint. */
        int requests() {
            return requests.get();
        }

        /** Returns the last conditional request value received by the endpoint. */
        String lastIfNoneMatch() {
            return lastIfNoneMatch;
        }

        /** Stops the endpoint so callers can simulate a provider outage. */
        void stop() {
            server.stop(0);
        }

        /** Handles one test request with deterministic response metadata. */
        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            lastIfNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            if (etagged && "\"v1\"".equals(lastIfNoneMatch)) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
            if (etagged) {
                exchange.getResponseHeaders().set("ETag", "\"v1\"");
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        /** Stops the endpoint when a test scope closes. */
        @Override
        public void close() {
            stop();
        }
    }
}
