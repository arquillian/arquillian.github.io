package org.arquillian.website;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.roq.testing.RoqAndRoll;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Checks the minimum source contract for the first generated Roq page. */
@QuarkusTest
@RoqAndRoll
class SiteGenerationTest {

    /** Ensures the homepage source and Qute layout are present before batch generation. */
    @Test
    void homepageUsesAsciiDocAndPageLayout() throws Exception {
        assertTrue(Files.exists(Path.of("content/index.adoc")));
        assertTrue(Files.exists(Path.of("templates/layouts/page.html")));
        assertTrue(Files.exists(Path.of("templates/layouts/base.html")));
        String layout = Files.readString(Path.of("templates/layouts/base.html"));
        assertTrue(layout.contains("<title>{#if page.title??}"));
    }
}
