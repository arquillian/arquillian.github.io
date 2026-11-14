package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Protects origin filtering, link resolution, asset integrity, and failure evidence. */
class ProductionBaselineTest {

    /** Prevents default ports being mistaken for foreign origins while keeping schemes and hosts isolated. */
    @Test
    void comparesEffectiveOrigins() {
        assertTrue(ProductionBaseline.sameOrigin(URI.create("https://example.org/a"), URI.create("https://EXAMPLE.org:443/b")));
        assertFalse(ProductionBaseline.sameOrigin(URI.create("https://example.org/"), URI.create("http://example.org/")));
        assertFalse(ProductionBaseline.sameOrigin(URI.create("https://example.org/"), URI.create("https://example.org:8443/")));
    }

    /** Checks saved bytes, including inline CSS assets, against a local server without remote requests. */
    @Test
    void capturesResolvedLinksAndAssetFailures(@TempDir Path output) throws Exception {
        AtomicInteger externalCalls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/guide/", exchange -> {
            byte[] html = """
                    <html><head><link rel="stylesheet" href="../site.css">
                    <style>.cover { background: url('../cover.png'); }</style></head><body>
                    <h2 id="chapter">Chapter</h2><a name="old_anchor"></a>
                    <a href="../next/?q=1#part">Next</a><a href="#chapter">Here</a>
                    <a href="http://localhost:%s/outside/">Outside</a>
                    <a href="mailto:test@example.org">Mail</a><img src="../missing.png">
                    <div class="cover" style="background-image: url('../inline.png')"></div>
                    </body></html>
                    """.formatted(server.getAddress().getPort()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            try (exchange; java.io.OutputStream body = exchange.getResponseBody()) {
                body.write(html);
            }
        });
        server.createContext("/site.css", exchange -> {
            byte[] css = "abc".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/css");
            exchange.sendResponseHeaders(200, css.length);
            try (exchange; java.io.OutputStream body = exchange.getResponseBody()) {
                body.write(css);
            }
        });
        server.createContext("/outside/", exchange -> {
            externalCalls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/inline.png", exchange -> {
            byte[] image = "inline".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, image.length);
            try (exchange; java.io.OutputStream body = exchange.getResponseBody()) {
                body.write(image);
            }
        });
        server.createContext("/cover.png", exchange -> {
            byte[] image = "cover".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, image.length);
            try (exchange; java.io.OutputStream body = exchange.getResponseBody()) {
                body.write(image);
            }
        });
        server.start();
        try {
            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            JsonNode page = new ObjectMapper().valueToTree(
                    ProductionBaseline.capturePage(URI.create(origin + "/guide/"), output));
            assertEquals(200, page.path("status").asInt());
            assertTrue(page.path("fragments").toString().contains("old_anchor"));
            assertTrue(page.path("fragments").toString().contains("chapter"));
            assertTrue(page.path("links").toString().contains(origin + "/next/?q=1"));
            assertTrue(page.path("links").toString().contains("part"));
            assertFalse(page.path("links").toString().contains("localhost"));
            assertFalse(page.path("links").toString().contains("mailto"));
            assertEquals(0, externalCalls.get());
            JsonNode css = page.path("assets").get(0);
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", css.path("sha256").asText());
            assertEquals("abc", Files.readString(output.resolve(css.path("path").asText())));
            JsonNode missing = page.path("assets").get(1);
            assertEquals(404, missing.path("status").asInt());
            assertFalse(missing.path("error").asText().isBlank());
            assertEquals(4, page.path("assets").size());
            JsonNode inline = page.path("assets").findParents("url").stream()
                    .filter(asset -> asset.path("url").asText().equals(origin + "/inline.png")).findFirst().orElseThrow();
            assertEquals("995cf20a9c45daaf0a2cc31e85c290032ced97aadbac6c9d625595f5ce0ed427", inline.path("sha256").asText());
            assertEquals("inline", Files.readString(output.resolve(inline.path("path").asText())));
            JsonNode cover = page.path("assets").findParents("url").stream()
                    .filter(asset -> asset.path("url").asText().equals(origin + "/cover.png")).findFirst().orElseThrow();
            assertEquals("cover", Files.readString(output.resolve(cover.path("path").asText())));
            assertTrue(Files.readString(output.resolve(page.path("htmlPath").asText())).contains("id=\"chapter\""));
        } finally {
            server.stop(0);
        }
    }
}
