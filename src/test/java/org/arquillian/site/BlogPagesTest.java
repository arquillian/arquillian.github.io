package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Protects regenerable routes without introducing a second editable post snapshot or reading migration captures. */
class BlogPagesTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    /** A fresh checkout derives every missing release and exact occurrence-based archive route deterministically. */
    @Test
    void derivesAllPublishedRoutesFromCatalogAndAuthoredFrontmatter() throws IOException {
        Path nativeIndex = temporary.resolve("native.json");
        SitePages.generate(Path.of("data/catalog.json"), nativeIndex);
        BlogPages.generate(Path.of("data/catalog.json"), Path.of("content/blog"), nativeIndex,
                temporary.resolve("releases.json"), temporary.resolve("archives.json"));
        JsonNode releases = read("releases.json");
        JsonNode archives = read("archives.json");
        assertEquals(370, releases.size());
        assertEquals(574, archives.size());
        assertEquals(122, archives.valueStream().filter(row -> row.path("tag").asText().isEmpty()).count());
        assertEquals(452, archives.valueStream().filter(row -> !row.path("tag").asText().isEmpty()).count());
        assertEquals(115, archives.valueStream().filter(row -> row.path("number").asInt() == 1
                && !row.path("tag").asText().isEmpty()).count());
        assertEquals(606, releases.size() + 236);
        assertEquals(575, archives.size() + 1);
        assertTrue(releases.valueStream().anyMatch(row -> row.path("link").asText()
                .equals("/blog/2018/06/13/arquillian-cube-1-16-0/")));
        assertFalse(releases.valueStream().anyMatch(row -> row.path("link").asText()
                .equals("/blog/2018/03/29/arquillian-cube-1-15-3/")));
        for (JsonNode row : releases) assertEquals(6, row.size());
        for (JsonNode row : archives) assertEquals("Arquillian", row.path("site_title").asText());
        String first = Files.readString(temporary.resolve("releases.json"));
        String second = Files.readString(temporary.resolve("archives.json"));
        BlogPages.generate(Path.of("data/catalog.json"), Path.of("content/blog"), nativeIndex,
                temporary.resolve("releases.json"), temporary.resolve("archives.json"));
        assertEquals(first, Files.readString(temporary.resolve("releases.json")));
        assertEquals(second, Files.readString(temporary.resolve("archives.json")));
    }

    /** Authored overrides and duplicate tags change derived routes, while literal body syntax is never interpreted. */
    @Test
    void readsOnlyFrontmatterAndRetainsTagOccurrences() throws IOException {
        ObjectNode catalog = catalog();
        for (int number = 1; number <= 5; number++) catalog.withArray("releases").add(release("r" + number, number));
        source("one.adoc", "title: Edited\nlink: /blog/2020/01/01/post-1/\ndate: '2030-01-01'\nauthor: unknown\ntags: [edited, edited]\n", "{=literal} <script>---</script>");
        source("draft.adoc", "draft: true\nlink: /blog/2020/01/06/post-6/\n", "ignored");
        generate(catalog);
        assertEquals(4, read("releases.json").size());
        assertTrue(read("archives.json").valueStream().anyMatch(row -> row.path("tag").asText().equals("edited")));
        assertFalse(read("archives.json").valueStream().anyMatch(row -> row.path("link").asText().equals("/blog/page/2/")));
        source("new.html", "title: New\nlink: /blog/2020/01/07/post-7/\ndate: '2020-01-07'\nauthor: unknown\ntags: [edited, edited, edited, edited]\n", "{#if broken");
        generate(catalog);
        assertTrue(read("archives.json").valueStream().anyMatch(row -> row.path("link").asText().equals("/blog/tags/edited/page/2/")));
        assertTrue(read("archives.json").valueStream().anyMatch(row -> row.path("link").asText().equals("/blog/page/2/")));
    }

    /** Ordinary native pages may use Roq title/date defaults and omit tags, matching the runtime metadata API. */
    @Test
    void preservesNativeDefaultsForNewOrdinaryPosts() throws IOException {
        source("2020-01-07-new-post.adoc", "link: /blog/2020/01/07/new-post/\nauthor: unknown\n", "Literal body");
        generate(catalog());
        assertEquals(0, read("releases.json").size());
        assertEquals(1, read("archives.json").size());
        assertEquals("/blog/", read("archives.json").get(0).path("link").asText());
    }

    /** Bad canonical links, catalog IDs, dates and tags fail without replacing either prior route index. */
    @Test
    void rejectsMalformedRowsAndPreservesPreviousOutputs() throws IOException {
        ObjectNode catalog = catalog();
        catalog.withArray("releases").add(release("one", 1));
        generate(catalog);
        String previous = Files.readString(temporary.resolve("archives.json"));
        for (String field : List.of("id", "link", "title", "date", "tags")) {
            ObjectNode broken = catalog.deepCopy();
            ((ObjectNode) broken.withArray("releases").get(0)).remove(field);
            assertThrows(IllegalArgumentException.class, () -> generate(broken), field);
            assertEquals(previous, Files.readString(temporary.resolve("archives.json")));
        }
        for (String route : List.of("//evil.test/", "/blog/2020/01/01/../", "/blog/2020/01/01/x%2fy/",
                "/blog/2020/01/01/post-1", "/modules/a/")) {
            ObjectNode broken = catalog.deepCopy();
            ((ObjectNode) broken.withArray("releases").get(0)).put("link", route);
            assertThrows(IllegalArgumentException.class, () -> generate(broken), route);
        }
        ObjectNode broken = catalog.deepCopy();
        ((ObjectNode) broken.withArray("releases").get(0)).put("date", "2020-02-31");
        assertThrows(IllegalArgumentException.class, () -> generate(broken));
        source("bad.adoc", "title: Missing closing delimiter\n", "body");
        Files.writeString(temporary.resolve("blog/bad.adoc"), "---\nlink: /blog/2020/01/02/post-2/\n");
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
    }

    /** All collision classes are rejected before publication, including native routes, duplicate authored pages and IDs. */
    @Test
    void rejectsCatalogAuthoredAndNativeCollisions() throws IOException {
        ObjectNode catalog = catalog();
        catalog.withArray("releases").add(release("one", 1));
        catalog.withArray("releases").add(release("one", 2));
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
        catalog.withArray("releases").remove(1);
        catalog.withArray("releases").add(release("two", 1));
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
        catalog.withArray("releases").remove(1);
        source("one.adoc", "title: One\nlink: /blog/2020/01/01/post-1/\n", "one");
        source("duplicate.adoc", "title: Two\nlink: /blog/2020/01/01/post-1/\n", "two");
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
        Files.delete(temporary.resolve("blog/duplicate.adoc"));
        Files.writeString(temporary.resolve("native.json"), "[{\"id\":\"native\",\"ref\":\"native\",\"link\":\"/blog/\"}]");
        assertThrows(IllegalArgumentException.class, () -> generate(catalog));
    }

    /** Input/output aliases and invalid JSON cannot destroy authoritative data or a successful index. */
    @Test
    void rejectsAliasedOutputsAndInvalidInputs() throws IOException {
        generate(catalog());
        Path catalog = temporary.resolve("catalog.json");
        Path nativeIndex = temporary.resolve("native.json");
        Path blog = temporary.resolve("blog");
        Path releases = temporary.resolve("releases.json");
        Path archives = temporary.resolve("archives.json");
        assertThrows(IllegalArgumentException.class, () -> BlogPages.generate(catalog, blog, nativeIndex, catalog, archives));
        assertThrows(IllegalArgumentException.class, () -> BlogPages.generate(catalog, blog, nativeIndex, releases, releases));
        Files.writeString(catalog, "{broken");
        assertThrows(IOException.class, () -> BlogPages.generate(catalog, blog, nativeIndex, releases, archives));
        assertEquals("[ ]", Files.readString(releases));
    }

    /** Writes one controlled fixture through the same build entry point used on a clean checkout. */
    private void generate(ObjectNode catalog) throws IOException {
        Files.createDirectories(temporary.resolve("blog"));
        JSON.writeValue(temporary.resolve("catalog.json").toFile(), catalog);
        Path nativeIndex = temporary.resolve("native.json");
        if (!Files.exists(nativeIndex)) Files.writeString(nativeIndex, "[]");
        BlogPages.generate(temporary.resolve("catalog.json"), temporary.resolve("blog"), nativeIndex,
                temporary.resolve("releases.json"), temporary.resolve("archives.json"));
    }

    /** Builds a minimal catalog containing only the authority needed for blog generation. */
    private static ObjectNode catalog() {
        ObjectNode catalog = JSON.createObjectNode();
        catalog.putArray("releases");
        return catalog;
    }

    /** Supplies a valid release with no copied article body. */
    private static ObjectNode release(String id, int number) {
        ObjectNode release = JSON.createObjectNode().put("id", id).put("title", "Release " + number)
                .put("link", "/blog/2020/01/%02d/post-%d/".formatted(number, number)).put("date", "2020-01-%02d".formatted(number));
        release.putArray("tags").addObject().put("title", "release").put("href", "/blog/tags/release/");
        return release;
    }

    /** Creates a native source whose body is deliberately opaque to the route generator. */
    private void source(String filename, String frontmatter, String body) throws IOException {
        Files.createDirectories(temporary.resolve("blog"));
        Files.writeString(temporary.resolve("blog").resolve(filename), "---\n" + frontmatter + "---\n" + body);
    }

    /** Reads only disposable output metadata for concise schema assertions. */
    private JsonNode read(String filename) throws IOException {
        return JSON.readTree(temporary.resolve(filename).toFile());
    }
}
