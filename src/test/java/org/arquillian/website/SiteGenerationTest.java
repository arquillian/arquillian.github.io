package org.arquillian.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.arquillian.website.data.model.SiteData;
import org.arquillian.website.roq.GeneratedDataExtension;
import org.arquillian.website.roq.GeneratedSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks the minimum source contract for the first generated Roq page. */
@QuarkusTest
@RoqAndRoll(port = 8082)
class SiteGenerationTest {

    @Inject
    SiteData siteData;

    @Inject
    GeneratedSelection generatedSelection;

    @TempDir
    Path stagingDirectory;

    /** Ensures the homepage source and Qute layout are present before batch generation. */
    @Test
    void homepageUsesAsciiDocAndPageLayout() throws Exception {
        assertTrue(Files.exists(Path.of("content/index.adoc")));
        assertTrue(Files.exists(Path.of("templates/layouts/page.html")));
        assertTrue(Files.exists(Path.of("templates/layouts/base.html")));
        String layout = Files.readString(Path.of("templates/layouts/base.html"));
        assertTrue(layout.contains("<title>{#if page.title??}"));
    }

    /** Confirms Roq binds the curated data file before Qute rendering begins. */
    @Test
    void bindsTypedSiteData() {
        assertEquals("Arquillian", siteData.name());
        assertTrue(siteData.description().contains("testing"));
    }

    /** Confirms generated routes retain stable pretty output filenames for static export. */
    @Test
    void selectsGeneratedOutputPaths() {
        assertTrue(generatedSelection.pages().paths().stream()
                .anyMatch(path -> path.outputPath().equals("modules/arquillian-core/index.html")));
        assertTrue(generatedSelection.pages().paths().stream()
                .anyMatch(path -> path.outputPath().equals("community/contributors/index.html")));
        assertTrue(generatedSelection.pages().paths().stream()
                .anyMatch(path -> path.outputPath().equals("community/aslakknutsen/index.html")));
    }

    /** Confirms normalized provider output is staged outside tracked data directories. */
    @Test
    void stagesGeneratedDataSafely() throws Exception {
        GeneratedDataExtension loader = new GeneratedDataExtension(stagingDirectory,
                Path.of("data/curated/generated-pages.json"));

        assertTrue(loader.stage(loader.load()).startsWith(stagingDirectory));
        assertThrows(IllegalArgumentException.class,
                () -> new GeneratedDataExtension(Path.of("data"), Path.of("data/curated/generated-pages.json"))
                        .stage(loader.load()));
    }

    /** Confirms generated page families resolve through the running Roq router. */
    @Test
    void servesGeneratedPageFamilies() {
        RestAssured.port = 8082;
        assertTrue(RestAssured.get("/modules/arquillian-core/").then().statusCode(200).extract().asString()
                .contains("Arquillian Core"));
        assertTrue(RestAssured.get("/blog/2011/04/12/arquillian-core-1-0-0-final/").then().statusCode(200).extract()
                .asString().contains("1.0.0.Final"));
        assertTrue(RestAssured.get("/community/aslakknutsen").then().statusCode(200).extract().asString()
                .contains("Aslak Knutsen"));
    }

}
