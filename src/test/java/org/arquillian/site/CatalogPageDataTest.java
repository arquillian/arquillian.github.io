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

/** Guards the captured page fields that native generated layouts need beyond the homepage snapshot. */
class CatalogPageDataTest {
    private static JsonNode catalog;

    /** Validates the enriched snapshot through the actual refresh-store boundary before checking page fields. */
    @BeforeAll
    static void loadCatalog() throws IOException {
        catalog = new SnapshotStore(Path.of("data/catalog.json")).read();
    }

    /** Protects the hero heading, lead avatar, source badges and latest activity from incomplete extraction. */
    @Test
    void retainsModuleHeroAndRepositoryMetadata() {
        JsonNode cube = row("modules", "/modules/cube-extension/");
        assertTrue(cube.path("hero_blocks").valueStream().anyMatch(block -> block.path("tag").asText().equals("h3")
                && block.path("html").asText().equals("What is Arquillian Cube Extension?")));
        assertEquals("Alex Soto", cube.path("lead").path("name").asText());
        assertEquals("/community/lordofthejars", cube.path("lead").path("link").asText());
        assertEquals("https://avatars.githubusercontent.com/u/1517153?s=48", cube.path("lead").path("avatar_url").asText());
        assertEquals("http://arquillian.org/arquillian-cube", cube.path("documentation").path("href").asText());
        assertEquals(2, cube.path("repository_badges").size());
        assertEquals(2, cube.path("activity").path("unreleased_commits").asInt());
        assertEquals("fix: close all cubes, even on error (#1059)", cube.path("activity").path("message").asText());
        assertEquals("Andy Gumbrecht", cube.path("activity").path("author").asText());
        assertEquals("2018-07-03", cube.path("activity").path("date").asText());
    }

    /** Keeps build snippets as editable text and artifact coordinates separate from link-only metadata. */
    @Test
    void retainsCoordinatesAndContributors() {
        JsonNode cube = row("modules", "/modules/cube-extension/");
        assertTrue(cube.path("artifact_blocks").valueStream().anyMatch(block -> block.path("kind").asText().equals("coordinates")
                && block.path("text").asText().contains("<artifactId>arquillian-cube-bom</artifactId>")
                && block.path("text").asText().contains("<scope>import</scope>")));
        JsonNode published = cube.path("artifact_blocks").valueStream()
                .filter(block -> block.path("kind").asText().equals("published")).findFirst().orElseThrow();
        assertEquals("org.arquillian.cube:arquillian-cube-api:jar:1.16.0", published.path("items").get(0).path("coordinates").asText());
        assertEquals(List.of("jar", "javadoc", "pom"), published.path("items").get(0).path("links").valueStream()
                .map(link -> link.path("title").asText()).toList());
        assertTrue(cube.path("contributors").size() > 10);
        JsonNode adapter = row("modules", "/modules/wildfly-arquillian-wildfly-managed-container-adapter/");
        assertTrue(adapter.path("artifact_blocks").valueStream().anyMatch(block -> block.path("text").asText()
                .contains("<artifactId>wildfly-arquillian-container-managed</artifactId>")));
    }

    /** Public roles and team badges must survive even when the profile's social fields already exist. */
    @Test
    void retainsProfileRolesAndTeams() {
        JsonNode hantsy = row("identities", "/community/hantsy/");
        assertEquals(List.of("translator"), hantsy.path("teams").valueStream().map(JsonNode::asText).toList());
        assertTrue(row("identities", "/community/aslakknutsen/").path("role").asText().contains("Lead"));
        assertEquals(122, catalog.path("modules").size());
        assertEquals(358, catalog.path("identities").size());
        assertEquals(1351, catalog.path("modules").valueStream().mapToInt(module -> module.path("release_history").size()).sum());
    }

    /** The published but unlisted Style Lab guide gets attribution and fragments without a fabricated menu row. */
    @Test
    void includesUnlistedStyleLabGuide() {
        JsonNode style = row("guides", "/guides/style_lab/");
        assertEquals("published:guide:/guides/style_lab/", style.path("id").asText());
        assertEquals("https://arquillian.org/guides/style_lab/", style.path("source_url").asText());
        assertEquals("8dbcd3e756119ea33ad76e375f84943111190c731af8631a86a2a99f451bbe8e", style.path("source_sha256").asText());
        assertEquals("Style Lab", style.path("title").asText());
        assertEquals("en", style.path("language").asText());
        assertTrue(style.path("authors_html").asText().contains("Dan Allen"));
        assertEquals(List.of("step_1", "step_2", "share"), style.path("chapters").valueStream()
                .map(chapter -> chapter.path("id").asText()).toList());
        assertTrue(style.path("footer_html").asText().contains("Recent Changelog"));
        assertFalse(style.has("menu_group"));
        assertEquals(45, catalog.path("guides").size());
    }

    /** Finds a literal historical path so expectations never depend on extraction implementation helpers. */
    private static JsonNode row(String domain, String link) {
        return catalog.path(domain).valueStream().filter(row -> row.path("link").asText().equals(link))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + link));
    }
}
