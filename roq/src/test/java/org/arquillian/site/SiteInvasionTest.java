package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.qute.Engine;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.ValueResolver;
import io.quarkus.qute.HtmlEscaper;
import io.quarkus.qute.Variant;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Protects canonical team membership and future event views without clocks, networks or captured-page fixtures. */
@QuarkusTest
class SiteInvasionTest {
    @Inject
    @Named("catalog")
    JsonObject runtimeData;

    /** Explicit canonical ranks retain advisors and distinguish the published contributor occurrences from accounts. */
    @Test
    void preservesTeamAndRoadieMembership() throws IOException {
        SiteInvasion invasion = invasion();
        assertEquals(List.of("aslakknutsen", "bartoszmajsak", "lordofthejars", "matousjobanek", "smiklosovic",
                "kpiwko", "lfryc", "mojavelinux", "graphitefriction", "alrubinger", "pmuir"),
                invasion.team().stream().map(row -> row.getJsonObject("identity").getString("github_id")).toList());
        assertEquals(List.of("aslakknutsen", "mojavelinux", "kpiwko", "bartoszmajsak", "lfryc"),
                invasion.roadies().stream().map(row -> row.getJsonObject("identity").getString("github_id")).toList());
        assertEquals(357, invasion.contributorCount());
        assertTrue(invasion.events().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> invasion.team().clear());
    }

    /** Actual editable prose and dynamic layouts retain every published team, modal and empty-calendar hook. */
    @Test
    void preservesPublishedTemplates() throws IOException {
        SiteInvasion invasion = invasion();
        Document team = render(invasion, "team");
        assertEquals(14, team.select("ul.team > li").size());
        assertEquals(11, team.select(".modal[id]").size());
        assertEquals("357 Contributors", team.select("ul.team > li > strong").get(11).text());
        assertEquals(4, team.select(".modal a[href^='https://plus.google.com/']").size());
        assertEquals(22, team.select("img[height=60][width=60]").size());
        assertEquals("Every contribution matters. Thanks for being part of the team!", team.selectFirst("p.rally").text());
        Document events = render(invasion, "events");
        assertTrue(events.selectFirst("p").text().startsWith("We've found 0 upcoming sessions"));
        assertTrue(events.selectFirst("p").text().contains("that's not shown here"));
        assertEquals("0", events.selectFirst("p > strong").text());
        assertEquals(0, events.select("#sessions > .session").size());
        assertEquals(5, events.select(".lanyrd-target-splat").size());
        assertEquals(List.of("webcal://arquillian.org/invasion/events/arquillian.ics",
                "http://www.google.com/calendar/render?cid=http%3A//arquillian.org/invasion/events/arquillian.ics", "arquillian.ics"),
                events.select("p").get(1).select("a").eachAttr("href"));
    }

    /** Stored flags decide visibility while date precision, timezone and missing ends remain intact. */
    @Test
    void rendersTimedDateOnlyAndConferenceListings() throws IOException {
        SiteInvasion invasion = invasion();
        invasion.data.put("events", new JsonArray().add(event("timed").put("start_datetime", "2035-04-05T15:30:00Z")
                .put("end_datetime", "2035-04-05T16:30:00Z").put("timezone", "Europe/Prague"))
                .add(event("date").put("start_date", "2035-05-06").put("end_date", "2035-05-07"))
                .add(event("conference").put("conference_start_date", "2035-06-07"))
                .add(event("missing-end").put("start_datetime", "2035-07-08T10:00:00Z"))
                .add(event("past").put("upcoming", false).put("start_date", "2035-08-09"))
                .add(event("historic-flag").put("start_date", "2001-01-01"))
                .add(event("unreviewed").put("upcoming", null).put("start_date", "2099-01-01")));
        String before = invasion.data.encode();
        assertEquals(List.of("timed", "date", "conference", "missing-end", "historic-flag"),
                invasion.events().stream().map(row -> row.getString("id")).toList());
        Document events = render(invasion, "events");
        assertEquals("5", events.selectFirst("p > strong").text());
        assertTrue(events.select(".meta").get(0).text().contains("Thu, Apr 05, 2035 - 05:30PM to 06:30PM Europe/Prague"));
        assertTrue(events.select(".meta").get(1).text().contains("Sun, May 06, 2035"));
        assertTrue(events.select(".meta").get(2).text().contains("Thu, Jun 07, 2035"));
        assertFalse(events.select(".meta").get(2).text().contains("AM"));
        assertFalse(events.select(".meta").get(3).text().contains(" to "));
        assertEquals("Apr", invasion.events().getFirst().getString("month"));
        assertEquals("05", invasion.events().getFirst().getString("day"));
        assertEquals("Speaker One and Speaker Two", invasion.events().getFirst().getString("speaker_label"));
        assertEquals(before, invasion.data.encode());
        assertFalse(events.selectFirst(".body").html().contains("<script>"));
        assertEquals("<script>alert(1)</script> & details", events.selectFirst(".body").text());
    }

    /** Unsafe URL schemes, malformed dates and duplicate ranks cannot become browser links or ambiguous cards. */
    @Test
    void rejectsUnsafeAndIncompleteViewData() throws IOException {
        SiteInvasion invasion = invasion();
        invasion.data.put("events", new JsonArray().add(event("unsafe").put("start_date", "2035-01-01")
                .put("detail_url", "javascript:alert(1)")));
        assertThrows(IllegalArgumentException.class, invasion::events);
        invasion.data.put("events", new JsonArray().add(event("broken").put("start_date", "bad-date")));
        assertThrows(IllegalArgumentException.class, invasion::events);
        invasion.data.put("events", new JsonArray().add(event("undated")));
        assertThrows(IllegalArgumentException.class, invasion::events);
        JsonObject second = invasion.team().get(1).getJsonObject("identity");
        second.put("team_order", 0);
        assertThrows(IllegalArgumentException.class, invasion::team);
    }

    /** Shared biography normalization and canonical facts feed actual templates without mutating the source snapshot. */
    @Test
    void reflectsCanonicalProfileAndAuthoredProseChanges() throws IOException {
        SiteInvasion invasion = invasion();
        JsonObject identity = invasion.team().getFirst().getJsonObject("identity");
        identity.put("name", "Changed profile").put("role", "Changed role").put("bio_html", "First paragraph\n\nSecond paragraph");
        identity.getJsonArray("profiles").getJsonObject(0).getJsonArray("links").getJsonObject(0).put("href", "https://github.com/changed");
        String before = invasion.data.encode();
        Document team = render(invasion, "team");
        assertEquals("Changed profile", team.selectFirst("ul.team > li > h3").text());
        assertEquals("Changed role", team.selectFirst("ul.team > li > strong").text());
        assertEquals("https://github.com/changed", team.selectFirst("ul.team > li > a").attr("href"));
        assertEquals(List.of("First paragraph", "Second paragraph"), team.select("#aslakknutsen .modal-body > p").eachText());
        assertEquals("<p>One</p>\n<p>Two</p>", invasion.directories.biography("One\n\nTwo"));
        assertEquals("<p>Rich <em>text</em></p>", invasion.directories.biography("<p>Rich <em>text</em></p>"));
        assertEquals(before, invasion.data.encode());
        String authored = Files.readString(Path.of("content/invasion/team.adoc"));
        assertTrue(authored.contains("Meet the team leading the invasion"));
        assertFalse(authored.contains("<p>") || authored.contains("siteInvasion"));
        assertEquals("<p>Edited prose</p>", invasion.prose("<div class='openblock introduction'><div class='content'><p>Edited prose</p></div></div>", "introduction"));
    }

    /** Native routes retain published chrome, modal targets, calendar links and the legacy badge loading order. */
    @Test
    void servesNativeInvasionRoutes() {
        Document team = http("/invasion/team/");
        assertEquals("Team · Arquillian", team.title());
        assertEquals(11, team.select("#content .modal[id]").size());
        assertEquals(1, team.select("#tab > li.active > a[href='/invasion/team']").size());
        Document events = http("/invasion/events/");
        assertEquals("Events · Arquillian", events.title());
        assertEquals(5, events.select(".lanyrd-target-splat").size());
        List<String> scripts = events.select("script[src]").eachAttr("src");
        assertTrue(scripts.indexOf("http://cdn.lanyrd.net/badges/person-v1.min.js") > scripts.indexOf("/javascripts/site.js?b716a0a39091a141161c91c10c906872383b40c9"));
    }

    /** Permanent HTTP mutation proves profiles and new future sessions reach native routes, restoring the catalog even on failure. */
    @Test
    void rendersCanonicalMutationsThroughHttp() {
        JsonObject identity = runtimeData.getJsonArray("identities").stream().map(JsonObject.class::cast)
                .filter(row -> "aslakknutsen".equals(row.getString("github_id"))).findFirst().orElseThrow();
        String name = identity.getString("name");
        String biography = identity.getString("bio_html");
        JsonArray events = runtimeData.getJsonArray("events");
        try {
            identity.put("name", "Native changed team").put("bio_html", "Native changed biography");
            runtimeData.put("events", new JsonArray().add(event("native").put("start_date", "2035-01-02"))
                    .add(event("timed-native").put("start_datetime", "2035-04-05T15:30:00Z").put("timezone", "Europe/Prague"))
                    .add(event("conference-native").put("conference_start_date", "2035-06-07"))
                    .add(event("past").put("upcoming", false).put("start_date", "2000-01-01")));
            Document team = http("/invasion/team/");
            assertEquals("Native changed team", team.selectFirst("ul.team > li > h3").text());
            assertEquals("Native changed biography", team.selectFirst("#aslakknutsen .modal-body > p").text());
            Document page = http("/invasion/events/");
            assertEquals("3", page.selectFirst("#content > p > strong").text());
            assertEquals(3, page.select("#sessions .session").size());
            assertTrue(page.select("#sessions .meta").get(1).text().contains("05:30PM Europe/Prague"));
            assertTrue(page.select("#sessions .meta").get(2).text().contains("Thu, Jun 07, 2035"));
            assertTrue(page.select("#sessions .body script").isEmpty());
            assertEquals("Session native", page.selectFirst("#sessions .title a").text());
            assertEquals("https://lanyrd.example/native", page.selectFirst("#sessions .title a").attr("href"));
        } finally {
            identity.put("name", name).put("bio_html", biography);
            runtimeData.put("events", events);
        }
    }

    /** Supplies plain untrusted descriptions and two names to exercise escaping and stable speaker links. */
    private static JsonObject event(String id) {
        return new JsonObject().put("id", id).put("upcoming", true).put("title", "Session " + id)
                .put("detail_url", "https://lanyrd.example/" + id).put("event", "Conference " + id)
                .put("event_url", "https://conference.example/" + id).put("event_location", "Brno, Czech Republic")
                .put("description", "<script>alert(1)</script> & details")
                .put("speakers", new JsonArray().add(new JsonObject().put("username", "one").put("name", "Speaker One"))
                        .add(new JsonObject().put("username", "two").put("name", "Speaker Two")));
    }

    /** Gives each pure check its own canonical source and shared biography bean. */
    private static SiteInvasion invasion() throws IOException {
        SiteInvasion invasion = new SiteInvasion();
        invasion.data = new JsonObject(Files.readString(Path.of("data/catalog.json")));
        invasion.directories = new SiteDirectories();
        invasion.directories.data = invasion.data;
        invasion.markup = new LegacyMarkup();
        return invasion;
    }

    /** Renders actual authored AsciiDoc and Qute layouts through the same escaping rules as native HTML. */
    private static Document render(SiteInvasion invasion, String name) throws IOException {
        Engine engine = Engine.builder().configureParser((id, variant) -> new ParserConfig('='))
                .addDefaults().addValueResolver(io.quarkus.qute.ValueResolvers.rawResolver()).addValueResolver(new ReflectionValueResolver())
                .addResultMapper(new HtmlEscaper(List.of("text/html")))
                .addValueResolver(ValueResolver.builder().appliesTo(context -> context.getBase() instanceof JsonObject)
                        .resolveSync(context -> ((JsonObject) context.getBase()).getValue(context.getName())).build())
                .addNamespaceResolver(NamespaceResolver.builder("cdi").resolve(context -> invasion).build()).build();
        String source = Files.readString(Path.of("content/invasion/" + name + ".adoc")).split("---", 3)[2];
        Asciidoctor renderer = Asciidoctor.Factory.create();
        try {
            String content = renderer.convert(source, Options.builder().safe(SafeMode.SECURE).build());
            String layout = Files.readString(Path.of("templates/layouts/" + name + ".html")).split("---", 3)[2];
            return Jsoup.parse(engine.parse(layout, new Variant(java.util.Locale.US, java.nio.charset.StandardCharsets.UTF_8, "text/html"), name)
                    .data("page", new JsonObject().put("content", content)).render());
        } finally {
            renderer.shutdown();
        }
    }

    /** Fetches an actual discovered Roq route instead of rendering a substitute fragment. */
    private static Document http(String path) {
        return Jsoup.parse(RestAssured.get(path).then().statusCode(200).extract().asString());
    }
}
