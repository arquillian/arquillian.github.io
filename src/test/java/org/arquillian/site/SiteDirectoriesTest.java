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
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
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

/** Verifies published directories, mutable snapshot consumption and native HTTP routes without ignored captures. */
@QuarkusTest
class SiteDirectoriesTest {
    @Inject
    @Named("catalog")
    JsonObject runtimeData;

    /** Protects group order, current/obsolete membership, exact labels, latest versions and documentation filtering. */
    @Test
    void groupsThePublishedCatalog() throws IOException {
        SiteDirectories directories = directories();
        assertEquals(List.of("Boms", "Platforms", "Tools", "Daemons", "Container Adapters", "Extensions",
                "Test Runners (other than JUnit and TestNG)", "Tool Plugins", "Shrinkwraps", "Tcks"),
                directories.moduleGroups(false).stream().map(group -> group.getString("title")).toList());
        assertEquals(List.of("Obsolete Container Adapters", "Obsolete Extensions"),
                directories.moduleGroups(true).stream().map(group -> group.getString("title")).toList());
        Document modules = render(directories, "modules/index.html");
        assertEquals(121, modules.select("#content .nav-list > li > a").size());
        assertEquals("Universe – 1.2.0.1", modules.selectFirst(".nav-list > li > a").text());
        assertEquals(5, modules.select(".label-warning").size());
        assertEquals(List.of("Platforms", "Tools", "Extensions"),
                directories.documentationGroups().stream().map(group -> group.getString("title")).toList());
        Document docs = render(directories, "docs/index.adoc");
        assertEquals(10, docs.select(".nav-list > li > a").size());
        assertEquals("http://arquillian.org/arquillian-core", docs.selectFirst(".nav-list > li > a").attr("href"));
        assertEquals(List.of("#test-runners", "#deployments", "#test-enrichers", "#containers", "#run-modes", "#extensions"),
                docs.select(".nav-pills a[data-toggle=tab]").eachAttr("href"));
        assertEquals(6, docs.select(".tab-content > .tab-pane").size());
        assertEquals(1, docs.select(".tab-pane.active#test-runners").size());
        assertEquals("@RunWith(Arquillian.class)", docs.selectFirst("#test-runners code").text());
        assertEquals("JUnit – add the annotation @RunWith(Arquillian.class) to the test class",
                docs.select("#test-runners li").get(2).text());
        assertEquals("ShrinkWrap", docs.selectFirst("#deployments a[href='http://jboss.org/shrinkwrap']").text());
        assertEquals(3, docs.select("#containers ol > li").size());
        assertEquals(0, docs.select("div.paragraph,div.ulist,div.olist,li > p").size());
        String authored = Files.readString(Path.of("content/docs/index.adoc"));
        assertTrue(authored.contains("link:http://jboss.org/shrinkwrap[ShrinkWrap]"));
        assertFalse(authored.contains("<p>") || authored.contains("siteDirectories"));
    }

    /** Keeps historical card, social and biography spacing while repairing the duplicate modal target. */
    @Test
    void preservesContributorCardsWithUniqueModalTargets() throws IOException {
        SiteDirectories directories = directories();
        assertEquals(357, directories.contributors().size());
        Document contributors = render(directories, "community/contributors.html");
        assertEquals("Contributors 357", contributors.selectFirst("#content-header h1").text());
        assertEquals(357, contributors.select("ul.contributors > li").size());
        assertEquals(357, contributors.select(".modal[id]").eachAttr("id").stream().distinct().count());
        assertEquals("Aslak Knutsen", contributors.selectFirst("ul.contributors > li > h3").text());
        assertEquals("Blog Twitter GitHub", contributors.selectFirst(".photo ul").wholeText()
                .replaceAll("\\s+", " ").trim());
        assertEquals("Pedro Kowalski", contributors.select("ul.contributors > li > h3").get(138).text());
        assertEquals("Piotr Nowicki", contributors.select("ul.contributors > li > h3").get(343).text());
        for (org.jsoup.nodes.Element photo : contributors.select(".photo > img")) {
            assertEquals(1, contributors.select(photo.attr("data-target")).size());
            assertEquals("modal", photo.attr("data-toggle"));
        }
        assertEquals("#piotrnowicki-343", contributors.select(".photo > img").get(343).attr("data-target"));
        assertEquals(357, contributors.select(".modal-body img[height=60][width=60]").size());
        assertEquals(357, contributors.select(".photo > img[height=140][width=140]").size());
        assertEquals(86, contributors.select(".modal-body > p").size());
        assertTrue(directories.contributors().get(137).getString("bio_html").contains("</p>\n<p>"));
        assertFalse(contributors.text().contains("commit count"));
    }

    /** Real template renders must change when the sole catalog's consumed fields change. */
    @Test
    void reflectsSnapshotMutationsWithoutMutatingInput() throws IOException {
        SiteDirectories directories = directories();
        JsonObject core = directories.data.getJsonArray("modules").stream().map(JsonObject.class::cast)
                .filter(row -> row.getString("link").equals("/modules/core-platform/")).findFirst().orElseThrow();
        core.getJsonObject("directory").put("label", "Changed Core");
        core.getJsonArray("release_history").getJsonObject(0).put("version", "99.0");
        core.getJsonObject("documentation").put("href", "https://docs.example/core");
        JsonObject identity = directories.data.getJsonArray("identities").stream().map(JsonObject.class::cast)
                .filter(row -> row.getString("github_id").equals("aslakknutsen")).findFirst().orElseThrow();
        identity.put("name", "Updated Name").put("company", "Updated Company").put("bio_html", "<p>Updated bio</p>");
        identity.getJsonArray("directory_cards").getJsonObject(0).put("order", 999);
        String before = directories.data.encode();
        assertTrue(render(directories, "modules/index.html").text().contains("Changed Core – 99.0"));
        assertEquals("Changed Core – 99.0", render(directories, "docs/index.adoc")
                .selectFirst("a[href='https://docs.example/core']").text());
        Document contributors = render(directories, "community/contributors.html");
        assertEquals("Updated Name", contributors.select("ul.contributors > li > h3").last().text());
        assertEquals("Updated bio", contributors.selectFirst("#aslakknutsen .modal-body").text());
        assertEquals(before, directories.data.encode());
    }

    /** New or malformed classifications must explain the missing curated mapping instead of disappearing. */
    @Test
    void rejectsUnclassifiedRowsAndDuplicateCardHooks() throws IOException {
        SiteDirectories directories = directories();
        directories.data.getJsonArray("modules").getJsonObject(0).getJsonObject("directory").put("category", "unknown");
        SiteDirectories unknown = directories;
        assertThrows(IllegalArgumentException.class, () -> unknown.moduleGroups(false));
        directories = directories();
        directories.data.getJsonArray("identities").getJsonObject(0).remove("directory_cards");
        SiteDirectories missing = directories;
        assertThrows(IllegalArgumentException.class, missing::contributors);
        directories = directories();
        JsonObject piotr = directories.data.getJsonArray("identities").stream().map(JsonObject.class::cast)
                .filter(row -> row.getString("github_id").equals("piotrnowicki")).findFirst().orElseThrow();
        piotr.getJsonArray("directory_cards").getJsonObject(1).remove("modal_id");
        assertThrows(IllegalArgumentException.class, directories::contributors);
    }

    /** Native requests verify page discovery, browser titles and outer structure after the source phase is active. */
    @Test
    void servesAllThreeNativeRoutes() {
        for (String[] entry : new String[][] {{"/modules/", "Modules"}, {"/docs/", "Documentation"},
                {"/community/contributors/", "Contributors"}}) {
            Document page = Jsoup.parse(RestAssured.get(entry[0]).then().statusCode(200).extract().asString());
            assertEquals(entry[1] + " · Arquillian", page.title());
            assertEquals(1, page.select("#main #content").size());
            assertEquals(1, page.select("#banner").size());
            if (entry[0].equals("/modules/")) {
                assertEquals(121, page.select("#content .nav-list > li > a").size());
                assertEquals("/modules/arquillian-universe-bom-bom/", page.selectFirst("#content .nav-list a").attr("href"));
            } else if (entry[0].equals("/docs/")) {
                assertEquals(10, page.select("#content .nav-list > li > a").size());
                assertEquals("http://arquillian.org/arquillian-core", page.selectFirst("#content .nav-list a").attr("href"));
            } else {
                assertEquals(357, page.select("ul.contributors > li").size());
                assertEquals("Aslak Knutsen", page.selectFirst("ul.contributors > li > h3").text());
                assertEquals("#aslakknutsen", page.selectFirst(".photo > img").attr("data-target"));
            }
        }
    }

    /** Repeated native requests must consume changed catalog facts rather than frozen directory fragments. */
    @Test
    void rendersSnapshotMutationsThroughHttp() {
        JsonObject core = runtimeData.getJsonArray("modules").stream().map(JsonObject.class::cast)
                .filter(row -> row.getString("link").equals("/modules/core-platform/")).findFirst().orElseThrow();
        JsonObject metadata = core.getJsonObject("directory");
        String previousLabel = metadata.getString("label");
        String previousHref = core.getJsonObject("documentation").getString("href");
        try {
            metadata.put("label", "Changed native core");
            core.getJsonObject("documentation").put("href", "https://docs.example/native-core");
            Document modules = Jsoup.parse(RestAssured.get("/modules/").then().statusCode(200).extract().asString());
            assertTrue(modules.selectFirst("a[href='/modules/core-platform/']").text().startsWith("Changed native core"));
            Document docs = Jsoup.parse(RestAssured.get("/docs/").then().statusCode(200).extract().asString());
            assertTrue(docs.selectFirst("a[href='https://docs.example/native-core']").text().startsWith("Changed native core"));
        } finally {
            metadata.put("label", previousLabel);
            core.getJsonObject("documentation").put("href", previousHref);
        }
    }

    /** Creates a fresh native data object so mutation checks never affect the application-scoped runtime bean. */
    private static SiteDirectories directories() throws IOException {
        SiteDirectories directories = new SiteDirectories();
        directories.data = new JsonObject(Files.readString(Path.of("data/catalog.json")));
        return directories;
    }

    /** Renders real AsciiDoc and its docs layout through Qute without depending on migration captures. */
    private static Document render(SiteDirectories directories, String path) throws IOException {
        Engine engine = Engine.builder().configureParser((id, variant) -> new ParserConfig('='))
                .addDefaults().addValueResolver(io.quarkus.qute.ValueResolvers.rawResolver()).addValueResolver(new ReflectionValueResolver())
                .addValueResolver(ValueResolver.builder().appliesTo(context -> context.getBase() instanceof JsonObject)
                        .resolveSync(context -> ((JsonObject) context.getBase()).getValue(context.getName())).build())
                .addNamespaceResolver(NamespaceResolver.builder("cdi").resolve(context -> switch (context.getName()) {
                    case "siteDirectories" -> directories;
                    case "legacyMarkup" -> new LegacyMarkup();
                    default -> throw new IllegalArgumentException(context.getName());
                }).build()).build();
        String source = Files.readString(Path.of("content", path)).split("---", 3)[2];
        if (path.endsWith(".adoc")) {
            Asciidoctor renderer = Asciidoctor.Factory.create();
            try {
                String content = renderer.convert(source, Options.builder().safe(SafeMode.SECURE).build());
                String layout = Files.readString(Path.of("templates/layouts/docs.html")).split("---", 3)[2];
                return Jsoup.parse(engine.parse(layout).data("page", new JsonObject().put("content", content)).render());
            } finally {
                renderer.shutdown();
            }
        }
        return Jsoup.parse(engine.parse(source).render());
    }
}
