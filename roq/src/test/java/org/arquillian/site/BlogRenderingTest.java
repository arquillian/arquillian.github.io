package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.junit.jupiter.api.Test;

/** Exercises actual authored Roq pages so metadata fixtures cannot hide content/layout integration failures. */
@QuarkusTest
class BlogRenderingTest {
    /** Uses the actual native page registry and body renderer rather than migration capture files. */
    @Inject
    SiteBlog blog;

    /** Injects the native registry directly because CDI client-proxy fields do not hold bean state. */
    @Inject
    Site site;

    /** Preserves the oldest article's native prose, attribution, original comment identity and disabled previous link. */
    @Test
    void rendersAuthoredArticle() {
        String link = "/blog/2010/03/10/testing-javaee-the-jboss-way/";
        String html = given().when().get(link).then().statusCode(200).extract().asString();
        Document document = Jsoup.parse(html);
        assertEquals("Testing Java EE the JBoss way · Arquillian Blog", document.title());
        assertTrue(document.body().hasClass("blog"));
        assertEquals("Testing Java EE the JBoss way", document.selectFirst("article.post h2.title a").text());
        assertEquals("Pete Muir", document.selectFirst("article.post .byline .author a").text());
        assertTrue(document.selectFirst("article.post .body").text().contains("Congratulations to Aslak"));
        assertFalse(document.select("article.post .body blockquote").isEmpty());
        assertEquals(link, document.selectFirst("article.post .footer > a").attr("href"));
        assertTrue(html.contains("3fe8b36048034b0b3eed79eca0d1271ee643cb3e"));
        assertTrue(document.selectFirst("ul.pager li.previous").hasClass("disabled"));
        assertEquals("Post 1 of 606", document.selectFirst("ul.pager li.pages").text());
        assertFalse(document.select("#sidebar nav.tags a").isEmpty());
    }

    /** Combines the complete editable release narrative with the same catalog details used by generated releases. */
    @Test
    void rendersAuthoredRelease() {
        String link = "/blog/2018/03/29/arquillian-cube-1-15-3/";
        String html = given().when().get(link).then().statusCode(200).extract().asString();
        Document document = Jsoup.parse(html);
        assertEquals("Arquillian Cube Extension 1.15.3 Released · Arquillian Blog", document.title());
        assertEquals("Dipak Pawar", document.selectFirst("article.post .byline .author a").text());
        assertTrue(document.selectFirst("article.post .body").text().contains("Fabric8 Maven Plugin integration"));
        assertTrue(document.selectFirst("article.post .body").text().contains("Release details"));
        assertTrue(document.selectFirst("article.post .body").text().contains("1.15.3"));
        assertFalse(document.select("article.post .body pre.prettify code.prettify").isEmpty());
        assertFalse(document.select("article.post .body p.info > span a").isEmpty());
        assertTrue(html.contains("7a907243a3838bc8efb61838e7cc80b06c2a1f6b"));
        assertEquals("Post 604 of 606", document.selectFirst("ul.pager li.pages").text());
        assertEquals(java.util.List.of("release", "extension", "cube"), document.select("article.post .footer a.tag").eachText());
    }

    /** Drops Textile-only escape slashes while retaining literal JSF expressions inside an editable code example. */
    @Test
    void rendersWarpManagedPropertyExampleWithoutTextileEscapes() {
        String link = "/blog/2013/06/20/arquillian-extension-warp-1-0-0-Alpha3/";
        Document document = Jsoup.parse(given().when().get(link).then().statusCode(200).extract().asString());
        String listing = document.select("article.post .body pre code").stream().map(Element::wholeText)
                .filter(text -> text.contains("facesContext.application")).findFirst().orElseThrow();
        assertTrue(listing.contains("@ManagedProperty(\"#{facesContext.application}\")"));
        assertTrue(listing.contains("@ManagedProperty(\"#{myBean}\")"));
        assertFalse(listing.contains("\\#{"));
    }

    /** Keeps the rendered opening quotation mark from the published Devoxx prose. */
    @Test
    void rendersDevoxxQuotation() {
        String link = "/blog/2012/11/15/devoxx-2012-hackergarten-overdrive/";
        Document document = Jsoup.parse(given().when().get(link).then().statusCode(200).extract().asString());
        assertTrue(document.selectFirst("article.post .body").text().contains("“2 CDs”"));
    }

    /** Preserves whitespace inside font-sized tag spans because those gaps determine published tag positions. */
    @Test
    void preservesTagCloudInternalWhitespace() {
        List<JsonObject> expected = blog.tags();
        assertEquals(115, expected.size());
        for (String link : List.of("/blog/2010/03/10/testing-javaee-the-jboss-way/",
                "/blog/2018/03/29/arquillian-cube-1-15-3/")) {
            Document document = Jsoup.parse(given().when().get(link).then().statusCode(200).extract().asString());
            List<Element> spans = document.select("#sidebar nav.tags .tag-cloud > span");
            assertEquals(expected.size(), spans.size(), link);
            for (int i = 0; i < expected.size(); i++) {
                JsonObject tag = expected.get(i);
                Element span = spans.get(i);
                String context = link + " tag " + tag.getString("title");
                assertEquals("tag tag-" + tag.getInteger("weight"), span.className(), context);
                assertEquals(3, span.childNodeSize(), context);
                TextNode before = assertInstanceOf(TextNode.class, span.childNode(0), context);
                TextNode after = assertInstanceOf(TextNode.class, span.childNode(2), context);
                assertTrue(!before.getWholeText().isEmpty() && before.getWholeText().isBlank(), context);
                assertTrue(!after.getWholeText().isEmpty() && after.getWholeText().isBlank(), context);
                assertEquals("a", span.child(0).tagName(), context);
                assertEquals(tag.getString("href"), span.child(0).attr("href"), context);
                assertEquals(tag.getString("title"), span.child(0).text(), context);
            }
        }
    }

    /** Retains the published popular-post widget hooks and conditional fallback on both authored article forms. */
    @Test
    void rendersLegacyPopularPostsWidget() {
        for (String link : List.of("/blog/2010/03/10/testing-javaee-the-jboss-way/",
                "/blog/2018/03/29/arquillian-cube-1-15-3/")) {
            Document document = Jsoup.parse(given().when().get(link).then().statusCode(200).extract().asString());
            assertEquals(1, document.select("#sidebar nav.latest #popularposts.dsq-widget").size(), link);
            assertTrue(document.select("#popularthreads").isEmpty(), link);
            assertEquals("Popular Posts", document.selectFirst("#popularposts > h2.dsq-widget-title").text(), link);
            assertEquals(List.of("h2", "script", "script", "noscript"),
                    document.selectFirst("#popularposts").children().stream().map(element -> element.tagName()).toList(), link);
            assertEquals("http://arquillian.disqus.com/popular_threads_widget.js?num_items=5",
                    document.selectFirst("#popularposts > script[src]").attr("src"), link);
            String fallback = document.select("#popularposts > script").getLast().data();
            assertTrue(fallback.contains("document.getElementById(\"popularposts\")"), link);
            assertTrue(fallback.contains("if (ppw.getElementsByTagName('li').length == 0)"), link);
            assertTrue(fallback.contains("p.className = 'noresults'"), link);
            assertTrue(fallback.contains("document.createTextNode('No popular posts reported.')"), link);
            assertTrue(fallback.contains("ppw.appendChild(p)"), link);
            assertTrue(document.select("#popularposts > p.noresults").isEmpty(), link);
            assertEquals("Please enable JavaScript to view the popular posts powered by Disqus.",
                    document.selectFirst("#popularposts > noscript p").text(), link);
            assertEquals("http://disqus.com/?ref_noscript",
                    document.selectFirst("#popularposts > noscript a").attr("href"), link);
        }
    }

    /** Every authored native public route renders its narrative and one shared article with the expected historical metadata. */
    @Test
    void rendersAll236AuthoredPublicRoutes() {
        List<Page> authored = site.allPages().stream().filter(page -> !page.draft() && !page.source().generated()
                && !(page instanceof DocumentPage document && "blogreleases".equals(document.collectionId()))
                && page.url().path().matches("/blog/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9._-]+/")).toList();
        assertEquals(236, authored.size());
        assertEquals(212L, authored.stream().filter(page -> blog.post(page).getBoolean("release")).count());
        assertEquals(606, blog.posts().size());
        for (Page page : authored) {
            String link = page.url().path();
            JsonObject post = blog.post(page);
            String html = given().when().get(link).then().statusCode(200).extract().asString();
            Document document = Jsoup.parse(html);
            assertEquals(1, document.select("#main[role=main]").size(), link);
            assertEquals(1, document.select("article.post").size(), link);
            assertEquals(post.getString("title"), document.selectFirst("article.post h2.title a").text(), link);
            assertEquals("Posted by " + post.getString("author"), document.selectFirst("article .byline .author").text(), link);
            assertEquals("on " + post.getString("display_date"), document.selectFirst("article .dateinline").text(), link);
            assertEquals(post.getString("comment_id"), document.selectFirst("article .comments a").attr("data-disqus-identifier"), link);
            assertEquals(1, document.select("#comments #disqus_thread").size(), link);
            assertEquals(1, html.split("arquillian.disqus.com/count.js", -1).length - 1, link);
            assertEquals(1, document.select("head script[src*='addthis_widget.js']").size(), link);
            assertEquals(1, document.select("script[src*='addthis_widget.js']").size(), link);
            assertFalse(document.selectFirst("article > .body").text().isBlank(), link);
            assertEquals(Jsoup.parseBodyFragment(blog.body(link)).text(), document.selectFirst("article > .body").text(), link);
            assertEquals(5, document.select("#sidebar nav.latest li").size(), link);
        }
    }

    /** Real authored release edits update the browser, article, sidebar and latest-news API while preserving release details and thread IDs. */
    @Test
    void publishesEditableReleaseMetadataWithoutChangingProtectedIdentity() {
        String link = "/blog/2018/03/29/arquillian-cube-1-15-3/";
        Page source = blog.source(link);
        JsonObject original = source.data().copy();
        JsonObject before = blog.post(source);
        JsonObject details = blog.release(link);
        try {
            source.data().put("title", "Edited authored release").put("author", "Unbound author")
                    .put("date", "2030-02-03").put("tags", new JsonArray().add("edited-tag"))
                    .put("disqus_identifier", "attempted-source-override");
            Document document = Jsoup.parse(given().when().get(link).then().statusCode(200).extract().asString());
            assertEquals("Edited authored release · Arquillian Blog", document.title());
            assertEquals("Edited authored release", document.selectFirst("article h2.title").text());
            assertEquals("Posted by Unbound author", document.selectFirst("article .byline .author").text());
            assertTrue(document.select("article .byline .author a, article .byline img.avatar").isEmpty());
            assertEquals("on Feb 03, 2030", document.selectFirst("article .dateinline").text());
            assertEquals(List.of("edited-tag"), document.select("article .footer a.tag").eachText());
            assertEquals("Edited authored release", document.selectFirst("#sidebar nav.latest li .title a").text());
            assertEquals("Feb 03, 2030", document.selectFirst("#sidebar nav.latest li .meta").text());
            JsonObject latest = blog.latest(3).getFirst();
            assertEquals(link, latest.getString("link"));
            assertEquals("Posted by Unbound author on Feb 03, 2030", latest.getString("homepage_avatar_title"));
            assertEquals(before.getString("comment_id"), latest.getString("comment_id"));
            assertEquals(before.getString("disqus_url"), latest.getString("disqus_url"));
            assertEquals(before.getString("comment_id"), document.selectFirst("article .comments a").attr("data-disqus-identifier"));
            assertEquals(details.getString("date"), blog.release(link).getString("date"));
            assertEquals(details.getJsonArray("details"), blog.release(link).getJsonArray("details"));
            assertTrue(document.selectFirst("article .body table").text().contains("2018-03-29"));
        } finally {
            source.data().clear().mergeIn(original);
        }
    }

    /** The homepage API keeps all three published two-sentence excerpts and separate homepage/byline avatar titles. */
    @Test
    void computesPublishedHomepageExcerptsFromSharedBodies() {
        List<JsonObject> latest = blog.latest(3);
        assertEquals(List.of(
                "The Arquillian team is proud to announce the 1.16.0 release of the Arquillian Cube Extension component! What is Arquillian Cube Extension? With Arquillian Cube you can control the lifecycle of Docker images as part of the test lifecyle, either automatically or manually....",
                "The Arquillian team is proud to announce the 1.0.0 release of the Arquillian Extension Byteman component! What is Arquillian? Arquillian is open source software that empowers you to test JVM-based applications more effectively....",
                "Since we wrote this post we didn't laze around. Check our latest announcement...."),
                latest.stream().map(post -> blog.excerpt(post.getString("link"))).toList());
        assertEquals("Posted by Dipak Pawar on Jun 13, 2018", latest.getFirst().getString("homepage_avatar_title"));
        assertEquals("", latest.getFirst().getString("avatar_title"));
        String generated = "/blog/2018/06/13/arquillian-cube-1-16-0/";
        assertNull(blog.source(generated));
        assertTrue(blog.body(generated).contains("Release details"));
        assertTrue(Jsoup.parseBodyFragment(blog.body(generated)).select("article,#comments,script").isEmpty());
    }

}
