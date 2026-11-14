package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/** Protects the published guide directory, article routes and preserved chapter navigation. */
@QuarkusTest
class GuideRenderingTest {

    @Inject
    @Named("catalog")
    JsonObject catalog;

    /** Exercises native guide summaries, published grouping, translation flags and external references. */
    @Test
    void rendersGuideDirectory() {
        Document document = Jsoup.parse(given().when().get("/guides/").then().statusCode(200).extract().asString());
        assertEquals("Guides · Arquillian", document.title());
        assertTrue(document.body().hasClass("guide"));
        assertTrue(document.body().hasClass("index"));
        assertEquals(java.util.List.of("First Steps", "More Coverage", "Enhance", "Reference"),
                document.select("#content > h2.group").eachText());
        assertEquals("Learn how to add Arquillian to the test suite of your project and write your first Arquillian test.",
                document.selectFirst("#content > dl > dd").text());
        assertTrue(document.select("#content a[href='/guides/getting_started_de/'] img.flag").size() > 0);
        assertEquals("API & SPI Documentation", document.selectFirst("#content a[href='http://docs.jboss.org/arquillian/aggregate/latest']").text());
        assertEquals(5, document.select("#sidebar ul.legend > li").size());
    }

    /** Finds missing translated pages, stale chapter targets and accidental publication of draft guides. */
    @Test
    void rendersEveryPublishedGuideAndChapter() {
        JsonArray guides = catalog.getJsonArray("guides");
        int published = 0;
        for (int i = 0; i < guides.size(); i++) {
            JsonObject guide = guides.getJsonObject(i);
            String link = guide.getString("link");
            if (!link.startsWith("/guides/")) {
                continue;
            }
            Document document = Jsoup.parse(given().when().get(link).then().statusCode(200).extract().asString());
            assertEquals(guide.getString("title") + " · Arquillian Guides", document.title(), link);
            assertEquals(guide.getString("language"), document.selectFirst("html").attr("lang"), link);
            JsonArray chapters = guide.getJsonArray("chapters");
            for (int j = 0; j < chapters.size(); j++) {
                JsonObject chapter = chapters.getJsonObject(j);
                String id = chapter.getString("id");
                assertTrue(document.getElementById(id) != null, link + "#" + id);
                assertEquals("#" + id, document.getElementById(id + "_link").attr("href"), link);
            }
            published++;
        }
        assertEquals(41, published);
        given().when().get("/guides/get_started_faster_with_forge_ja/").then().statusCode(404);
    }


    /** Keeps the published German Forge list boundary and its spacing while preserving the intended prose and commands. */
    @Test
    void preservesGermanForgeInstallationLists() {
        Document document = Jsoup.parse(given().when().get("/guides/getting_started_de/")
                .then().statusCode(200).extract().asString());
        Element download = document.selectFirst("#create_project_using_forge + p + p + ol");
        assertEquals(1, download.childrenSize());
        assertTrue(download.child(0).text().startsWith("Download Forge und packe Forge"));
        Element path = download.nextElementSibling();
        assertEquals("ol", path.tagName());
        assertEquals(1, path.childrenSize());
        assertEquals("Füge $FORGE_HOME/bin zu Deinem Pfad hinzu (Windows, Linux oder Mac OSX)", path.child(0).text());
        assertTrue(path.nextElementSibling().text().startsWith("Auf Unix-basierenden Systemen"));
        assertEquals("$ export FORGE_HOME=$HOME/forge/\n$ export PATH=$PATH:$FORGE_HOME/bin",
                path.nextElementSibling().nextElementSibling().selectFirst("code.command").wholeText());
        Element information = document.selectFirst("#annahmen + p + p.info");
        assertTrue(information.select("del").isEmpty());
        assertTrue(information.text().contains("— wenn nicht besser —"));
        assertTrue(information.text().contains("einen Ant- oder Gradle-Build"));
    }

    /** Protects authoring links, legacy fragments, code hooks, translations and chapter navigation. */
    @Test
    void rendersGettingStarted() {
        String html = given().when().get("/guides/getting_started/").then().statusCode(200).extract().asString();
        Document document = Jsoup.parse(html);
        assertEquals("Getting Started · Arquillian Guides", document.title());
        assertTrue(document.body().hasClass("guide"));
        assertEquals("Getting Started", document.selectFirst("#content .titlebar h2").text());
        assertTrue(document.select("#content > h1").isEmpty());
        assertEquals(1, document.select("#content > .header > ul").size());
        assertEquals("/guides/", document.selectFirst("#banner li.active a").attr("href"));
        assertTrue(document.selectFirst("#content .btn-group a").attr("href")
                .endsWith("/roq/content/guides/getting_started.adoc"));
        assertEquals("Dan Allen", document.selectFirst("#content .authors a").text());
        assertFalse(document.select("#guides a[href='/guides/getting_started_de/'] img.flag").isEmpty());
        for (String id : new String[] {"assumptions", "create_a_new_project", "write_an_arquillian_test", "share"}) {
            assertTrue(document.getElementById(id) != null, "Missing preserved fragment " + id);
            assertEquals("#" + id, document.getElementById(id + "_link").attr("href"));
        }
        assertTrue(document.select("pre.prettify > code.prettify").size() > 5);
        assertFalse(document.select(".filename + pre.prettify").isEmpty());
        assertFalse(document.select("#content p.info").isEmpty());
        assertEquals(document.select("#content p.info").size(), document.select("#content p.info > span").size());
        assertTrue(document.select("#content").text().contains("Nov 20, 2017"));
        assertTrue(html.contains("activateScrollingToc(["));
        assertTrue(document.select("#content pre").text().contains("xmlns=\"http://jboss.org/schema/arquillian\""));
        assertFalse(html.contains("#{site."));
    }
}
