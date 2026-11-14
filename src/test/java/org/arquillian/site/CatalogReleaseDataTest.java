package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.arquillian.site.refresh.SnapshotStore;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Guards release-note data against accidental extraction of authored narrative definition lists. */
class CatalogReleaseDataTest {
    /** Holds the validated checked-in snapshot without depending on migration captures. */
    private static JsonNode catalog;

    /** Loads the same schema-validated snapshot used by refresh and native release rendering. */
    @BeforeAll
    static void loadCatalog() throws IOException {
        catalog = new SnapshotStore(Path.of("data/catalog.json")).read();
    }

    /** Keeps Chameleon's authored WildFly/GlassFish highlights out of its two actual release issues. */
    @Test
    void chameleonContainsOnlyPublishedReleaseIssues() {
        JsonNode release = row("/blog/2017/11/29/arquillian-container-chameleon-1-0-0-Beta3/");
        assertEquals(2, release.path("issues_count").asInt());
        assertEquals(List.of("Other"), release.path("resolved_issues").valueStream()
                .map(group -> group.path("title").asText()).toList());
        List<String> issues = release.path("resolved_issues").get(0).path("issues_html").valueStream().map(JsonNode::asText).toList();
        assertEquals(List.of(
                "<a href=\"https://github.com/arquillian/arquillian-container-chameleon/issues/77\">#77 Add support for Glassfish 5.x</a>",
                "<a href=\"https://github.com/arquillian/arquillian-container-chameleon/issues/76\">#76 Support WildFly 11</a>"), issues);
    }

    /** Every structured issue must have its tracker link, while historical published counts remain intact. */
    @Test
    void releaseGroupsContainTrackerIssuesRatherThanNarrativeLists() {
        assertEquals(582, catalog.path("releases").size());
        for (JsonNode release : catalog.path("releases")) {
            JsonNode groups = release.path("resolved_issues");
            String context = release.path("link").asText();
            if (release.path("issues_count").isNull()) assertTrue(groups.isEmpty(), context);
            for (JsonNode group : groups) {
                assertFalse(group.path("title").asText().isBlank(), context);
                assertFalse(group.path("issues_html").isEmpty(), context + ": empty issue group " + group.path("title"));
                for (JsonNode issue : group.path("issues_html")) {
                    Element link = Jsoup.parseBodyFragment(issue.asText()).selectFirst("a[href]");
                    assertTrue(link != null && !link.attr("href").isBlank(), context + ": issue without tracker link");
                }
            }
        }
    }

    /** Preserves the published opening and closing quotation marks through actual AsciiDoc conversion. */
    @Test
    void cubeOpeningQuotesSurviveAsciiDocConversion() throws IOException {
        String source = Files.readString(Path.of("content/blog/arquillian-cube-1.10.0.adoc"));
        String prose = source.substring(source.indexOf("\n---\n", 4) + 5);
        Asciidoctor asciidoc = Asciidoctor.Factory.create();
        try {
            String text = Jsoup.parseBodyFragment(asciidoc.convert(prose,
                    Options.builder().safe(SafeMode.SECURE).build())).text();
            assertEquals(5, text.split("OpenShift:“https://www.openshift.com/”", -1).length - 1);
            assertFalse(text.contains("OpenShift:”https://www.openshift.com/”"));
        } finally {
            asciidoc.shutdown();
        }
    }

    /** Finds an independently named historical release instead of reusing any extraction selector. */
    private static JsonNode row(String link) {
        return catalog.path("releases").valueStream().filter(row -> row.path("link").asText().equals(link))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + link));
    }
}
