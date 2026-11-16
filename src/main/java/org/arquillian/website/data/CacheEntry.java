package org.arquillian.website.data;

import java.time.Instant;

/** Stores one normalized HTTP response and the metadata required to revalidate it. */
public record CacheEntry(
        String body,
        Instant fetchedAt,
        String etag,
        String lastModified,
        int status,
        String contentType) {
}
