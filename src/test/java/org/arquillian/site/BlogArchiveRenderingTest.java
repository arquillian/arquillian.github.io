package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/** Exercises generated native release routes, global/tag archives and the intentionally layout-free tag-cloud fragment. */
@QuarkusTest
class BlogArchiveRenderingTest {
    /** Uses the same metadata and body API as the generated archive layout. */
    @Inject SiteBlog blog;
    /** Checks native generated pages rather than treating disposable JSON as proof of published routes. */
    @Inject Site site;

    /** Both the newest Cube and an old generated release retain catalog details, single comments and historical IDs. */
    @Test
    void rendersLatestAndOldGeneratedReleases() {
        List<Page> generated = site.allPages().stream().filter(page -> page instanceof DocumentPage document
                && "blogreleases".equals(document.collectionId())).toList();
        assertEquals(370, generated.size());
        Page latest = generated.stream().filter(page -> page.url().path()
                .equals("/blog/2018/06/13/arquillian-cube-1-16-0/")).findFirst().orElseThrow();
        Page oldest = generated.stream().min(java.util.Comparator.comparing(page -> page.url().path())).orElseThrow();
        for (Page page : List.of(latest, oldest)) {
            JsonObject post = blog.post(page);
            String html = html(post.getString("link"));
            Document document = Jsoup.parse(html);
            assertEquals(post.getString("title") + " · Arquillian Blog", document.title());
            assertEquals(1, document.select("article.post").size());
            assertEquals(post.getString("title"), document.selectFirst("article h2.title").text());
            assertEquals(post.getString("comment_id"), document.selectFirst("article .comments a").attr("data-disqus-identifier"));
            assertEquals(Jsoup.parseBodyFragment(blog.body(post.getString("link"))).text(), document.selectFirst("article > .body").text());
            assertFalse(document.select("article .body table").isEmpty());
            assertEquals(1, document.select("#comments #disqus_thread").size());
            assertEquals(1, document.select("head script[src*='addthis_widget.js']").size());
            assertEquals(1, html.split("arquillian.disqus.com/count.js", -1).length - 1);
        }
    }

    /** A from-data route must not masquerade as authored source or replace the captured publication date. */
    @Test
    void preservesGeneratedReleasePublicationAuthority() {
        String link = "/blog/2018/06/13/arquillian-cube-1-16-0/";
        assertNull(blog.source(link));
        JsonObject post = blog.posts().stream().filter(row -> link.equals(row.getString("link"))).findFirst().orElseThrow();
        assertEquals("2018-06-13", post.getString("date"));
    }

    /** First, middle and last global pages preserve five-item publication order, inherited chrome and pager boundaries. */
    @Test
    void rendersGlobalArchiveBoundaries() {
        for (int number : List.of(1, 2, 122)) archive("", "/blog/", number);
    }

    /** Repeated tag occurrences stay visible so the final release page and all 452 tag pages remain reachable. */
    @Test
    void rendersTagArchiveBoundaries() {
        int last = blog.page("release", 1, 5).getInteger("pages");
        assertEquals(117, last);
        archive("release", "/blog/tags/release/", 1);
        archive("release", "/blog/tags/release/", last);
        assertEquals(452, blog.tags().stream().mapToInt(tag -> blog.page(tag.getString("title"), 1, 5).getInteger("pages")).sum());
    }

    /** Every post and archive exists once in the native registry, with one extra authored tag-cloud route. */
    @Test
    void publishesCompleteNativeRouteUnion() {
        List<Page> posts = site.allPages().stream().filter(page -> !page.draft()
                && page.url().path().matches("/blog/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9._-]+/")).toList();
        assertEquals(606, posts.size());
        assertEquals(606, posts.stream().map(page -> page.url().path()).distinct().count());
        List<Page> archives = site.allPages().stream().filter(page -> page instanceof DocumentPage document
                && "blogarchives".equals(document.collectionId())).toList();
        assertEquals(574, archives.size());
        assertEquals(574, archives.stream().map(page -> page.url().path()).distinct().count());
        assertEquals(1, site.allPages().stream().filter(page -> page.url().path().equals("/blog/tags/")).count());
    }

    /** The public tag route is only the historic weighted fragment, without document chrome or an invented title. */
    @Test
    void rendersTagCloudAsLayoutFreeFragment() {
        String html = html("/blog/tags/");
        assertFalse(html.contains("<html"));
        assertFalse(html.contains("<head"));
        assertFalse(html.contains("id=\"main\""));
        Document fragment = Jsoup.parseBodyFragment(html);
        assertEquals(1, fragment.body().childrenSize());
        assertEquals("tag-cloud", fragment.body().child(0).className());
        assertEquals(115, fragment.select(".tag-cloud span.tag").size());
        for (JsonObject tag : blog.tags()) {
            Element entry = fragment.select(".tag-cloud a").stream().filter(anchor -> anchor.text().equals(tag.getString("title")))
                    .findFirst().orElseThrow();
            assertEquals(tag.getString("href"), entry.attr("href"));
            assertTrue(entry.parent().hasClass("tag-" + tag.getInteger("weight")));
        }
    }

    /** Compares actual HTTP output with the shared runtime selection and body API, including disabled links and scripts. */
    private void archive(String tag, String base, int number) {
        JsonObject selection = blog.page(tag, number, 5);
        String route = number == 1 ? base : base + "page/" + number + "/";
        String html = html(route);
        Document document = Jsoup.parse(html);
        assertEquals("Arquillian Blog · Arquillian", document.title(), route);
        assertEquals("blog index", document.body().className(), route);
        assertEquals("Arquillian Blog", document.selectFirst("#content-header h1 a").text(), route);
        assertEquals("/blog/", document.selectFirst("#content-header h1 a").attr("href"), route);
        List<JsonObject> posts = selection.getJsonArray("posts").stream().map(JsonObject.class::cast).toList();
        assertEquals(posts.stream().map(post -> post.getString("link")).toList(), document.select("#content article h2.title a").eachAttr("href"), route);
        List<Element> articles = document.select("#content article.post");
        for (int index = 0; index < posts.size(); index++) {
            JsonObject post = posts.get(index);
            Element article = articles.get(index);
            assertEquals(post.getString("title"), article.selectFirst("h2.title").text(), route);
            assertEquals(post.getString("comment_id"), article.selectFirst(".comments a").attr("data-disqus-identifier"), route);
            assertEquals(Jsoup.parseBodyFragment(blog.body(post.getString("link"))).text(), article.selectFirst(".body").text(), route);
        }
        assertEquals("Page " + number + " of " + selection.getInteger("pages"), document.selectFirst("ul.pager li.pages").text(), route);
        Integer older = selection.getInteger("next");
        Integer newer = selection.getInteger("previous");
        assertEquals(older == null ? "#" : base + "page/" + older + "/", document.selectFirst("ul.pager li.previous a").attr("href"), route);
        assertEquals(newer == null ? "#" : newer == 1 ? base : base + "page/" + newer + "/", document.selectFirst("ul.pager li.next a").attr("href"), route);
        assertEquals(older == null, document.selectFirst("ul.pager li.previous").hasClass("disabled"), route);
        assertEquals(newer == null, document.selectFirst("ul.pager li.next").hasClass("disabled"), route);
        assertTrue(document.select("#comments,script[src*='addthis_widget.js'],.addthis_toolbox").isEmpty(), route);
        assertEquals(1, html.split("arquillian.disqus.com/count.js", -1).length - 1, route);
        assertEquals(5, document.select("#sidebar nav.latest li").size(), route);
        assertEquals(115, document.select("#sidebar nav.tags .tag-cloud span.tag").size(), route);
    }

    /** Requests a native route through the real Quarkus HTTP listener. */
    private static String html(String link) {
        return given().when().get(link).then().statusCode(200).extract().asString();
    }
}
