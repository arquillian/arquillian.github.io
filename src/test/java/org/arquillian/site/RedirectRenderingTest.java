package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Verifies native static redirects retain the old navigation without creating blog articles or comment identities. */
@QuarkusTest
class RedirectRenderingTest {
    /** Uses the actual post registry to detect accidental collection of public redirect assets. */
    @Inject
    SiteBlog blog;

    /** Exercises each physical index and its directory aliases against the native HTTP server. */
    @TestFactory
    Stream<DynamicTest> preservesConfiguredRedirects() {
        return List.of(
                "/blog/2016/09/23/arquillian-pact-1-0-0-Alpha1",
                "/blog/2016/09/29/arquillian-pact-1-0-0-Alpha2",
                "/blog/2016/11/03/arquillian-pact-1-0-0-Alpha3",
                "/blog/2016/11/14/arquillian-pact-1-0-0-Alpha4")
                .stream().map(source -> DynamicTest.dynamicTest(source, () -> {
                    String target = source.replace("arquillian-pact", "arquillian-algeron");
                    String html = given().redirects().follow(false).when().get(source + "/index.html")
                            .then().statusCode(200).extract().asString();
                    Document document = Jsoup.parse(html);
                    assertEquals("UTF-8", document.selectFirst("meta[charset]").attr("charset"));
                    assertEquals("5; url=" + target, document.selectFirst("meta[http-equiv=refresh]").attr("content"));
                    assertEquals("Page Redirection", document.title());
                    assertEquals(target, document.selectFirst("a[href]").attr("href"));
                    assertEquals("right place", document.selectFirst("a[href]").text());
                    assertTrue(html.contains("window.location.href = \"" + target + "\""));
                    assertTrue(html.contains("//www.google-analytics.com/analytics.js"));
                    assertTrue(html.contains("ga('create', 'UA-18727998-3', 'auto');"));
                    assertTrue(html.contains("ga('send', 'pageview');"));
                    assertTrue(document.body().text().contains("Looks like you used an old link."));
                    assertTrue(document.select("article, header, footer, #sidebar, #disqus_thread").isEmpty());
                    assertFalse(html.contains("disqus"));
                    assertEquals(html, given().when().get(source + "/").then().statusCode(200).extract().asString());
                    Document destination = Jsoup.parse(given().when().get(target + "/")
                            .then().statusCode(200).extract().asString());
                    assertEquals(1, destination.select("article.post").size());
                    assertEquals(1, destination.select("#disqus_thread").size());
                }));
    }

    /** Keeps redirects out of post counts and feed metadata so the original 606 articles retain their identities. */
    @Test
    void keepsRedirectsOutOfBlogPosts() {
        assertEquals(606, blog.posts().size());
        assertFalse(blog.posts().stream().anyMatch(post -> post.getString("link").contains("/arquillian-pact-")));
    }
}
