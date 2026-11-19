package org.arquillian.website.data;

import java.time.Duration;
import java.util.Objects;

/** Defines how one provider response is identified, aged, and revalidated. */
public record CachePolicy(String key, Duration maxAge, boolean conditionalRequests, boolean allowStaleFallback) {

    /** Creates a policy that does not serve stale data after a provider failure. */
    public CachePolicy(String key, Duration maxAge, boolean conditionalRequests) {
        this(key, maxAge, conditionalRequests, false);
    }

    /** Validates the cache key and age policy used by a provider request. */
    public CachePolicy {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(maxAge, "maxAge");
        if (key.isBlank() || key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new IllegalArgumentException("key must be a non-blank cache filename without path traversal");
        }
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("maxAge must not be negative");
        }
    }
}
