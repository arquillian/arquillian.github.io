package org.arquillian.site;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Derives disposable native Roq route metadata from the single authoritative domain catalog. */
public final class SitePages {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Prevents construction of the build-time entry point. */
    private SitePages() {
    }

    /** Runs before compilation through Java source-file launch so a fresh build needs no compiled classes. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Usage: SitePages.java <catalog.json> <generated.json>");
        }
        generate(Path.of(arguments[0]), Path.of(arguments[1]));
    }

    /** Replaces the generated route index only after the authoritative input has passed validation. */
    static void generate(Path catalog, Path output) throws IOException {
        if (catalog.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || (Files.exists(output) && Files.isSameFile(catalog, output))) {
            throw new IllegalArgumentException("Generated routes must not replace the authoritative catalog");
        }
        JsonNode source = JSON.readTree(catalog.toFile());
        if (source == null || !source.isObject()) {
            throw new IllegalArgumentException("Catalog must be a JSON object");
        }
        List<ObjectNode> pages = new ArrayList<>();
        Set<String> references = new HashSet<>();
        Set<String> routes = new HashSet<>();
        Set<String> identifiers = new HashSet<>();
        for (String domain : List.of("modules", "identities")) {
            JsonNode rows = source.path(domain);
            if (!rows.isArray()) {
                throw new IllegalArgumentException("Catalog domain must be an array: " + domain);
            }
            for (JsonNode row : rows) {
                if (!row.isObject()) {
                    throw new IllegalArgumentException("Catalog row must be an object: " + domain);
                }
                if (domain.equals("identities") && !row.hasNonNull("link")) {
                    continue;
                }
                String reference = required(row, "id", domain);
                if (!reference.matches("[A-Za-z0-9][A-Za-z0-9:._/-]*")) {
                    throw new IllegalArgumentException("Malformed catalog id: " + reference);
                }
                String route = required(row, "link", reference);
                String prefix = domain.equals("modules") ? "/modules/" : "/community/";
                if (!route.matches(prefix + "[A-Za-z0-9_-][A-Za-z0-9._-]*/")) {
                    throw new IllegalArgumentException("Unsafe canonical route for " + reference + ": " + route);
                }
                String name = required(row, domain.equals("modules") ? "title" : "name", reference);
                String identifier = UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8)).toString();
                if (!references.add(reference) || !identifiers.add(identifier)) {
                    throw new IllegalArgumentException("Duplicate catalog id: " + reference);
                }
                if (!routes.add(route)) {
                    throw new IllegalArgumentException("Duplicate canonical route: " + route);
                }
                ObjectNode page = JSON.createObjectNode().put("id", identifier).put("link", route)
                        .put("title", domain.equals("modules") ? name : "Community member: " + name)
                        .put("header_title", name).put("domain", domain).put("ref", reference);
                pages.add(page);
            }
        }
        pages.sort(Comparator.comparing(page -> page.path("link").asText()));
        byte[] content = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(pages);
        Path destination = output.toAbsolutePath();
        Files.createDirectories(destination.getParent());
        Path staging = Files.createTempFile(destination.getParent(), ".generated-", ".json");
        try {
            Files.write(staging, content);
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    /** Rejects missing, blank and mistyped fields with enough row context to repair the catalog. */
    private static String required(JsonNode row, String field, String context) {
        JsonNode value = row.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid " + field + " in " + context);
        }
        return value.asText();
    }
}
