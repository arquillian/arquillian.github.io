package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/** Checks native data-generated modules and canonical community profiles through their public URLs. */
@QuarkusTest
class GeneratedPagesRenderingTest {
    @Inject
    @Named("generated")
    JsonArray generated;

    @Inject
    Site site;

    /** Protects the published hero, dependency coordinates and complete ordered Cube release history. */
    @Test
    void rendersCubeExtension() {
        Document document = page("/modules/cube-extension/");
        assertEquals("Arquillian Cube Extension · Arquillian", document.title());
        assertEquals("Arquillian Cube Extension", document.selectFirst("#content-header h1").text());
        assertEquals("Arquillian Cube Extension", document.selectFirst(".hero-unit h2").text());
        assertEquals("What is Arquillian Cube Extension?", document.selectFirst(".hero-unit h3").text());
        assertEquals("1.16.0", document.selectFirst(".hero-unit .breadcrumb strong.label").text());
        assertEquals("2018-06-13", document.selectFirst(".hero-unit .breadcrumb li:nth-child(2) strong").text());
        assertEquals("https://avatars.githubusercontent.com/u/1517153?s=48",
                document.selectFirst("#content > .row .thumbnail img").attr("src"));
        assertEquals("http://arquillian.org/arquillian-cube", document.selectFirst("#documentation a").attr("href"));
        assertTrue(document.selectFirst("#artifacts pre.prettify").wholeText().contains("<artifactId>arquillian-cube-bom</artifactId>"));
        assertTrue(document.selectFirst("#artifacts pre.prettify").wholeText().contains("<scope>import</scope>"));
        assertTrue(document.selectFirst("#artifacts").text().contains("org.arquillian.cube:arquillian-cube-api:jar:1.16.0"));
        assertEquals("https://github.com/arquillian/arquillian-cube",
                document.selectFirst("#source-repo a[href='https://github.com/arquillian/arquillian-cube']").attr("href"));
        assertReleaseHistory(document, 47, "1.16.0", "2018-06-13", "Dipak Pawar");
        assertEquals("/blog/2018/06/13/arquillian-cube-1-16-0/",
                document.selectFirst("#releases tbody tr td:nth-child(2) a").attr("href"));
        assertTrue(document.selectFirst("#releases tbody tr td:nth-child(5)").text().contains("Arquillian Core – 1.4.0.Final"));
    }

    /** Guards adapter-specific coordinates, type, release count and the newest unreleased-news version. */
    @Test
    void rendersManagedContainerAdapter() {
        Document document = page("/modules/wildfly-arquillian-wildfly-managed-container-adapter/");
        assertEquals("Arquillian WildFly Managed Container Adapter · Arquillian", document.title());
        assertEquals("Container Adapter", document.selectFirst(".hero-unit h2 small").text());
        assertTrue(document.selectFirst("#artifacts pre.prettify").wholeText()
                .contains("<artifactId>wildfly-arquillian-container-managed</artifactId>"));
        assertTrue(document.selectFirst("#artifacts pre.prettify").wholeText().contains("<version>2.1.1.Final</version>"));
        assertReleaseHistory(document, 23, "2.1.1.Final", "2018-06-19", "Stuart Douglas");
        assertTrue(document.selectFirst("#releases tbody tr td:nth-child(2) a").attr("href")
                .startsWith("https://github.com/wildfly/wildfly-arquillian/commit/"));
    }

    /** Keeps the profile title, avatar choice, biography, social links and translator role intact. */
    @Test
    void rendersHantsyProfile() {
        Document document = page("/community/hantsy/");
        assertEquals("Community member: Hantsy Bai · Arquillian", document.title());
        assertEquals("Hantsy Bai", document.selectFirst("#content-header h1").text());
        Element avatar = document.selectFirst(".hero-unit .pull-left img");
        assertEquals("https://avatars.githubusercontent.com/u/854520?s=120", avatar.attr("src"));
        assertEquals("Hantsy Bai", avatar.attr("alt"));
        assertEquals("120", avatar.attr("width"));
        assertEquals("Freelancer China", document.selectFirst(".hero-unit h2").text());
        assertEquals("Coding for food", document.selectFirst(".hero-unit .bio").text());
        assertEquals(List.of("GitHub", "Blog"), document.select("#content .well dt").eachText());
        assertEquals(List.of("https://github.com/hantsy", "http://hantsy.blogspot.com"),
                document.select("#content .well dd a").eachAttr("href"));
        assertEquals("translator", document.selectFirst("#content .greenbar").text());
        assertEquals("/community/", document.selectFirst("#banner li.active a").attr("href"));
    }

    /** Detects missing generated data or accidental inclusion of directory rows before their own phase. */
    @Test
    void loadsAllCanonicalGeneratedPages() {
        assertEquals(480, generated.size());
        RoqCollection pages = site.collections().get("generated");
        assertNotNull(pages, "Native generated collection must be activated");
        assertEquals(480, pages.size());
        assertEquals(122, pages.stream().filter(page -> page.data("domain").equals("modules")).count());
        assertEquals(358, pages.stream().filter(page -> page.data("domain").equals("identities")).count());
        assertEquals(generated.stream().map(JsonObject.class::cast).map(row -> row.getString("link"))
                        .collect(Collectors.toSet()),
                pages.stream().map(page -> page.url().path()).collect(Collectors.toSet()));
    }

    /** Fetches a generated page and verifies that the shared ordinary-page landmark shell is present. */
    private static Document page(String path) {
        Document document = Jsoup.parse(given().when().get(path).then().statusCode(200).extract().asString());
        assertEquals(1, document.select("#main[role=main] > #content-header > .container").size());
        assertEquals(1, document.select("#main > .container > #content").size());
        return document;
    }

    /** Checks published table hooks and descending historical ordinals rather than inferring missing news. */
    private static void assertReleaseHistory(Document document, int count, String version, String date, String author) {
        assertEquals(Integer.toString(count), document.selectFirst("#releases .page-header small").text());
        assertEquals(List.of("#", "Version", "Release date", "Released by", "Compiled against"),
                document.select("#releases table.table-bordered.table-striped thead th").eachText());
        assertEquals(count, document.select("#releases tbody tr").size());
        assertEquals(IntStream.range(0, count).mapToObj(index -> Integer.toString(count - index)).toList(),
                document.select("#releases tbody tr td:first-child").eachText());
        List<Element> first = document.selectFirst("#releases tbody tr").children();
        assertEquals(Integer.toString(count), first.get(0).text());
        assertTrue(first.get(1).text().startsWith(version));
        assertEquals(date, first.get(2).text());
        assertEquals(author, first.get(3).text());
        assertEquals("1", document.select("#releases tbody tr").getLast().child(0).text());
    }
}
