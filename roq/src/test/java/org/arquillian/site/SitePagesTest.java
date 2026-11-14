package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Protects the disposable route index while the catalog remains the only domain snapshot. */
class SitePagesTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    /** Preserves historical case, underscores, titles and catalog references in stable route order. */
    @Test
    void derivesExactRoutesAndTitlesDeterministically() throws IOException {
        ObjectNode catalog = catalog();
        catalog.withArray("modules").add(module("z", "/modules/Z_Adapter-3.1/", "Z Adapter"));
        catalog.withArray("modules").add(module("a", "/modules/a-extension/", "A Extension"));
        catalog.withArray("identities").add(identity("h", "/community/Hantsy_Bai/", "Hantsy Bai"));
        JsonNode routes = generate(catalog);
        assertEquals(List.of("/community/Hantsy_Bai/", "/modules/Z_Adapter-3.1/", "/modules/a-extension/"),
                routes.valueStream().map(row -> row.path("link").asText()).toList());
        assertEquals("Community member: Hantsy Bai", routes.get(0).path("title").asText());
        assertEquals("Hantsy Bai", routes.get(0).path("header_title").asText());
        assertEquals("identities", routes.get(0).path("domain").asText());
        assertEquals("h", routes.get(0).path("ref").asText());
        assertEquals("Z Adapter", routes.get(1).path("title").asText());
        assertEquals("modules", routes.get(1).path("domain").asText());
        byte[] first = Files.readAllBytes(temporary.resolve("generated.json"));
        generate(catalog);
        assertTrue(java.util.Arrays.equals(first, Files.readAllBytes(temporary.resolve("generated.json"))));
    }

    /** Git author hints without a published local profile must never acquire invented pages. */
    @Test
    void skipsIdentitiesWithoutCanonicalPublicProfiles() throws IOException {
        ObjectNode catalog = catalog();
        catalog.withArray("identities").add(JSON.createObjectNode().put("id", "git:hint").put("name", "Author hint"));
        catalog.withArray("identities").add(JSON.createObjectNode().put("id", "git:null").putNull("link").put("name", "Author hint"));
        catalog.withArray("identities").add(identity("known", "/community/known/", "Known"));
        assertEquals(1, generate(catalog).size());
    }

    /** Rejects incomplete catalog rows instead of creating broken routes with fallback titles. */
    @Test
    void rejectsMissingAndMalformedRequiredFields() {
        for (String field : List.of("id", "title", "link")) {
            ObjectNode catalog = catalog();
            ObjectNode row = module("id", "/modules/core/", "Core");
            row.remove(field);
            catalog.withArray("modules").add(row);
            assertThrows(IllegalArgumentException.class, () -> generate(catalog), field);
            row.put(field, " ");
            assertThrows(IllegalArgumentException.class, () -> generate(catalog), field);
        }
        ObjectNode catalog = catalog();
        catalog.withArray("modules").add(module("bad id", "/modules/core/", "Core"));
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
        assertThrows(IllegalArgumentException.class, () -> generate(JSON.createObjectNode()));
    }

    /** Invalid JSON and an accidental input/output alias cannot overwrite the authoritative catalog. */
    @Test
    void preservesCatalogAndPreviousIndexOnInputErrors() throws IOException {
        Path source = temporary.resolve("catalog.json");
        Path output = temporary.resolve("generated.json");
        Files.writeString(output, "previous");
        Files.writeString(source, "{broken");
        assertThrows(IOException.class, () -> SitePages.generate(source, output));
        assertEquals("previous", Files.readString(output));
        Files.writeString(source, "{\"modules\":[],\"identities\":[]}");
        String previous = Files.readString(source);
        assertThrows(IllegalArgumentException.class, () -> SitePages.generate(source, source));
        assertEquals(previous, Files.readString(source));
    }

    /** Duplicate IDs and public routes must fail before a previous successful index is replaced. */
    @Test
    void rejectsDuplicatesWithoutReplacingOutput() throws IOException {
        ObjectNode catalog = catalog();
        catalog.withArray("modules").add(module("id", "/modules/core/", "Core"));
        generate(catalog);
        String previous = Files.readString(temporary.resolve("generated.json"));
        catalog.withArray("modules").add(module("id", "/modules/other/", "Other"));
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
        assertEquals(previous, Files.readString(temporary.resolve("generated.json")));
        catalog.withArray("modules").remove(1);
        catalog.withArray("modules").add(module("other", "/modules/core/", "Other"));
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
        assertEquals(previous, Files.readString(temporary.resolve("generated.json")));
    }

    /** Explicit local routes cannot traverse directories, escape hosts or lose their canonical slash. */
    @Test
    void rejectsUnsafeOrWrongDomainRoutes() {
        for (String route : List.of("https://evil.test/modules/core/", "//evil.test/", "/modules/../", "/modules/a%2fb/",
                "/modules/core/?q=x", "/modules/core/#x", "/modules/core", "/community/core/", "/modules/a\\b/")) {
            ObjectNode catalog = catalog();
            catalog.withArray("modules").add(module("id", route, "Core"));
            assertThrows(IllegalArgumentException.class, () -> generate(catalog), route);
        }
    }

    /** The checked-in snapshot must supply every published module and profile exactly once. */
    @Test
    void indexesAllPublishedCatalogPages() throws IOException {
        Path output = temporary.resolve("generated.json");
        SitePages.generate(Path.of("data/catalog.json"), output);
        JsonNode routes = JSON.readTree(output.toFile());
        assertEquals(480, routes.size());
        assertEquals(122, routes.valueStream().filter(row -> row.path("domain").asText().equals("modules")).count());
        assertEquals(358, routes.valueStream().filter(row -> row.path("domain").asText().equals("identities")).count());
    }

    /** Writes a fixture through the public generation boundary so validation and replacement are tested. */
    private JsonNode generate(ObjectNode catalog) throws IOException {
        Path source = temporary.resolve("catalog.json");
        Path output = temporary.resolve("generated.json");
        JSON.writeValue(source.toFile(), catalog);
        SitePages.generate(source, output);
        return JSON.readTree(output.toFile());
    }

    /** Builds a minimal supported catalog without unrelated snapshot domains. */
    private static ObjectNode catalog() {
        ObjectNode catalog = JSON.createObjectNode();
        catalog.putArray("modules");
        catalog.putArray("identities");
        return catalog;
    }

    /** Supplies the catalog fields required to publish a module route. */
    private static ObjectNode module(String id, String link, String title) {
        return JSON.createObjectNode().put("id", id).put("link", link).put("title", title);
    }

    /** Supplies an already canonical public profile rather than an unverified author hint. */
    private static ObjectNode identity(String id, String link, String name) {
        return JSON.createObjectNode().put("id", id).put("link", link).put("name", name);
    }
}
