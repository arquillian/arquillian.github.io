package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.qute.Engine;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.ValueResolver;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Protects shared ordinary-page layout structure and page-specific browser asset ordering. */
class OrdinaryLayoutsTest {
    /** Keeps optional assets ordered around common libraries without creating duplicate site chrome. */
    @Test
    void ordersOptionalPageAssets() throws IOException {
        FixturePage page = new FixturePage("/example/", "<p>Body</p>");
        page.data().put("stylesheets", new JsonArray().add("/page.css"))
                .put("javascripts", new JsonArray().add("/head.js"))
                .put("bottom_javascripts", new JsonArray().add("/bottom.js"));
        String value = render(page, "{#include layouts/base.html}{#include layouts/default.html}<p>Body</p>{/include}{/include}");
        assertTrue(value.indexOf("jquery.min.js") < value.indexOf("/head.js"));
        assertTrue(value.indexOf("/head.js") < value.indexOf("</head>"));
        assertTrue(value.indexOf("/javascripts/site.js") < value.indexOf("/bottom.js"));
        assertTrue(value.indexOf("/bottom.js") < value.indexOf("apis.google.com"));
        Document document = Jsoup.parse(value);
        assertEquals(1, document.select("link[href='/page.css']").size());
        assertEquals(1, document.select("#banner").size());
        assertEquals(1, document.select("#main").size());
        assertEquals(1, document.select("footer").size());
        FixturePage plain = new FixturePage("/example/", "Body");
        assertFalse(render(plain, "{#include layouts/base.html}Body{/include}").contains("NOT_FOUND"));
    }

    /** Shares the eight sidebar links and exercises both nested and legacy invasion bodies with literal templates. */
    @Test
    void rendersInvasionSidebarAndAuthoredContent() throws IOException {
        for (String route : List.of("/invasion/", "/invasion/team/", "/invasion/origins/", "/invasion/buzz/",
                "/invasion/events/", "/invasion/press/", "/invasion/videos/", "/invasion/spread/")) {
            FixturePage page = new FixturePage(route, "<div class='paragraph'><p>Body</p></div><script>{{:title}}</script>");
            Document document = Jsoup.parse(render(page,
                    "{#include layouts/invasion.html}<p>Body</p><script>{{:title}}</script>{/include}"));
            assertEquals(8, document.select("#sidebar .tab-group #tab > li > a").size());
            assertEquals(1, document.select("#tab > li.active").size());
            assertEquals(route.equals("/invasion/") ? route : route.substring(0, route.length() - 1),
                    document.selectFirst("#tab > li.active a").attr("href"));
            assertEquals("Body", document.selectFirst("#content > p").text());
            assertEquals("{{:title}}", document.selectFirst("#content script").data());
        }
        Document authored = Jsoup.parse(render(new FixturePage("/community/", "<div class='paragraph'><p>Prose</p></div>"),
                "{#include layouts/authored.html /}"));
        assertEquals("Prose", authored.selectFirst("p").text());
        assertTrue(authored.select("div.paragraph").isEmpty());
    }

    /** Parses real layout bodies with native Roq pages and Qute's configured alternative expression syntax. */
    private static String render(FixturePage page, String template) throws IOException {
        Engine engine = Engine.builder().configureParser((id, variant) -> new ParserConfig('='))
                .addDefaults().addValueResolver(io.quarkus.qute.ValueResolvers.rawResolver()).addValueResolver(new ReflectionValueResolver())
                .addValueResolver(ValueResolver.builder().appliesTo(context -> context.getBase() instanceof JsonObject)
                        .resolveSync(context -> ((JsonObject) context.getBase()).getValue(context.getName())).build())
                .addNamespaceResolver(NamespaceResolver.builder("cdi").resolve(context -> new LegacyMarkup()).build()).build();
        for (String name : List.of("layouts/base", "layouts/default", "layouts/invasion", "layouts/authored", "partials/header", "partials/footer")) {
            String source = Files.readString(Path.of("templates/" + name + ".html"));
            if (source.startsWith("---")) source = source.split("---", 3)[2];
            engine.putTemplate(name + ".html", engine.parse(source));
        }
        return engine.parse(template).data("page", page).render();
    }

    /** Supplies renderer output through the native page contract without activating a public fixture route. */
    public static class FixturePage extends Page {
        private final String body;

        /** Gives the layout a real public URL, metadata and source while isolating authored body rendering. */
        FixturePage(String route, String body) {
            super(new RoqUrl(new RootUrl("https://arquillian.org", "/"), route),
                    new PageSource(TemplateSource.create("fixture.html", "html", new SourceFile(".", "fixture.html"),
                            "fixture.html", "fixture.html", false, true, false, false), false, "2026-11-13", PageFiles.empty(), false),
                    new JsonObject().put("title", "Fixture"));
            this.body = body;
        }

        /** Returns already-rendered body text so literal browser templates are never parsed twice. */
        @Override
        public String content() {
            return body;
        }
    }
}
