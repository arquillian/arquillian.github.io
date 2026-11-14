package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies Roq serves the authoritative legacy assets without maintaining duplicate editable copies. */
@QuarkusTest
class SharedAssetsTest {

    /** Compares a prominent production image and shared scripts with their original source bytes. */
    @Test
    void servesExistingAssets() throws IOException {
        for (String asset : new String[] {"images/testing-java-microservices-cover.png", "javascripts/site.js",
                "javascripts/jquery-scroll.js", "favicon.ico"}) {
            byte[] served = given().when().get("/" + asset).then().statusCode(200).extract().asByteArray();
            assertArrayEquals(Files.readAllBytes(Path.of("..").resolve(asset)), served, asset);
        }
    }

    /** Checks the captured stylesheet retains its license and uses local sprite paths for previews. */
    @Test
    void servesCapturedStylesheet() throws IOException {
        byte[] served = given().when().get("/stylesheets/screen.css").then().statusCode(200).extract().asByteArray();
        assertArrayEquals(Files.readAllBytes(Path.of("public/stylesheets/screen.css")), served);
        String css = new String(served, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(css.contains("Copyright 2012 Twitter, Inc"));
        assertTrue(css.contains("/images/header_icons.png"));
    }

    /** Keeps the standalone repository dashboard styled with its exact captured Bootstrap CSS. */
    @Test
    void servesDashboardStylesheet() throws IOException {
        byte[] served = given().when().get("/stylesheets/dashboard.css")
                .then().statusCode(200).extract().asByteArray();
        assertEquals(265277, served.length);
        assertArrayEquals(Files.readAllBytes(Path.of("public/stylesheets/dashboard.css")), served);
        assertTrue(new String(served, java.nio.charset.StandardCharsets.UTF_8)
                .contains("Copyright 2012 Twitter, Inc"));
    }
}
