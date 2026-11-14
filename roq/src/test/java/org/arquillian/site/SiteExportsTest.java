package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/** Protects public feed identities and offline calendar/text exports against native rendering regressions. */
@QuarkusTest
class SiteExportsTest {
    /** Allows feed expectations to follow authored metadata without a second post catalog. */
    @Inject
    SiteBlog blog;

    /** Accesses native source pages for publication-date edit regressions. */
    @Inject
    Site site;

    /** Lets HTTP calendar tests restore the authoritative snapshot after each temporary edit. */
    @Inject
    @Named("catalog")
    JsonObject catalog;

    /** Detects XML double escaping, identity churn, lost duplicate tags and damaged literal examples. */
    @Test
    void serializesFeedWithoutEscapingTwice() throws Exception {
        SiteExports local = new SiteExports();
        local.data = new JsonObject().put("project", new JsonObject().put("title", "Arquillian").put("legacyBaseUrl", "http://arquillian.org"));
        local.blog = new SiteBlog() {
            /** Supplies controlled public metadata while exercising the real XML renderer. */
            @Override
            public List<JsonObject> latest(int limit) {
                assertEquals(50, limit);
                return List.of(new JsonObject().put("link", "/blog/2000/01/01/stable/").put("date", "2099-01-01")
                        .put("title", "XML & literal <tags>").put("tags", new JsonArray()
                                .add(new JsonObject().put("title", "one")) .add(new JsonObject().put("title", "one"))));
            }

            /** Keeps root links, absolute links, entities and template braces in the representative shared body. */
            @Override
            public String body(String link) {
                return "<p>Body &amp; words <a href=\"/root\">root</a><img src=\"https://images.example/a\"></p>"
                        + "<pre><code>{=literal} &lt;tag&gt; &amp; data</code></pre>";
            }
        };
        String value = local.atom();
        assertTrue(value.startsWith("<?xml"));
        Document xml = xml(value);
        assertEquals("http://www.w3.org/2005/Atom", xml.getDocumentElement().getNamespaceURI());
        assertEquals("en-US", xml.getDocumentElement().getAttributeNS(XMLConstants.XML_NS_URI, "lang"));
        assertEquals("Arquillian", xml.getElementsByTagNameNS("*", "author").item(0).getTextContent());
        assertEquals("http://arquillian.org/blog/2000/01/01/stable/", xml.getElementsByTagNameNS("*", "entry").item(0).getFirstChild().getTextContent());
        assertEquals("2099-01-02T00:00:00+00:00", xml.getElementsByTagNameNS("*", "updated").item(1).getTextContent());
        assertEquals("XML & literal <tags>", xml.getElementsByTagNameNS("*", "title").item(1).getTextContent());
        assertEquals(3, xml.getElementsByTagNameNS("*", "category").getLength());
        String body = xml.getElementsByTagNameNS("*", "content").item(0).getTextContent();
        assertTrue(body.contains("href=\"http://arquillian.org/root\""));
        assertTrue(body.contains("src=\"https://images.example/a\""));
        assertTrue(body.contains("{=literal} &lt;tag&gt; &amp; data"));
        assertEquals(0, xml.getElementsByTagNameNS("*", "published").getLength());
        local.data.getJsonObject("project").put("title", "broken\u0000title");
        assertThrows(IllegalArgumentException.class, local::atom);
    }

    /** Preserves the literal-space legacy budget, including empty fields and embedded newlines. */
    @Test
    void summarizesLegacyWhitespace() {
        assertEquals("alpha  beta\ngamma delta......", SiteExports.summary("<p>alpha &nbsp;beta\ngamma delta</p>"));
        assertEquals("word ".repeat(99) + "word......", SiteExports.summary("word ".repeat(101)));
        assertEquals("<extension> & words......", SiteExports.summary("<pre>&lt;extension&gt; &amp; words</pre>"));
        assertEquals("Heading?\nParagraph.\n  code\n x......", SiteExports.summary("<h3>Heading?</h3><p>Paragraph.</p><pre>  code\n x</pre>"));
    }

    /** Catches moving clocks, changed empty-calendar bytes, inclusive ends and broken UTF-8 line folding. */
    @Test
    void formatsSnapshotCalendar() {
        SiteExports local = calendar();
        assertEquals("BEGIN:VCALENDAR\nPRODID;X-RICAL-TZSOURCE=TZINFO:-//com.denhaven2/NONSGML ri_cal gem//EN\nCALSCALE:GREGORIAN\nVERSION:2.0\nEND:VCALENDAR\n", local.calendar());
        local.catalog.getJsonArray("events").add(event().put("title", "é漢字😀".repeat(30))
                .put("description", "slash\\ comma, semi;\nnext").put("speakers", new JsonArray().add(new JsonObject().put("name", "Zoë, Example"))));
        String actual = local.calendar();
        assertEquals(actual, local.calendar());
        String unfolded = actual.replace("\r\n ", "");
        assertTrue(unfolded.contains("UID:stable@arquillian.org\r\n"));
        assertTrue(unfolded.contains("DTSTAMP:20000102T030405Z\r\n"));
        assertTrue(unfolded.contains("DTSTART:20010102T100000Z\r\nDTEND:20010102T110000Z\r\n"));
        assertTrue(unfolded.contains("DESCRIPTION:slash\\\\ comma\\, semi\\;\\nnext\r\n"));
        assertTrue(unfolded.contains("ATTENDEE;CN=\"Zoë, Example\":"));
        for (String line : actual.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
            assertFalse(line.contains("\uFFFD"));
        }
        assertFalse(actual.replace("\r\n", "").contains("\n"));
        local.catalog.put("events", new JsonArray().add(event().put("upcoming", false))
                .add(event().put("id", "conference").put("start_datetime", null).put("end_datetime", null).put("conference_start_date", "2099-01-01"))
                .add(event().put("start_datetime", null).put("end_datetime", null).put("start_date", "2001-01-02").put("end_date", "2001-01-03")));
        unfolded = local.calendar().replace("\r\n ", "");
        assertEquals(1, unfolded.split("BEGIN:VEVENT", -1).length - 1);
        assertTrue(unfolded.contains("DTSTART;VALUE=DATE:20010102\r\nDTEND;VALUE=DATE:20010104\r\n"));
    }

    /** Invalid time ranges and URI/control injection must fail before an invalid calendar is published. */
    @Test
    void rejectsMalformedCalendar() {
        for (JsonObject invalid : List.of(event().put("end_datetime", "2001-01-02T09:59:59Z"),
                event().put("end_datetime", "2001-01-02T10:00:00Z"), event().put("start_datetime", "wrong"),
                event().put("detail_url", "https://example.org/\r\nBEGIN:VEVENT"),
                event().put("event_url", "https://example.org/\nATTENDEE:bad"),
                event().put("updated", "bad"), event().put("start_date", "2001-01-02"),
                event().put("start_datetime", null).put("end_datetime", null).put("start_date", "2001-01-03").put("end_date", "2001-01-02"))) {
            SiteExports local = calendar();
            local.catalog.getJsonArray("events").add(invalid);
            assertThrows(IllegalArgumentException.class, local::calendar, invalid.toString());
        }
    }

    /** Rejects forbidden C0/DEL characters in calendar text and names while retaining legal tabs and line breaks. */
    @Test
    void rejectsForbiddenCalendarTextControls() {
        for (JsonObject invalid : List.of(event().put("title", "bad\u0000title"),
                event().put("description", "bad\u0001description"), event().put("uid", "bad\u007fuid"),
                event().put("event", "bad\u0002location"),
                event().put("speakers", new JsonArray().add(new JsonObject().put("name", "bad\u0000name"))))) {
            SiteExports local = calendar();
            local.catalog.getJsonArray("events").add(invalid);
            assertThrows(IllegalArgumentException.class, local::calendar, invalid.toString());
        }
        SiteExports local = calendar();
        local.catalog.getJsonArray("events").add(event().put("title", "valid\tline\r\nnext")
                .put("speakers", new JsonArray().add(new JsonObject().put("name", "Ada\r\nLovelace"))));
        String calendar = local.calendar().replace("\r\n ", "");
        assertTrue(calendar.contains("SUMMARY:valid\tline\\nnext\r\n"));
        assertTrue(calendar.contains("ATTENDEE;CN=\"Ada^nLovelace\":"));
    }

    /** Protects native route MIME types and proves a publication edit cannot change an entry's public identity. */
    @Test
    void rendersMutableNativeFeed() throws Exception {
        Document feed = xml(given().get("/blog/atom.xml").then().statusCode(200).contentType("application/xml").extract().asString());
        assertEquals(50, feed.getElementsByTagNameNS("*", "entry").getLength());
        List<JsonObject> posts = blog.latest(50);
        for (int index = 0; index < posts.size(); index++) {
            org.w3c.dom.Element entry = (org.w3c.dom.Element) feed.getElementsByTagNameNS("*", "entry").item(index);
            assertEquals("http://arquillian.org" + posts.get(index).getString("link"), entry.getElementsByTagNameNS("*", "id").item(0).getTextContent());
            assertEquals(posts.get(index).getString("title"), entry.getElementsByTagNameNS("*", "title").item(0).getTextContent());
        }
        assertEquals("http://arquillian.org/blog/2018/06/13/arquillian-cube-1-16-0/", feed.getElementsByTagNameNS("*", "entry").item(0).getFirstChild().getTextContent());
        Page article = site.page("blog/2010-03-10-testing-javaee-the-jboss-way.adoc");
        String date = article.data().getString("date");
        try {
            article.data().put("date", "2099-01-01");
            Document edited = xml(given().get("/blog/atom.xml").then().statusCode(200).extract().asString());
            assertEquals("http://arquillian.org/blog/2010/03/10/testing-javaee-the-jboss-way/", edited.getElementsByTagNameNS("*", "entry").item(0).getFirstChild().getTextContent());
            assertEquals("2099-01-02T00:00:00+00:00", edited.getElementsByTagNameNS("*", "updated").item(1).getTextContent());
            assertTrue(edited.getElementsByTagNameNS("*", "content").item(0).getTextContent().contains("Recently"));
        } finally {
            article.data().put("date", date);
        }
    }

    /** Exercises the native calendar route with local snapshot edits and guarantees restoration even on failure. */
    @Test
    void rendersCalendarAndRestoresSnapshot() {
        JsonArray original = catalog.getJsonArray("events");
        try {
            catalog.put("events", new JsonArray().add(event()));
            String calendar = given().get("/invasion/events/arquillian.ics").then().statusCode(200).contentType("text/calendar").extract().asString();
            assertTrue(calendar.contains("DTSTART:20010102T100000Z\r\n"));
        } finally {
            catalog.put("events", original);
        }
    }

    /** Asserts exact legacy API and static asset bytes so template parsing cannot damage the client dashboard. */
    @Test
    void preservesAncillaryResponses() throws Exception {
        String expected = "nodePath=/arquillian\nprojectName=Arquillian\nhomePage=http://arquillian.org\nlicense=ASL\n"
                + "description=An innovative testing platform for the Java Virtual Machine (JVM). Open source. Highly extensible.\n"
                + "docsLink=http://arquillian.org/guides\nknowledgeBaseLink=https://docs.jboss.org/author/display/ARQ/Reference+Guide\n"
                + "userForumLink=http://community.jboss.org/en/arquillian?view=discussions\n"
                + "devForumLink=http://community.jboss.org/en/arquillian/dev?view=discussions\n"
                + "blogLink=http://arquillian.org/blog\ngithubLink=https://github.com/arquillian\nissueTrackerLink=https://issues.jboss.org/browse/ARQ\n";
        assertEquals(expected, given().get("/api/jbossorg/arquillian.properties").then().statusCode(200).contentType("text/plain").extract().asString());
        assertEquals("google-site-verification: google8ad3dd04249bcf67.html\n", given().get("/google8ad3dd04249bcf67.html").then().statusCode(200).extract().asString());
        assertArrayEquals(Files.readAllBytes(Path.of("../code/dashboard/index.html")), given().get("/code/dashboard/index.html").then().statusCode(200).extract().asByteArray());
    }

    /** Builds a fully dated stable snapshot without consulting the test machine's clock. */
    private static JsonObject event() {
        return new JsonObject().put("id", "stable").put("uid", "stable@arquillian.org").put("upcoming", true)
                .put("title", "Session").put("updated", "2000-01-02T03:04:05Z")
                .put("start_datetime", "2001-01-02T11:00:00+01:00").put("end_datetime", "2001-01-02T12:00:00+01:00")
                .put("detail_url", "https://example.org/session/").put("event", "Conference").put("event_url", "https://example.org/");
    }

    /** Creates an isolated native catalog for calendar serialization checks. */
    private static SiteExports calendar() {
        SiteExports local = new SiteExports();
        local.catalog = new JsonObject().put("events", new JsonArray());
        return local;
    }

    /** Parses generated XML with external entities and DTDs disabled so assertions do not permit external I/O. */
    private static Document xml(String value) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(value)));
    }
}
