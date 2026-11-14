package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Checks authored public routes through native HTTP so shared layouts and prose formatting stay integrated. */
@QuarkusTest
class OrdinaryPagesRenderingTest {
    /** Preserves historical titles, body flags, landmarks and active global navigation on each authored route. */
    @Test
    void rendersAuthoredRoutes() {
        Map<String, String> pages = Map.ofEntries(
                Map.entry("/features/", "Feature Tour"), Map.entry("/community/", "Community"),
                Map.entry("/community/nobles/", "Arquillian Nobles"), Map.entry("/community/webchat/", "Web-based Chat"),
                Map.entry("/invasion/", "Arquillian Invasion!"), Map.entry("/invasion/origins/", "Origins"),
                Map.entry("/invasion/buzz/", "Transmissions"),
                Map.entry("/invasion/press/", "Press"), Map.entry("/invasion/videos/", "Videos"),
                Map.entry("/invasion/spread/", "Spread Ike!"), Map.entry("/code/", "Code Overview"),
                Map.entry("/code/architecture/", "Architecture"), Map.entry("/code/issues/", "Issue Activity"),
                Map.entry("/code/builds/", "Build Status"));
        for (Map.Entry<String, String> entry : pages.entrySet()) {
            String route = entry.getKey();
            Document document = page(route);
            assertEquals(entry.getValue() + " · Arquillian", document.title(), route);
            assertEquals(entry.getValue(), document.selectFirst("#content-header h1").text(), route);
            assertEquals(1, document.select("#main[role=main]").size(), route);
            assertEquals(1, document.select("#banner[role=banner]").size(), route);
            assertEquals(1, document.select("#banner nav[role=navigation]").size(), route);
            assertEquals(1, document.select("footer").size(), route);
            assertEquals(route.equals("/community/webchat/") ? 0 : 1, document.select("#content").size(), route);
            String bodyClass = route.startsWith("/invasion/") ? "invasion" : route.equals("/features/") ? "tour" : "";
            assertEquals(bodyClass, document.body().className(), route);
            assertEquals(route.startsWith("/invasion/") ? "/invasion/" : route.startsWith("/code/") ? "/modules/"
                            : route.equals("/features/") ? "/features/" : "/community/",
                    document.selectFirst("#banner li.active a").attr("href"), route);
            assertFalse(document.html().contains("NOT_FOUND"), route);
            if (bodyClass.equals("invasion")) {
                assertEquals(8, document.select("#sidebar #tab > li").size(), route);
                assertEquals(1, document.select("#sidebar #tab > li.active").size(), route);
                String href = route.equals("/invasion/") ? route : route.substring(0, route.length() - 1);
                assertEquals(href, document.selectFirst("#tab > li.active > a").attr("href"), route);
            }
        }
    }

    /** Keeps all feature headings and authored guide targets available instead of flattening the feature tour. */
    @Test
    void preservesFeatureTour() {
        Document document = page("/features/");
        assertEquals(List.of("Real Tests", "IDE Friendly", "Test Enrichment", "Classpath Control", "Drive the Browser",
                "Debug the Server", "Container Agnostic", "Extensible Platform", "Strong Tooling"),
                document.select("#content.features > .feature > .summary > h2").eachText());
        assertEquals(9, document.select("#content .preview > img").size());
        assertEquals(List.of("/guides/getting_started/#a_closer_look", "/guides/getting_started/#run_the_arquillian_test",
                "/guides/getting_started/#a_closer_look", "/guides/getting_started/#write_an_arquillian_test",
                "/guides/functional_testing_using_graphene", "/guides/getting_started/#debug_the_test",
                "/guides/getting_started/#add_more_containers", "/guides/get_started_faster_with_forge"),
                document.select("#content .learn > a").eachAttr("href"));
        assertEquals("Death to all bugs!", document.selectFirst("#content p.rally strong").text());
    }

    /** Guards community prose roles and historical noble-table cells, including the published issue-link spellings. */
    @Test
    void preservesCommunityMarkup() {
        Document community = page("/community/");
        assertEquals(1, community.select("#content > #intro").size());
        assertEquals(List.of("Forums and Wiki", "Chat (IRC)", "Issue Tracker", "Source Repository", "Arquillian Nobles"),
                community.select("#content .section-area > h2.icon").eachText());
        assertEquals(4, community.select(".screenshot > img").size());
        assertEquals(List.of("webchat", "nobles", "contributors"),
                community.select("#content a[href=webchat],#content a[href=nobles],#content a[href=contributors]").eachAttr("href"));
        Document nobles = page("/community/nobles/");
        assertEquals(5, nobles.select("#content table.data").size());
        assertEquals(24, nobles.select("#content table.data td").size());
        assertTrue(nobles.select("#content table.data colgroup,#content table.data thead").isEmpty());
        assertTrue(nobles.selectFirst("#content p.info > span").text().startsWith("Community members may be crowned multiple times."));
        assertEquals("ARQ-225", nobles.selectFirst("a[href='https://issues.jboss.org/browse/ARQ-255']").text());
        assertEquals("SHRINKWRAP-163", nobles.selectFirst("a[href='https://issues.jboss.org/browse/SHRINKWAP-163']").text());
    }

    /** Preserves the historical embedded chat URL and transcript link without depending on the external service. */
    @Test
    void preservesWebChatHooks() {
        Document document = page("/community/webchat/");
        assertEquals("http://webchat.freenode.net?channels=arquillian&uio=MT1mYWxzZSY3PWZhbHNlJjExPTIzNg0c",
                document.selectFirst("#main iframe").attr("src"));
        assertEquals("width: 100%; height: 500px; border: 1px solid #C3C5E0", document.selectFirst("#main iframe").attr("style"));
        assertEquals("transcripts.jboss.org", document.selectFirst("#main a[href='http://transcripts.jboss.org/channel/irc.freenode.org/%23arquillian/']").text());
        assertTrue(document.selectFirst("#main > .container > p").text().contains("Simply enter a nickname, answer the CAPTCHA challenge, then click Connect."));
    }

    /** Preserves mission roles, origin definitions and the original Twitter hook and complete testimonial collection. */
    @Test
    void preservesInvasionMarkupAndScripts() {
        Document mission = page("/invasion/");
        assertTrue(mission.selectFirst("#content p.well").text().startsWith("Arquillian is an innovative and highly extensible testing platform for the JVM"));
        assertEquals(6, mission.select("#content > ul > li").size());
        assertEquals(3, mission.select("#content dt").size());
        assertTrue(mission.select("#content dd > p").isEmpty());
        Document origins = page("/invasion/origins/");
        assertEquals(5, origins.select("#content dt").size());
        assertTrue(origins.select("#content dd > p").isEmpty());
        assertEquals(List.of("The formation of Arquillian", "JBoss Testing", "Contributions", "About the name"),
                origins.select("#content h2").eachText());
        assertEquals("JSR-346", origins.select("#content a[href='http://jcp.org/en/jsr/detail?id=299']").getLast().text());
        Document buzz = page("/invasion/buzz/");
        assertEquals("765158390444285952", buzz.selectFirst("#content .twitter-timeline").attr("data-widget-id"));
        assertEquals("https://twitter.com/hashtag/arquillian", buzz.selectFirst("#content .twitter-timeline").attr("href"));
        assertEquals(12, buzz.select("#content blockquote").size());
        assertEquals(12, buzz.select("#content blockquote .source").size());
        assertTrue(buzz.selectFirst("#content .tweet script").data().contains("twitter-wjs"));
        assertTrue(buzz.selectFirst("#content .tweet script").data().contains("://platform.twitter.com/widgets.js"));
    }

    /** Preserves the custom hosting error page, its original public file path and the illustrated home link. */
    @Test
    void preservesCustomNotFoundPage() {
        Document document = page("/404.html");
        assertEquals("The battle of the bugs continue... · Arquillian", document.title());
        assertEquals("The battle of the bugs continue...", document.selectFirst("#content-header h1").text());
        assertEquals("...but this is not the correct direction.", document.selectFirst("#content p.rally").text());
        assertEquals("/", document.selectFirst("#content a").attr("href"));
        assertEquals("/images/404_ike.png", document.selectFirst("#content img").attr("src"));
        assertEquals("512", document.selectFirst("#content img").attr("width"));
        assertEquals("512", document.selectFirst("#content img").attr("height"));
    }

    /** Guards literal client templates, feed callbacks and script order against Qute evaluation or lost metadata. */
    @Test
    void preservesFeedScriptsAndClientTemplates() {
        Map<String, String> templates = Map.of(
                "/invasion/press/", "{{:publishedDateMonth}}",
                "/invasion/videos/", "{{:~format_duration(duration)}}",
                "/code/issues/", "{{:contentSnippet}}",
                "/code/builds/", "{{:~getBuildStatusImage(~extractComment(title), 32)}}");
        Map<String, String> callbacks = Map.of(
                "/invasion/press/", "$('#diigo').html($('#diigo-tmpl').render(feeds.entries))",
                "/invasion/videos/", "function display_videos(data)",
                "/code/issues/", "$('#issues tbody').html($('#issues-tmpl').render(feeds.entries))",
                "/code/builds/", "$('#builds tbody').html($('#status-tmpl').render(feeds.entries.sort(sort_title)))");
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            String value = given().when().get(entry.getKey()).then().statusCode(200).extract().asString();
            assertTrue(value.contains(entry.getValue()), entry.getKey());
            assertTrue(value.contains(callbacks.get(entry.getKey())), entry.getKey());
            assertTrue(value.indexOf("jquery.min.js") < value.indexOf("/javascripts/siphon-20120407.js"), entry.getKey());
            assertTrue(value.indexOf("/javascripts/siphon-20120407.js") < value.indexOf("</head>"), entry.getKey());
        }
        Document press = page("/invasion/press/");
        assertEquals("text/x-jsquery", press.selectFirst("#diigo-tmpl").attr("type"));
        assertTrue(press.selectFirst("#diigo-tmpl").data().contains("{{:content}}"));
        assertEquals(1, press.select("#diigo.feed").size());
        Document videos = page("/invasion/videos/");
        assertEquals("text/x-jsrender", videos.selectFirst("#video_tmpl").attr("type"));
        assertTrue(videos.selectFirst("#video_tmpl").data().contains("{{if ~not_last()}}"));
        assertTrue(videos.selectFirst("#content > script:not([id])").data().contains("jsonpCallback: 'display_videos'"));
        assertEquals("Arquillian Videos on Vimeo", videos.selectFirst("#content h2").text());
        assertEquals(1, videos.select("#videos").size());
    }

    /** Keeps downloadable artwork, image styling and source-authored wallpaper dimensions available to visitors. */
    @Test
    void preservesSpreadArtwork() {
        Document document = page("/invasion/spread/");
        assertEquals(List.of("Logos", "Icons", "Emotional Ike Icons", "Desktop Wallpaper", "Swag"),
                document.select("#content h3").eachText());
        assertEquals("Don’t mock me. Write real tests!", document.selectFirst("#content blockquote").text());
        assertEquals("Desktop Wallpaper", document.selectFirst("#content h3.clear").text());
        assertEquals("float: right; margin-right: 1.5em; margin-left: 10px;", document.selectFirst("#content img[src=/images/cool_stuff.jpg]").attr("style"));
        assertEquals("1024x768", document.selectFirst("#content a[href='https://www.jboss.org/dms/coolstuff/desktops/arquillian/arquillian_desktop_1024x768.jpg']").text());
        assertEquals(10, document.select("#content img").size());
        assertEquals(2, document.select("#content a[href='http://www.cafepress.com/jbossorg/7125835'] > img").size());
    }

    /** Keeps repository browsing, architecture and status navigation reachable from the code overview. */
    @Test
    void preservesCodeOverviewLinks() {
        Document document = page("/code/");
        assertEquals("http://www.ohloh.net/p/480465/widgets/project_basic_stats.js", document.selectFirst("#content script").attr("src"));
        assertEquals(List.of("/code/architecture/", "/modules/", "http://github.com/arquillian/arquillian-showcase", "/code/issues/", "/code/builds/", "/code/dashboard/"),
                document.select("#content > ul > li > a").eachAttr("href"));
        assertEquals("Fork us!", document.selectFirst("#content strong a[href='https://github.com/arquillian']").text());
        assertEquals("/images/octocat_social.png", document.selectFirst("#content a[href='https://github.com/arquillian'] > img").attr("src"));
    }

    /** Preserves all architecture tab targets and literal injection annotations for Bootstrap's tab navigation. */
    @Test
    void preservesArchitectureTabs() {
        Document document = page("/code/architecture/");
        List<String> fragments = List.of("test-runners", "deployments", "test-enrichers", "containers", "run-modes", "extensions");
        assertEquals(fragments, document.select("#content .tab-content > .tab-pane").eachAttr("id"));
        assertEquals(List.of("#test-runners", "#deployments", "#test-enrichers", "#containers", "#run-modes", "#extensions"),
                document.select("#main-menu a[data-toggle=tab]").eachAttr("href"));
        assertEquals("test-runners", document.selectFirst(".tab-pane.active").id());
        assertEquals(1, document.select(".tab-pane.active").size());
        assertEquals(List.of("@RunWith(Arquillian.class)", "Arquillian", "@Inject", "@Resource", "@EJB", "@PersistenceUnit", "@PersistenceContext"),
                document.select("#content code").eachText());
        assertEquals(3, document.select("#containers ol > li").size());
    }

    /** Retains issue/build table targets and callbacks outside content so feed entries can still populate the tables. */
    @Test
    void preservesCodeFeedTargets() {
        Document issues = page("/code/issues/");
        assertEquals("text/x-jsquery", issues.selectFirst("#issues-tmpl").attr("type"));
        assertEquals(1, issues.select("#issues.table.table-striped > tbody").size());
        assertTrue(issues.selectFirst("#content + script").data().contains("http://issues.jboss.org/activity?streams=key+IS+ARQ"));
        assertEquals("https://issues.jboss.org/browse/ARQ", issues.selectFirst("#content > p > a").attr("href"));
        Document builds = page("/code/builds/");
        assertEquals("text/x-jsrender", builds.selectFirst("#status-tmpl").attr("type"));
        assertEquals(List.of("Module", "Status", "Build", "Comment"), builds.select("#builds th").eachText());
        assertEquals(1, builds.select("#builds.table.table-bordered.table-striped > tbody").size());
        assertTrue(builds.selectFirst("#content + script").data().contains("https://arquillian.ci.cloudbees.com/rssLatest"));
        assertTrue(builds.selectFirst("#status-tmpl").data().contains("{{:link}}{{:href}}"));
        assertEquals("/images/cloudbees_logo.png", builds.selectFirst("#content a[href='https://arquillian.ci.cloudbees.com'] > img").attr("src"));
    }

    /** Fetches native rendered output so tests exercise Roq routing and the production layout pipeline. */
    private static Document page(String route) {
        return Jsoup.parse(given().when().get(route).then().statusCode(200).extract().asString());
    }
}
