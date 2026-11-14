package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.Engine;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Verifies the production homepage renders from local data without a collection step. */
@QuarkusTest
class SnapshotRenderingTest {

    @Inject
    Engine engine;

    /** Accesses real authored metadata so the homepage regression can change the latest article. */
    @Inject
    Site site;

    @Inject
    @Named("catalog")
    JsonObject catalog;

    @Inject
    SiteCatalog siteCatalog;

    /** Detects a missing homepage, shared navigation, or changed published news order and excerpts. */
    @Test
    void rendersProductionHomepage() {
        Document home = Jsoup.parse(given().when().get("/").then().statusCode(200).extract().asString());
        assertEquals("Arquillian · Write Real Tests", home.title());
        assertEquals(1, home.select("#main[role=main]").size());
        assertEquals(1, home.select("#banner[role=banner] nav[role=navigation]").size());
        assertEquals(3, home.select("#news > ul > li").size());
        assertEquals("/blog/2018/06/13/arquillian-cube-1-16-0/", home.selectFirst("#news h3 a").attr("href"));
        assertEquals("Arquillian Cube Extension 1.16.0 Released", home.selectFirst("#news h3 a").text());
        assertEquals("The Arquillian team is proud to announce the 1.16.0 release of the Arquillian Cube Extension component! What is Arquillian Cube Extension? With Arquillian Cube you can control the lifecycle of Docker images as part of the test lifecyle, either automatically or manually....", home.select("#news > ul > li p").get(0).text());
        assertEquals("The Arquillian team is proud to announce the 1.0.0 release of the Arquillian Extension Byteman component! What is Arquillian? Arquillian is open source software that empowers you to test JVM-based applications more effectively....", home.select("#news > ul > li p").get(1).text());
        assertEquals("Since we wrote this post we didn't laze around. Check our latest announcement....", home.select("#news > ul > li p").get(2).text());
        assertEquals(1, home.select("#slideshow .item.active .layout-photo").size());
        assertEquals(1, home.select("#spotlight li").size());
        assertEquals(1, home.select("#events > ul").size());
    }

    /** Ensures ordinary authored articles join the homepage news without altering published comment identities. */
    @Test
    void rendersOrdinaryPostInLatestNews() {
        Page article = site.page("blog/2010-03-10-testing-javaee-the-jboss-way.adoc");
        JsonObject metadata = article.data();
        String date = metadata.getString("date");
        try {
            metadata.put("date", "2099-01-01");
            Document home = Jsoup.parse(given().when().get("/").then().statusCode(200).extract().asString());
            assertEquals("/blog/2010/03/10/testing-javaee-the-jboss-way/", home.selectFirst("#news h3 a").attr("href"));
            assertEquals("Testing Java EE the JBoss way", home.selectFirst("#news h3 a").text());
            assertEquals("Posted by Pete Muir on Jan 01, 2099", home.selectFirst("#news img.avatar").attr("title"));
            assertTrue(home.selectFirst("#news > ul > li > p").text().startsWith("Recently, we’ve been working hard"));
        } finally {
            metadata.put("date", date);
        }
    }

    /** Keeps the homepage's three upcoming event cards tied to stored refresh decisions and session metadata. */
    @Test
    void rendersStoredUpcomingEvents() {
        JsonArray original = catalog.getJsonArray("events");
        JsonObject session = new JsonObject().put("id", "fixture:session").put("upcoming", true)
                .put("title", "First talk").put("detail_url", "https://events.example/talk")
                .put("event", "Example Conference").put("event_url", "https://events.example/")
                .put("event_location", "Chicago").put("start_date", "2027-05-09")
                .put("speakers", new JsonArray().add(new JsonObject().put("username", "alex").put("name", "Alex"))
                        .add(new JsonObject().put("username", "bea").put("name", "Bea")));
        JsonObject conference = session.copy().put("id", "fixture:conference")
                .put("title", "Conference-only talk").put("conference_start_date", "2027-06-01");
        conference.remove("start_date");
        JsonObject third = session.copy().put("id", "fixture:third").put("title", "Third talk");
        JsonObject fourth = session.copy().put("id", "fixture:fourth").put("title", "Fourth talk");
        JsonObject past = session.copy().put("id", "fixture:past").put("title", "Past talk")
                .put("upcoming", false);
        try {
            catalog.put("events", new JsonArray().add(past).add(session).add(conference).add(third).add(fourth));
            Document home = Jsoup.parse(given().when().get("/").then().statusCode(200).extract().asString());
            assertEquals(List.of("Example Conference", "Example Conference", "Example Conference"),
                    home.select("#events > ul > li h3 a").eachText());
            assertEquals(3, home.select("#events > ul > li").size());
            assertEquals("May09", home.select("#events > ul > li span.date").getFirst().text().replace(" ", ""));
            assertEquals("Jun", home.select("#events > ul > li span.date").get(1).text());
            assertEquals("https://events.example/", home.select("#events > ul > li h3 a").getFirst().attr("href"));
            assertEquals("Chicago", home.select("#events > ul > li em.location").getFirst().text());
            assertEquals("Alex and Bea will present:", home.select("#events > ul > li p").getFirst().ownText());
            assertEquals("First talk", home.select("#events > ul > li p a").getFirst().text());
            assertEquals(0, home.select("#events:contains(Past talk), #events:contains(Fourth talk)").size());
        } finally {
            catalog.put("events", original);
        }
    }

    /** Keeps literal-brace coverage after removing the temporary AsciiDoc homepage probe. */
    @Test
    void preservesLiteralBraces() {
        assertEquals("class Example { String value = \"{not_a_template}\"; }",
                engine.parse("{|class Example { String value = \"{not_a_template}\"; }|}").render());
    }

    /** Ensures layouts receive native catalog rows, including guide metadata rather than guide prose. */
    @Test
    void resolvesNativeCatalogData() {
        assertEquals(Set.of("repositories", "releases", "identities", "modules", "guides", "events"), catalog.fieldNames());
        JsonObject guide = siteCatalog.find("guides", "/guides/getting_started/");
        assertEquals("Getting Started", guide.getString("title"));
        assertEquals("en", guide.getString("language"));
        assertTrue(guide.getString("authors_html").contains("/community/mojavelinux"));
        assertEquals("assumptions", guide.getJsonArray("chapters").getJsonObject(0).getString("id"));
        assertTrue(guide.getString("footer_html").contains("Recent Changelog"));
        assertEquals(guide, siteCatalog.find("guides", guide.getString("id")));
        assertEquals("Arquillian Cube Extension 1.16.0 Released", siteCatalog.latestReleases(1).getFirst().getString("title"));
        assertEquals("Missing catalog row: guides/missing", assertThrows(IllegalArgumentException.class,
                () -> siteCatalog.find("guides", "missing")).getMessage());
        assertEquals("Unknown catalog domain: missing", assertThrows(IllegalArgumentException.class,
                () -> siteCatalog.find("missing", "row")).getMessage());
    }

    /** Catches reversed dates, unstable same-day ordering and accidental negative-limit acceptance. */
    @Test
    void sortsReleasesDeterministically() {
        SiteCatalog local = new SiteCatalog();
        local.catalog = new JsonObject().put("releases", new JsonArray()
                .add(new JsonObject().put("id", "b").put("date", "2018-06-13"))
                .add(new JsonObject().put("id", "old").put("date", "2017-12-31"))
                .add(new JsonObject().put("id", "a").put("date", "2018-06-13")));
        assertEquals(List.of("a", "b"), local.latestReleases(2).stream().map(row -> row.getString("id")).toList());
        assertTrue(local.latestReleases(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> local.latestReleases(-1));
    }

    /** Uses publication chronology and archived same-day order rather than a component's release date. */
    @Test
    void sortsAnnouncementsByPublicationDateAndOrder() {
        SiteCatalog local = new SiteCatalog();
        local.catalog = new JsonObject().put("releases", new JsonArray()
                .add(new JsonObject().put("id", "a").put("date", "2020-01-01")
                        .put("published_date", "2018-01-01").put("published_order", 8))
                .add(new JsonObject().put("id", "b").put("date", "2017-01-01")
                        .put("published_date", "2018-01-01").put("published_order", 7))
                .add(new JsonObject().put("id", "old").put("date", "2017-12-31")));
        assertEquals(List.of("b", "a", "old"),
                local.latestReleases(3).stream().map(row -> row.getString("id")).toList());
    }

    /** Prevents translated guides from duplicating canonical menu rows and keeps the published order. */
    @Test
    void groupsCanonicalGuidesInPublishedOrder() {
        SiteCatalog local = new SiteCatalog();
        local.catalog = new JsonObject().put("guides", new JsonArray()
                .add(new JsonObject().put("id", "later").put("menu_group", 1).put("menu_order", 2))
                .add(new JsonObject().put("id", "translated").put("baseline_link", "/guides/first/"))
                .add(new JsonObject().put("id", "other-group").put("menu_group", 2).put("menu_order", 0))
                .add(new JsonObject().put("id", "first").put("menu_group", 1).put("menu_order", 0)));
        assertEquals(List.of("first", "later"), local.guidesInGroup(1).stream().map(row -> row.getString("id")).toList());
        assertTrue(local.guidesInGroup(4).isEmpty());
        List<JsonObject> firstSteps = siteCatalog.guidesInGroup(1);
        assertEquals(List.of("Getting Started", "Getting Started: Rinse and Repeat", "Get Started Faster with Forge", "Creating Deployable Archives with ShrinkWrap"),
                firstSteps.stream().map(row -> row.getString("title")).toList());
        JsonObject english = firstSteps.getFirst();
        assertEquals("/guides/getting_started/", english.getString("baseline_link"));
        JsonObject german = english.getJsonArray("translations").getJsonObject(1);
        assertEquals("/guides/getting_started_de/", german.getString("link"));
        assertEquals("/images/flags/de.png", german.getString("flag_src"));
        assertEquals("Deutsch (de)", german.getString("flag_title"));
        assertFalse(siteCatalog.find("guides", "/guides/getting_started_de/").containsKey("menu_group"));
        assertEquals(List.of("Reference Guide", "API & SPI Docs", "Migration Guides", "FAQs"),
                siteCatalog.guidesInGroup(4).stream().map(row -> row.getString("title")).toList());
        assertEquals("https://docs.jboss.org/author/display/ARQ/Reference+Guide", siteCatalog.guidesInGroup(4).getFirst().getString("link"));
    }
}
