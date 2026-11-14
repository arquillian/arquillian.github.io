package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.arquillian.site.refresh.SnapshotStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Protects the release and reference-menu metadata required by native blog layouts. */
class ReleasePageDataTest {
    private static JsonNode catalog;

    /** Validates the authoritative snapshot through the production store before checking layout fields. */
    @BeforeAll
    static void loadCatalog() throws IOException {
        catalog = new SnapshotStore(Path.of("data/catalog.json")).read();
    }

    /** Captured headings, rich definitions, artifact groups and issue counts must not be reconstructed. */
    @Test
    void retainsCapturedReleaseSectionsAndBylines() {
        JsonNode cube = release("/blog/2018/06/13/arquillian-cube-1-16-0/");
        assertEquals("What is Arquillian Cube Extension?", cube.path("definition_title").asText());
        assertTrue(cube.path("definition_html").asText().startsWith("<p>With Arquillian Cube"));
        assertEquals("org.arquillian.cube", cube.path("artifacts_group").asText());
        assertEquals(6, cube.path("issues_count").asInt());
        assertEquals("https://avatars.githubusercontent.com/u/7941617?s=44", cube.path("author_avatar_url").asText());
        assertEquals("", cube.path("author_avatar_title").asText());
        assertEquals("http://arquillian.org/blog/2018/06/13/arquillian-cube-1-16-0/", cube.path("disqus_url").asText());
        assertEquals("http://arquillian.org/blog/2018/06/13/arquillian-cube-1-16-0/#disqus_thread", cube.path("comment_url").asText());
        assertTrue(cube.path("latest_link").isNull());
        assertEquals("Posted by Dipak Pawar on Jun 13, 2018", cube.path("avatar_title").asText());
        assertEquals(0, release("/blog/2011/02/25/descriptors-0-1-1/").path("issues_count").asInt(-1));
        assertTrue(release("/blog/2011/03/17/arquillian-container-jetty-1-0-0-Alpha5/").path("issues_count").isNull());
    }

    /** Keeps historical latest warnings exact and prevents permalink pseudo-tags becoming archive categories. */
    @Test
    void preservesLatestWarningAndCanonicalTags() {
        JsonNode older = release("/blog/2018/03/29/arquillian-cube-1-15-3/");
        assertEquals("/blog/2018/06/13/arquillian-cube-1-16-0/index.html", older.path("latest_link").asText());
        assertTrue(older.path("latest_warning_html").asText().contains("Check our latest announcement"));
        for (JsonNode row : catalog.path("releases")) {
            assertTrue(row.path("tags").valueStream().allMatch(tag -> tag.path("href").asText().startsWith("/blog/tags/")));
            assertFalse(row.path("tags").valueStream().anyMatch(tag -> tag.path("title").asText().equals("permalink")));
        }
    }

    /** Distinguishes publication dates from release-table dates and retains actual global pagination order. */
    @Test
    void retainsPublishedOrderAndDate() {
        assertEquals(0, release("/blog/2018/06/13/arquillian-cube-1-16-0/").path("published_order").asInt(-1));
        assertEquals(1, release("/blog/2018/06/06/arquillian-extension-byteman-1-0-0/").path("published_order").asInt(-1));
        JsonNode warp = release("/blog/2013/01/15/arquillian-extension-warp-1-0-0-Alpha2/");
        assertEquals("2013-01-15", warp.path("published_date").asText());
        assertEquals("2013-01-08", warp.path("date").asText());
        assertEquals(431, warp.path("published_order").asInt(-1));
        assertEquals(430, release("/blog/2013/01/15/arquillian-extension-drone-1-2-0-Alpha1/").path("published_order").asInt(-1));
        assertEquals(432, release("/blog/2013/01/15/arquillian-extension-android-1-0-0-Alpha2/").path("published_order").asInt(-1));
        assertEquals(433, release("/blog/2013/01/15/arquillian-graphene-2-0-0-Alpha3/").path("published_order").asInt(-1));
        assertEquals(582, catalog.path("releases").size());
        assertEquals(582, catalog.path("releases").valueStream().map(row -> row.path("published_order").asInt(-1)).distinct().count());
        assertTrue(catalog.path("releases").valueStream().allMatch(row -> row.path("published_order").asInt(-1) >= 0
                && row.path("published_order").asInt(-1) < 606));
    }

    /** The reference-directory labels and summaries follow the authored HAML rather than the compact menu labels. */
    @Test
    void retainsExternalGuideIndexLabelsAndSummaries() {
        List<JsonNode> references = catalog.path("guides").valueStream().filter(row -> row.path("external").asBoolean())
                .sorted(java.util.Comparator.comparingInt(row -> row.path("menu_order").asInt())).toList();
        assertEquals(List.of("Reference Guide", "API & SPI Documentation", "Migration Guides", "FAQs"),
                references.stream().map(row -> row.path("index_title").asText()).toList());
        assertEquals(List.of("Reference information for the Arquillian project.",
                "Documentation for Java APIs and SPIs of Arquillian",
                "Instructions for migrating between versions of Arquillian.",
                "Knowledge base of frequently asked questions and helpful tips."),
                references.stream().map(row -> row.path("summary").asText()).toList());
    }

    /** Finds literal routes so test expectations remain independent of the extraction utility. */
    private static JsonNode release(String link) {
        return catalog.path("releases").valueStream().filter(row -> row.path("link").asText().equals(link)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing release " + link));
    }
}
