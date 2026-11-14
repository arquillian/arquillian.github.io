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

/** Verifies Roq aliases preserve legacy navigation without creating duplicate blog articles or comment identities. */
@QuarkusTest
class RedirectRenderingTest {
    /** Uses the actual post registry to detect accidental collection of public redirect assets. */
    @Inject
    SiteBlog blog;

    /** Exercises each configured directory alias against the native HTTP server. */
    @TestFactory
    Stream<DynamicTest> preservesConfiguredRedirects() {
        return List.of(
                "/blog/2016/09/23/arquillian-pact-1-0-0-Alpha1/",
                "/blog/2016/09/29/arquillian-pact-1-0-0-Alpha2/",
                "/blog/2016/11/03/arquillian-pact-1-0-0-Alpha3/",
                "/blog/2016/11/14/arquillian-pact-1-0-0-Alpha4/")
                .stream().map(source -> DynamicTest.dynamicTest(source, () -> {
                    String targetPath = source.replace("arquillian-pact", "arquillian-algeron");
                    String target = "https://arquillian.org" + targetPath;
                    String html = given().when().get(source)
                            .then().statusCode(200).extract().asString();
                    Document document = Jsoup.parse(html);
                    assertEquals("utf-8", document.selectFirst("meta[charset]").attr("charset"));
                    assertEquals("0; url=" + target, document.selectFirst("meta[http-equiv=refresh]").attr("content"));
                    assertEquals("Redirecting…", document.title());
                    assertEquals(target, document.selectFirst("a[href]").attr("href"));
                    assertEquals("Click here if you are not redirected.", document.selectFirst("a[href]").text());
                    assertTrue(html.contains("<meta name=\"robots\" content=\"noindex\">"));
                    assertTrue(html.contains("<script>location=\"" + target + "\"</script>"));
                    Document destination = Jsoup.parse(given().when().get(targetPath)
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
