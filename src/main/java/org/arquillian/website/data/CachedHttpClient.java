package org.arquillian.website.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;

/** Fetches provider responses with deterministic disk caching and conditional revalidation. */
public final class CachedHttpClient {

    private final Path cacheDirectory;
    private final HttpClient client;
    private final Clock clock;
    private final RefreshPolicy refreshPolicy;

    /** Creates a client using the system clock and the configured refresh policy. */
    public CachedHttpClient(Path cacheDirectory, HttpClient client, RefreshPolicy refreshPolicy) {
        this(cacheDirectory, client, refreshPolicy, Clock.systemUTC());
    }

    /** Creates a client with an injectable clock so cache age decisions remain deterministic. */
    public CachedHttpClient(Path cacheDirectory, HttpClient client, RefreshPolicy refreshPolicy, Clock clock) {
        this.cacheDirectory = validateCacheDirectory(cacheDirectory);
        this.client = Objects.requireNonNull(client, "client");
        this.refreshPolicy = Objects.requireNonNull(refreshPolicy, "refreshPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Rejects tracked data directories so provider output remains in ignored cache storage. */
    private static Path validateCacheDirectory(Path directory) {
        Path normalized = Objects.requireNonNull(directory, "cacheDirectory").normalize();
        for (Path segment : normalized) {
            if (segment.toString().equals("data")) {
                throw new IllegalArgumentException("provider cache must not be stored under a tracked data directory: " + directory);
            }
        }
        return directory;
    }

    /** Fetches a response, using a fresh cache entry or revalidating a stale entry when possible. */
    public CachedResponse fetch(URI uri, CachePolicy policy) throws IOException, InterruptedException {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(policy, "policy");
        Path metadataPath = cacheDirectory.resolve(policy.key() + ".properties");
        Path bodyPath = cacheDirectory.resolve(policy.key() + ".body");
        CacheEntry cached = refreshPolicy.bypassesCache() ? null : read(metadataPath, bodyPath);
        if (refreshPolicy.cacheOnly()) {
            if (cached == null) {
                throw new IOException("Offline provider mode has no cache entry for " + uri + " with cache key " + policy.key());
            }
            return new CachedResponse(cached, true, false);
        }
        if (cached != null && isFresh(cached, policy.maxAge())) {
            return new CachedResponse(cached, true, false);
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (cached != null && policy.conditionalRequests() && !refreshPolicy.bypassesCache()) {
            if (cached.etag() != null && !cached.etag().isBlank()) {
                request.header("If-None-Match", cached.etag());
            }
            if (cached.lastModified() != null && !cached.lastModified().isBlank()) {
                request.header("If-Modified-Since", cached.lastModified());
            }
        }

        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 304 && cached != null) {
                CacheEntry revalidated = new CacheEntry(cached.body(), Instant.now(clock), cached.etag(),
                        cached.lastModified(), cached.status(), cached.contentType());
                write(metadataPath, bodyPath, revalidated);
                return new CachedResponse(revalidated, true, true);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Provider request failed: " + uri + " returned HTTP " + response.statusCode()
                        + " for cache key " + policy.key());
            }
            CacheEntry fresh = new CacheEntry(response.body(), Instant.now(clock), response.headers().firstValue("ETag").orElse(""),
                    response.headers().firstValue("Last-Modified").orElse(""), response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""));
            write(metadataPath, bodyPath, fresh);
            return new CachedResponse(fresh, false, false);
        } catch (IOException exception) {
            if (cached != null && policy.allowStaleFallback()) {
                return new CachedResponse(cached, true, false);
            }
            throw new IOException("Provider request failed: " + uri + " for cache key " + policy.key(), exception);
        }
    }

    /** Returns whether an entry is within the provider's configured freshness window. */
    private boolean isFresh(CacheEntry entry, Duration maxAge) {
        return !entry.fetchedAt().plus(maxAge).isBefore(Instant.now(clock));
    }

    /** Reads one cache entry from its stable metadata and body files. */
    private CacheEntry read(Path metadataPath, Path bodyPath) throws IOException {
        if (!Files.exists(metadataPath) || !Files.exists(bodyPath)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadataPath)) {
            properties.load(input);
        }
        return new CacheEntry(Files.readString(bodyPath), Instant.parse(properties.getProperty("fetchedAt")),
                properties.getProperty("etag", ""), properties.getProperty("lastModified", ""),
                Integer.parseInt(properties.getProperty("status")), properties.getProperty("contentType", ""));
    }

    /** Writes one cache entry atomically enough for a single-process build. */
    private void write(Path metadataPath, Path bodyPath, CacheEntry entry) throws IOException {
        Files.createDirectories(cacheDirectory);
        Files.writeString(bodyPath, entry.body(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        Properties properties = new Properties();
        properties.setProperty("fetchedAt", entry.fetchedAt().toString());
        properties.setProperty("etag", entry.etag());
        properties.setProperty("lastModified", entry.lastModified());
        properties.setProperty("status", Integer.toString(entry.status()));
        properties.setProperty("contentType", entry.contentType());
        try (OutputStream output = Files.newOutputStream(metadataPath, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "Arquillian Roq provider cache");
        }
    }

    /** Returns a cached response body and freshness provenance. */
    public record CachedResponse(CacheEntry entry, boolean fromCache, boolean revalidated) {
    }
}
