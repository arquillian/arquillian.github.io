package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.io.StringReader;
import java.net.URL;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.xml.sax.InputSource;

/** Protects the native generator's published form, mutable snapshot data and selected Maven POM behavior. */
@QuarkusTest
class SiteGeneratorTest {
    /** Captured production control order, names and published module links, kept inside the test for clean checkouts. */
    private static final String PUBLISHED_CONTROLS = """
            Test Framework|arquillian-junit|Arquillian JUnit|
            Test Framework|arquillian-junit-standalone|Arquillian JUnit Standalone|
            Test Framework|arquillian-testng|Arquillian TestNG|
            Test Framework|arquillian-testng-standalone|Arquillian TestNG Standalone|
            Test Framework|arquillian-cukes|Arquillian Cukes In Space|
            Test Framework|arquillian-ape-junit-rule|Arquillian APE JUnit Rule|
            Front-end|arquillian-drone|Arquillian Drone|/modules/drone-extension/index.html
            Front-end|arquillian-graphene|Arquillian Graphene|/modules/graphene-extension/index.html
            Front-end|arquillian-warp|Arquillian Warp|/modules/warp-extension/index.html
            Front-end|arquillian-warp-jsf|Arquillian Warp JSF|
            Front-end|arquillian-rest-resteasy2|Arquillian REST RestEasy 2x|
            Front-end|arquillian-rest-resteasy3|Arquillian REST RestEasy 3x|
            Front-end|arquillian-rest-jaxrs2|Arquillian REST JaxRS 2|
            Front-end|arquillian-rest-jersey|Arquillian REST Jersey|
            Front-end|arquillian-rest-warp-resteasy|Arquillian REST Warp RestEasy|
            Front-end|arquillian-rest-warp-jaxrs2|Arquillian REST Warp JAXRS 2.0|
            Front-end|arquillian-rest-warp-cxf|Arquillian REST Warp CXF|
            Front-end|arquillian-rest-warp-jersey|Arquillian REST Warp Jersey|
            Front-end|arquillian-cube-docker-restassured|Arquillian Cube Docker REST Assured|
            Front-end|arquillian-ape-rest-postman|Arquillian APE REST Postman|
            Reporting|arquillian-graphene-recorder|Arquillian Graphene Recorder|
            Reporting|arquillian-recorder-api|Arquillian Recorder API|
            Reporting|arquillian-recorder|Arquillian Recorder|/modules/recorder-extension/index.html
            Back-end|arquillian-transaction-jta|Arquillian Transaction JTA|
            Back-end|arquillian-persistence|Arquillian Persistence|/modules/persistence-extension/index.html
            Back-end|arquillian-byteman|Arquillian Byteman|/modules/byteman-extension/index.html
            Spring|arquillian-spring|Arquillian Spring|/modules/spring-extension/index.html
            Spring|arquillian-spring-transaction|Arquillian Spring Transaction|
            Spring|arquillian-spring-persistence|Arquillian Spring Persistence|
            Spring|arquillian-spring-warp|Arquillian Spring Warp|
            Issue Tracking|arquillian-governor-jira|Arquillian Governor JIRA|
            Issue Tracking|arquillian-governor-skipper|Arquillian Governor Skipper|
            Quality Tools|arquillian-jacoco|Arquillian Jacoco|/modules/jacoco-extension/index.html
            Containers|arquillian-chameleon|Arquillian Chameleon|/modules/arquillian-container-chameleon-container-adapter/index.html
            Containers|arquillian-cube-containerless|Arquillian Cube Containerless|
            System|arquillian-cube-docker|Arquillian Cube Docker|
            System|arquillian-cube-kubernetes|Arquillian Cube Kubernetes|
            System|arquillian-cube-openshift|Arquillian Cube OpenShift|
            Other|arquillian-algeron-pact-consumer|Arquillian Algeron Pact Consumer|
            Other|arquillian-algeron-pact-provider|Arquillian Algeron Pact Provider|
            Other|arquillian-algeron-git-publisher|Arquillian Algeron Git Publisher|
            Other|arquillian-algeron-git-retriever|Arquillian Algeron Git Retriever|
            Other|arquillian-algeron-maven-retriever|Arquillian Algeron Maven Retriever|
            Other|arquillian-algeron-pact-broker-retriever|Arquillian Algeron Pact Broker Retriever|
            Other|arquillian-ape-nosql-mongodb|Arquillian APE NoSQL MongoDB|
            Other|arquillian-ape-nosql-vault|Arquillian APE NoSQL Vault|
            Other|arquillian-ape-nosql-redis|Arquillian APE NoSQL Redis|
            Other|arquillian-ape-sql-container-dbunit|Arquillian APE SQL Container DBUnit|
            Other|arquillian-ape-sql-standalone-dbunit|Arquillian APE SQL Standalone DBUnit|
            Other|arquillian-ape-sql-standalone-flyway|Arquillian APE SQL Standalone Flyway|
            Other|arquillian-rusheye|Arquillian Rusheye|/modules/rusheye-extension/index.html
            """;

    @Inject
    SiteCatalog catalog;

    @Inject
    Site site;

    @TestHTTPResource("/guides/generator/")
    URL generatorUrl;

    /** Compares every visible control, group and link against the captured production form. */
    @Test
    void rendersAllPublishedControls() {
        Document page = page();
        assertEquals("Getting Started Generator · Arquillian", page.title());
        assertTrue(page.body().hasClass("guide"));
        assertEquals("Arquillian Universe Generator Beta Designed exclusively to get you started writing real tests as fast as possible.",
                page.selectFirst("#content-header h1").text());
        assertEquals(1, page.select("#main[role=main] > .container > form#form.form-inline").size());
        assertEquals("1.2.0.1", page.selectFirst("#form").attr("data-universe-version"));
        List<String> expected = PUBLISHED_CONTROLS.strip().lines().toList();
        assertEquals(51, expected.size());
        assertEquals(10, page.select("#form > .row > .span4 > fieldset").size());
        assertEquals(expected.size(), page.select("#form input[type=checkbox]").size());
        for (int index = 0; index < expected.size(); index++) {
            String[] row = expected.get(index).split("\\|", -1);
            Element label = page.select("#form label.checkbox").get(index);
            assertEquals(row[0], label.closest("fieldset").selectFirst("legend").text());
            assertEquals(row[1], label.selectFirst("input[type=checkbox]").id());
            assertEquals(row[2], label.text());
            assertEquals(row[3], label.select("a").attr("href"));
        }
    }

    /** Detects a frozen model, unsafe label/version interpolation or stale browser-title data. */
    @Test
    void reflectsSnapshotMutationsWithoutScriptInterpolation() {
        JsonObject universe = universe();
        JsonArray extensions = universe.getJsonArray("extensions");
        JsonObject release = universe.getJsonArray("release_history").getJsonObject(0);
        String version = release.getString("version");
        JsonObject metadata = site.page("guides/generator.html").data();
        Object browserTitle = metadata.getValue("browser_title");
        String unsafe = "O'Reilly \\\" </script><script>window.injected=true</script> &";
        try {
            universe.put("extensions", new JsonArray().add(extension(unsafe, "arquillian-spring-mutated")));
            release.put("version", unsafe);
            metadata.put("browser_title", unsafe);
            Document page = page();
            assertEquals(unsafe, page.title());
            assertEquals(List.of("Spring"), page.select("#form legend").eachText());
            assertEquals(unsafe, page.selectFirst("#form label").text());
            assertEquals(unsafe, page.selectFirst("#form").attr("data-universe-version"));
            assertTrue(page.select("script").stream().noneMatch(script -> script.data().contains("window.injected")));
        } finally {
            universe.put("extensions", extensions);
            release.put("version", version);
            if (browserTitle == null) metadata.remove("browser_title");
            else metadata.put("browser_title", browserTitle);
        }
    }

    /** Exercises ordered classification, CORE exclusion and fresh latest-release reads without modifying raw rows. */
    @Test
    void classifiesFreshRowsAndLeavesSourceUntouched() {
        SiteGenerator local = fixture(new JsonArray().add(extension("Spring Warp", "arquillian-spring-warp"))
                .add(extension("Hidden", "arquillian-core"))
                .add(extension("JUnit", "arquillian-junit"))
                .add(extension("Recorder", "arquillian-graphene-recorder")));
        assertEquals(List.of("Spring", "Test Framework", "Reporting"), local.model().getJsonArray("groups").stream()
                .map(JsonObject.class::cast).map(group -> group.getString("name")).toList());
        local.catalog.find("modules", "/modules/arquillian-universe-bom-bom/").getJsonArray("release_history")
                .getJsonObject(0).put("version", "2.0");
        assertEquals("2.0", local.model().getString("version"));
        assertFalse(local.catalog.find("modules", "/modules/arquillian-universe-bom-bom/")
                .getJsonArray("extensions").getJsonObject(0).containsKey("url"));
        assertTrue(fixture(new JsonArray()).model().getJsonArray("groups").isEmpty());
    }

    /** Fails clearly on duplicate DOM coordinates, malformed raw fields and missing release metadata. */
    @Test
    void rejectsMalformedAndDuplicateCoordinates() {
        JsonObject valid = extension("JUnit", "arquillian-junit");
        assertThrows(IllegalArgumentException.class, () -> fixture(new JsonArray().add(valid).add(valid.copy())).model());
        assertThrows(IllegalArgumentException.class,
                () -> fixture(new JsonArray().add(valid).add(valid.copy().put("groupId", "another.group"))).model());
        assertThrows(IllegalArgumentException.class,
                () -> fixture(new JsonArray().add(valid.copy().put("artifactId", "bad\" onclick=evil"))).model());
        assertThrows(IllegalArgumentException.class,
                () -> fixture(new JsonArray().add(valid.copy().put("groupId", "bad group"))).model());
        assertThrows(IllegalArgumentException.class,
                () -> fixture(new JsonArray().add(valid.copy().put("name", " "))).model());
        assertThrows(IllegalArgumentException.class, () -> fixture(new JsonArray().add("invalid")).model());
        SiteGenerator missing = fixture(new JsonArray());
        missing.catalog.find("modules", "/modules/arquillian-universe-bom-bom/").put("release_history", new JsonArray());
        assertTrue(assertThrows(IllegalArgumentException.class, missing::model).getMessage().contains("release_history"));
        SiteGenerator ambiguous = fixture(new JsonArray().add(extension("Drone", "arquillian-drone")));
        ambiguous.catalog.catalog.getJsonArray("repositories")
                .add(new JsonObject().put("id", "drone").put("path", "arquillian-extension-drone"));
        ambiguous.catalog.catalog.getJsonArray("modules")
                .add(new JsonObject().put("repository_id", "drone").put("link", "/modules/drone-one/"))
                .add(new JsonObject().put("repository_id", "drone").put("link", "/modules/drone-two/"));
        assertTrue(assertThrows(IllegalArgumentException.class, ambiguous::model).getMessage().contains("Ambiguous"));
    }

    /** Runs real client selection, form replacement, syntax highlighting and XML parsing when a browser is allocated. */
    @Test
    @EnabledIfEnvironmentVariable(named = "ARQUILLIAN_GENERATOR_BROWSER", matches = "true")
    void generatesOnlySelectedMavenDependenciesInBrowser() throws Exception {
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            com.microsoft.playwright.Page page = browser.newPage();
            page.navigate(generatorUrl.toString());
            page.locator("#arquillian-junit").check();
            page.locator("#arquillian-drone").check();
            page.locator("#guides_menu").click();
            assertEquals(0, page.locator("#form input").count());
            assertEquals(1, page.locator("#form > pre.prettify > code.prettify").count());
            page.waitForFunction("document.querySelector('#form code').querySelectorAll('span').length > 0");
            String pom = page.locator("#form code").textContent();
            DocumentBuilderFactory xml = DocumentBuilderFactory.newInstance();
            xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            xml.newDocumentBuilder().parse(new InputSource(new StringReader(pom)));
            Document parsed = Jsoup.parse(pom, "", org.jsoup.parser.Parser.xmlParser());
            assertEquals("1.2.0.1", parsed.getElementsByTag("version.arquillian_universe").getFirst().text());
            assertEquals("arquillian-universe", parsed.selectFirst("dependencyManagement artifactId").text());
            assertEquals("${version.arquillian_universe}", parsed.selectFirst("dependencyManagement version").text());
            assertEquals("import", parsed.selectFirst("dependencyManagement scope").text());
            assertEquals(List.of("arquillian-junit", "arquillian-drone"),
                    parsed.select("project > dependencies > dependency > artifactId").eachText());
            assertEquals(List.of("test", "test"), parsed.select("project > dependencies > dependency > scope").eachText());
            assertEquals(List.of("pom", "pom"), parsed.select("project > dependencies > dependency > type").eachText());
            assertEquals(List.of("org.arquillian.universe", "org.arquillian.universe"),
                    parsed.select("project > dependencies > dependency > groupId").eachText());
        }
    }

    /** Checks actual client XML escaping even if DOM values are changed after server-side coordinate validation. */
    @Test
    @EnabledIfEnvironmentVariable(named = "ARQUILLIAN_GENERATOR_BROWSER", matches = "true")
    void escapesBrowserMutationsIntoXmlText() throws Exception {
        String unsafe = "<&\"' </script><script>window.injected=true</script>";
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            com.microsoft.playwright.Page page = browser.newPage();
            page.navigate(generatorUrl.toString());
            page.locator("#form").evaluate("(form, value) => form.setAttribute('data-universe-version', value)", unsafe);
            page.locator("#arquillian-junit").check();
            page.locator("#arquillian-junit").evaluate("(input, value) => { input.id = value; input.setAttribute('data-group-id', value); }", unsafe);
            page.locator("#guides_menu").click();
            String pom = page.locator("#form code").textContent();
            DocumentBuilderFactory xml = DocumentBuilderFactory.newInstance();
            xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            xml.newDocumentBuilder().parse(new InputSource(new StringReader(pom)));
            Document parsed = Jsoup.parse(pom, "", org.jsoup.parser.Parser.xmlParser());
            assertEquals(unsafe, parsed.getElementsByTag("version.arquillian_universe").getFirst().text());
            assertEquals(unsafe, parsed.selectFirst("project > dependencies > dependency > artifactId").text());
            assertEquals(unsafe, parsed.selectFirst("project > dependencies > dependency > groupId").text());
            assertEquals(0, page.locator("#form script").count());
            assertEquals(null, page.evaluate("window.injected"));
        }
    }

    /** Fetches the real native route so template resolution and HTML escaping remain part of the contract. */
    private static Document page() {
        return Jsoup.parse(given().when().get("/guides/generator/").then().statusCode(200).extract().asString());
    }

    /** Accesses the sole canonical Universe component instead of maintaining a separate generator snapshot. */
    private JsonObject universe() {
        return catalog.find("modules", "/modules/arquillian-universe-bom-bom/");
    }

    /** Builds raw dependency metadata using the same three fields emitted by the Java refresh. */
    private static JsonObject extension(String name, String artifactId) {
        return new JsonObject().put("name", name).put("groupId", "org.arquillian.universe").put("artifactId", artifactId);
    }

    /** Supplies a real in-memory catalog with required identity/release ports and no network or filesystem reads. */
    private static SiteGenerator fixture(JsonArray extensions) {
        SiteGenerator generator = new SiteGenerator();
        generator.catalog = new SiteCatalog();
        generator.catalog.catalog = new JsonObject().put("repositories", new JsonArray())
                .put("modules", new JsonArray().add(new JsonObject().put("id", "universe")
                        .put("link", "/modules/arquillian-universe-bom-bom/").put("extensions", extensions)
                        .put("release_history", new JsonArray().add(new JsonObject().put("version", "1.2.0.1")))));
        generator.data = generator.catalog.catalog;
        return generator;
    }
}
