package org.arquillian.site.refresh;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Atomically merges validated catalog observations while retaining historical rows and unrelated domains. */
public final class SnapshotStore {
    private static final List<String> DOMAINS = List.of("repositories", "releases", "identities", "modules", "guides", "events");
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Path catalog;

    /** Selects the single catalog file without creating or modifying it. */
    public SnapshotStore(Path catalog) {
        this.catalog = java.util.Objects.requireNonNull(catalog).toAbsolutePath().normalize();
    }

    /** Reads and validates local snapshots, returning an empty complete catalog on first use. */
    public synchronized ObjectNode read() throws IOException {
        if (!Files.exists(catalog)) {
            ObjectNode empty = JsonNodeFactory.instance.objectNode();
            DOMAINS.forEach(empty::putArray);
            return empty;
        }
        JsonNode value = mapper.readTree(Files.readString(catalog, StandardCharsets.UTF_8));
        if (!(value instanceof ObjectNode object)) {
            throw new IOException("Catalog must be a JSON object");
        }
        validate(object, true);
        return object;
    }

    /** Runs the exact publication merge and validation without writing a file or creating directories. */
    public synchronized ObjectNode preview(ObjectNode updates) throws IOException {
        validate(updates, false);
        ObjectNode merged = read();
        for (String domain : DOMAINS) {
            Map<String, ObjectNode> rows = new TreeMap<>();
            for (JsonNode row : merged.path(domain)) {
                rows.put(row.path("id").asText(), (ObjectNode) row.deepCopy());
            }
            for (JsonNode candidate : updates.path(domain)) {
                String id = candidate.path("id").asText();
                ObjectNode previous = rows.get(id);
                if (previous != null) {
                    List<String> protectedFields = domain.equals("identities")
                            ? List.of("source", "source_id", "github_id", "jboss_username", "username")
                            : List.of("source", "source_id");
                    for (String field : protectedFields) {
                        if (previous.has(field) && candidate.has(field) && !previous.get(field).equals(candidate.get(field))) {
                            throw new IOException("Catalog identity change requires review: " + domain + "/" + id);
                        }
                    }
                    previous.setAll((ObjectNode) candidate.deepCopy());
                } else {
                    rows.put(id, (ObjectNode) candidate.deepCopy());
                }
            }
            ArrayNode array = merged.putArray(domain);
            rows.values().forEach(array::add);
        }
        validate(merged, true);
        return (ObjectNode) canonical(merged);
    }

    /** Stages a deterministic merged catalog and atomically replaces the old file only after preview validation. */
    public synchronized ObjectNode merge(ObjectNode updates) throws IOException {
        ObjectNode stable = preview(updates);
        Files.createDirectories(catalog.getParent());
        Path temporary = Files.createTempFile(catalog.getParent(), ".catalog-", ".json");
        try {
            Files.writeString(temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(stable) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, catalog, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return stable;
    }

    /** Rejects malformed domains, duplicate stable IDs and source-identity rebinding before any write. */
    private void validate(ObjectNode value, boolean complete) throws IOException {
        if (value == null) {
            throw new IOException("Catalog updates must be an object");
        }
        for (String name : value.properties().stream().map(Map.Entry::getKey).toList()) {
            if (!DOMAINS.contains(name)) {
                throw new IOException("Unknown catalog domain: " + name);
            }
        }
        for (String domain : DOMAINS) {
            JsonNode array = value.get(domain);
            if (array == null && !complete) {
                continue;
            }
            if (array == null || !array.isArray()) {
                throw new IOException("Catalog domain must be an array: " + domain);
            }
            Set<String> ids = new HashSet<>();
            Map<String, String> sourceIds = new HashMap<>();
            Set<String> accounts = new HashSet<>();
            for (JsonNode row : array) {
                if (!row.isObject() || !row.path("id").isTextual() || row.path("id").asText().isBlank()
                        || !ids.add(row.path("id").asText())) {
                    throw new IOException("Missing, invalid or duplicate catalog ID in " + domain);
                }
                if (row.has("source") || row.has("source_id")) {
                    if (!row.path("source").isTextual() || row.path("source").asText().isBlank()
                            || !(row.path("source_id").isTextual() || row.path("source_id").isIntegralNumber())
                            || row.path("source_id").asText().isBlank()) {
                        throw new IOException("Invalid catalog source identity in " + domain);
                    }
                    String sourceId = row.path("source").asText() + "\u0000" + row.path("source_id").asText();
                    if (sourceIds.putIfAbsent(sourceId, row.path("id").asText()) != null) {
                        throw new IOException("Source identity assigned to multiple catalog IDs in " + domain);
                    }
                }
                if (domain.equals("identities")) {
                    for (String field : List.of("github_id", "jboss_username", "username")) {
                        if (row.hasNonNull(field) && (!row.path(field).isTextual() || row.path(field).asText().isBlank()
                                || !accounts.add(field + "\u0000" + row.path(field).asText()))) {
                            throw new IOException("Invalid or reassigned identity account key: " + field);
                        }
                    }
                }
            }
        }
    }

    /** Sorts object keys recursively while preserving meaningful nested-array ordering. */
    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            Set<String> names = new TreeSet<>();
            value.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                result.set(name, canonical(value.get(name)));
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            value.forEach(element -> result.add(canonical(element)));
            return result;
        }
        return value.deepCopy();
    }
}
