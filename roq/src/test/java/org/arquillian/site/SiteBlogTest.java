package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.PageFiles;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.model.SourceFile;
import io.quarkiverse.roq.frontmatter.runtime.model.TemplateSource;
import io.quarkus.qute.Engine;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.ParserConfig;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.ValueResolver;
import io.quarkus.qute.ValueResolvers;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Protects native blog metadata, stable navigation and the inherited historical article structure. */
@QuarkusTest
class SiteBlogTest {
    private static final RootUrl ROOT = new RootUrl("https://arquillian.org", "/");
    private static final String OLD = "/blog/2017/01/01/core-1/";
    private static final String NEW = "/blog/2018/01/01/core-2/";
    private static final String ARTICLE = "/blog/2017/06/01/article/";

    @Inject
    SiteBlog runtimeBlog;

    /** Verifies the real CDI bean can combine Roq's native site and data without activating staged articles. */
    @Test
    void usesNativeRuntimeBeans() {
        assertEquals(582L, runtimeBlog.posts().stream().filter(post -> post.getBoolean("release")).count());
    }

    /** Authored publication dates override display metadata without duplicating announcements or changing release-detail dates. */
    @Test
    void combinesNativeArticlesWithCatalogReleasesOnce() {
        SiteBlog blog = blog(page(ARTICLE, "2017-06-01", "article", false),
                page(OLD, "2099-01-01", "release", false),
                page("/blog/", "2019-01-01", "index", false),
                page("/blog/2019/01/01/draft/", "2019-01-01", "draft", true));
        blog.catalog.find("releases", OLD).put("date", "2016-12-23").put("published_date", "2017-01-01");
        assertEquals(List.of(OLD, NEW, ARTICLE), links(blog.posts()));
        assertEquals("2099-01-01", blog.post(page(OLD, "2099-01-01", "release", false)).getString("date"));
        assertEquals("2016-12-23", blog.release(OLD).getString("date"));
        assertEquals(List.of(OLD, NEW), links(blog.latest(2)));
        assertThrows(IllegalArgumentException.class, () -> blog.latest(-1));
        assertThrows(UnsupportedOperationException.class, () -> blog.posts().clear());
    }

    /** Captured publication ranks break date ties, new posts use URL order and tag pagination stays bounded. */
    @Test
    void filtersSortsAndPaginatesDeterministically() {
        SiteBlog blog = blog(page(ARTICLE, "2018-01-01", "article", false));
        assertEquals(List.of(ARTICLE, NEW, OLD), links(blog.posts()));
        blog.site.pages().getFirst().data().put("published_order", 1);
        blog.catalog.find("releases", NEW).put("published_order", 0);
        assertEquals(List.of(NEW, ARTICLE, OLD), links(blog.posts()));
        JsonObject first = blog.page("release", 1, 1);
        assertEquals(2, first.getInteger("total"));
        assertEquals(2, first.getInteger("pages"));
        assertEquals(List.of(NEW), links(first.getJsonArray("posts").stream().map(JsonObject.class::cast).toList()));
        assertEquals(0, blog.page("releases", 1, 5).getInteger("total"));
        assertTrue(blog.page("release", Integer.MAX_VALUE, 10).getJsonArray("posts").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> blog.page(null, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> blog.page(null, 1, 0));
        assertEquals(List.of("article", "release"), blog.tags().stream().map(row -> row.getString("title")).toList());
        assertEquals(2, blog.tags().getLast().getInteger("count"));
        assertEquals(0, blog.tags().getFirst().getInteger("weight"));
        assertEquals(6, blog.tags().getLast().getInteger("weight"));
    }

    /** Previous means older and next means newer, with historical chronological post numbers at both edges. */
    @Test
    void preservesChronologicalNavigationAndCommentIdentity() {
        SiteBlog blog = blog(page(ARTICLE, "2017-06-01", "article", false));
        JsonObject navigation = blog.navigation(ARTICLE);
        assertNotNull(navigation.getJsonObject("previous"));
        assertEquals(OLD, navigation.getJsonObject("previous").getString("link"));
        assertEquals(NEW, navigation.getJsonObject("next").getString("link"));
        assertEquals(2, navigation.getInteger("number"));
        assertNull(blog.navigation(OLD).getValue("previous"));
        assertNull(blog.navigation(NEW).getValue("next"));
        JsonObject post = blog.post(page(ARTICLE, "2017-06-01", "article", false));
        assertEquals("original-comment-id", post.getString("comment_id"));
        assertEquals("http://arquillian.org" + ARTICLE, post.getString("disqus_url"));
        assertEquals("Known Author", post.getString("author"));
        assertEquals("/community/known/", post.getString("author_link"));
        assertEquals("https://avatars.example/known?s=44", post.getString("avatar_url"));
        assertEquals("Jun 01, 2017", post.getString("display_date"));
        assertThrows(IllegalArgumentException.class, () -> blog.navigation("/missing/"));
    }

    /** An explicit author is required even when native page title, date and ordinary comment identity have defaults. */
    @Test
    void rejectsMissingRequiredMetadata() {
        for (String field : List.of("author")) {
            NormalPage page = page(ARTICLE, "2017-06-01", "article", false);
            page.data().remove(field);
            assertThrows(IllegalArgumentException.class, () -> blog(page).posts(), field);
        }
        assertThrows(IllegalArgumentException.class, () -> blog().post(page("/missing/", "2017-01-01", "x", false)));
    }

    /** Latest-release warnings consider announcements of the same component rather than unrelated module history. */
    @Test
    void derivesLatestAnnouncementWithoutMutatingCatalog() {
        SiteBlog blog = blog();
        JsonObject release = blog.release(OLD);
        assertEquals(NEW, release.getString("latest_link"));
        assertFalse(release.getBoolean("latest"));
        assertTrue(blog.release(NEW).getBoolean("latest"));
        assertFalse(blog.catalog.find("releases", OLD).containsKey("latest"));
        assertThrows(IllegalArgumentException.class, () -> blog.release(ARTICLE));
    }

    /** The three layouts retain CSS hooks, table order, raw trusted cells and literal examples through real Qute parsing. */
    @Test
    void rendersReleaseAndPostLayoutsWithLegacyIdentity() throws IOException {
        SiteBlog blog = blog(page(ARTICLE, "2017-06-01", "article", false));
        Page page = new BodyPage(OLD, "<h1>Duplicate title</h1><div class=\"paragraph\"><p>Narrative</p></div>"
                + "<pre><code>{=notEvaluated} #{bean.value}</code></pre>");
        Document release = render(blog, page, true);
        assertEquals(1, release.select("#main #content-header h1 a[href='/blog/']").size());
        assertEquals(1, release.select("#content article.post > header.header > h2.title").size());
        assertTrue(release.select(".body h1").isEmpty());
        assertEquals("{=notEvaluated} #{bean.value}", release.selectFirst(".body pre code").text());
        assertEquals(List.of("Component", "Version", "Release date", "Released by"),
                release.select(".body table.table.table-bordered.table-striped th").eachText());
        assertEquals(NEW, release.selectFirst(".body p.info a").attr("href"));
        assertTrue(release.selectFirst(".body").text().contains("What is Core?"));
        assertEquals("/issue/1", release.selectFirst(".body dd a").attr("href"));
        assertEquals("/artifact.jar", release.selectFirst(".body a.artifact").attr("href"));
        assertEquals("http://arquillian.org" + OLD + "#disqus_thread",
                release.selectFirst(".byline .comments a").attr("href"));
        assertEquals("release-id", release.selectFirst(".byline .comments a").attr("data-disqus-identifier"));
        assertTrue(release.html().contains("var disqus_identifier = \"release-id\";"));
        assertTrue(release.html().contains("var disqus_url = \"http://arquillian.org" + OLD + "\";"));
        assertEquals(1, release.select("#comments #disqus_thread").size());
        assertEquals(3, release.select("#sidebar nav.latest li").size());
        Document generated = render(blog, new BodyPage(OLD, "", true), true);
        assertTrue(generated.selectFirst("article > .body").text().contains("Release details"));
        Document article = render(blog, new BodyPage(ARTICLE, "<h1>Duplicate</h1><p>Ordinary article</p>"), false);
        assertEquals("Ordinary article", article.selectFirst("article > .body").text());
        assertTrue(article.select("article > .body table").isEmpty());
    }

    /** Independent examples protect real catalog attribution and field order without relying on ignored migration captures. */
    @Test
    void matchesRepresentativeProductionReleaseStructures() throws IOException {
        SiteBlog blog = blog();
        blog.data = blog.catalog.catalog = new JsonObject(Files.readString(Path.of("data/catalog.json")));
        assertEquals(582, blog.posts().size());
        String[][] examples = {
                {"/blog/2018/06/13/arquillian-cube-1-16-0/", "Arquillian Cube Extension 1.16.0 Released", "Dipak Pawar", "Jun 13, 2018",
                        "https://avatars.githubusercontent.com/u/7941617?s=44", "What is Arquillian Cube Extension?", "5"},
                {"/blog/2014/11/30/arquillian-spacelift-gradle-1-0-0-alpha-4/", "Gradle Arquillian plugin 1.0.0-alpha-4 Released", "Karel Piwko", "Nov 30, 2014",
                        "https://avatars.githubusercontent.com/u/355881?s=44", "What is Arquillian?", "4"},
                {"/blog/2011/02/16/descriptors-0-1-0/", "ShrinkWrap Descriptors 0.1.0 Released", "Andrew Lee Rubinger", "Feb 16, 2011",
                        "https://avatars.githubusercontent.com/u/199891?s=44", "What is ShrinkWrap Descriptors?", "5"}
        };
        for (String[] example : examples) {
            String link = example[0];
            Document actual = render(blog, new BodyPage(link, ""), true);
            assertEquals(example[1], actual.selectFirst("article .header h2.title").text(), link);
            assertEquals("Posted by " + example[2], actual.selectFirst(".byline .author").text(), link);
            assertEquals("on " + example[3], actual.selectFirst(".byline .dateinline").text(), link);
            assertEquals(example[4], actual.selectFirst(".byline img.avatar").attr("src"), link);
            assertEquals(example[5], actual.selectFirst("article > .body > h3").text(), link);
            List<String> labels = new ArrayList<>(List.of("Component", "Version", "Release date", "Released by"));
            if (example[6].equals("5")) labels.add("Compiled against");
            assertEquals(labels, actual.select("article > .body table th").eachText(), link);
            assertEquals("http://arquillian.org" + link + "#disqus_thread", actual.selectFirst(".byline .comments a").attr("href"), link);
        }
    }

    /** Staged and active authored pages must merge into the 606 historical ordinals with stable tag-cloud weights. */
    @Test
    void combinesAllStagedAuthoredMetadataInPublishedOrder() throws IOException {
        List<NormalPage> pages = new ArrayList<>();
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        for (String directory : List.of("content/_migration/blog", "content/blog")) {
            if (!Files.isDirectory(Path.of(directory))) continue;
            try (Stream<Path> paths = Files.list(Path.of(directory))) {
                for (Path path : paths.filter(path -> path.toString().endsWith(".adoc")).sorted().toList()) {
                    String text = Files.readString(path);
                    JsonObject metadata = new JsonObject(yaml.readTree(text.substring(4, text.indexOf("\n---\n", 4))).toString());
                    pages.add(new NormalPage(new RoqUrl(ROOT, metadata.getString("link")), source(path.toString(), false), metadata, null));
                }
            }
        }
        SiteBlog blog = blog(pages.toArray(NormalPage[]::new));
        blog.data = blog.catalog.catalog = new JsonObject(Files.readString(Path.of("data/catalog.json")));
        List<JsonObject> posts = blog.posts();
        assertEquals(606, posts.size());
        for (int index = 0; index < posts.size(); index++) assertEquals(index, posts.get(index).getInteger("published_order"));
        for (JsonObject post : posts) {
            if (!post.getBoolean("release")) continue;
            JsonObject captured = blog.catalog.find("releases", post.getString("link"));
            assertEquals(captured.getString("title"), post.getString("title"), post.getString("link"));
            assertEquals(captured.getString("published_date", captured.getString("date")), post.getString("date"), post.getString("link"));
            assertEquals(captured.getString("author"), post.getString("author"), post.getString("link"));
            assertEquals(captured.getString("author_link"), post.getString("author_link"), post.getString("link"));
            assertEquals(captured.getString("author_avatar_url"), post.getString("avatar_url"), post.getString("link"));
            assertEquals(captured.getString("author_avatar_title", ""), post.getString("avatar_title"), post.getString("link"));
            assertEquals(captured.getJsonArray("tags"), post.getJsonArray("tags"), post.getString("link"));
            assertEquals(captured.getString("comment_id"), post.getString("comment_id"), post.getString("link"));
        }
        assertEquals(115, blog.tags().size());
        assertEquals(452, blog.tags().stream().mapToInt(tag -> blog.page(tag.getString("title"), 1, 5).getInteger("pages")).sum());
        assertEquals(List.of("extension:245:3", "nonrelease:24:1", "release:582:6", "shrinkwrap:137:2"),
                blog.tags().stream().filter(tag -> List.of("extension", "nonrelease", "release", "shrinkwrap").contains(tag.getString("title")))
                        .map(tag -> tag.getString("title") + ":" + tag.getInteger("count") + ":" + tag.getInteger("weight")).toList());
    }

    /** Authored release edits change display metadata while protected release dates and comment threads stay stable. */
    @Test
    void prefersEditableReleaseMetadataWithoutChangingHistoricalIdentity() {
        NormalPage authored = page(OLD, "2020-02-03", "edited", false);
        authored.data().put("title", "Edited release").put("author", "unknown author")
                .put("disqus_identifier", "changed-source-id");
        SiteBlog blog = blog(authored);
        JsonObject post = blog.posts().getFirst();
        assertEquals(OLD, post.getString("link"));
        assertEquals("Edited release", post.getString("title"));
        assertEquals("2020-02-03", post.getString("date"));
        assertEquals("unknown author", post.getString("author"));
        assertEquals("", post.getString("author_link"));
        assertEquals("", post.getString("avatar_url"));
        assertEquals(List.of("edited"), post.getJsonArray("tags").stream()
                .map(JsonObject.class::cast).map(tag -> tag.getString("title")).toList());
        assertEquals("release-id", post.getString("comment_id"));
        assertEquals("http://arquillian.org" + OLD, post.getString("disqus_url"));
        assertEquals("2017-01-01", blog.release(OLD).getString("date"));
        assertEquals("Posted by unknown author on Feb 03, 2020", post.getString("homepage_avatar_title"));
    }

    /** Exact known accounts bind attribution, while a claimed public display name alone cannot borrow a profile. */
    @Test
    void bindsOnlyExactAuthorAccountsOrExplicitPublishedLinks() {
        NormalPage authored = page(ARTICLE, "2017-06-01", "article", false);
        authored.data().put("author", "Known Author");
        SiteBlog blog = blog(authored);
        JsonObject unbound = blog.post(authored);
        assertEquals("Known Author", unbound.getString("author"));
        assertEquals("", unbound.getString("author_link"));
        assertEquals("", unbound.getString("avatar_url"));
        authored.data().put("author", "known");
        assertEquals("/community/known/", blog.post(authored).getString("author_link"));
        assertEquals("/community/known", blog.posts().getFirst().getString("author_link"));
    }

    /** A repeated footer tag repeats that post on tag pages without duplicating global posts or navigation. */
    @Test
    void paginatesEveryTagOccurrenceInPublishedOrder() {
        SiteBlog blog = blog();
        blog.catalog.find("releases", NEW).getJsonArray("tags")
                .add(new JsonObject().put("title", "release").put("href", "/blog/tags/release/"));
        assertEquals(2, blog.posts().size());
        assertEquals(3, blog.page("release", 1, 1).getInteger("total"));
        assertEquals(3, blog.page("release", 1, 1).getInteger("pages"));
        assertEquals(List.of(NEW, NEW, OLD), links(blog.page("release", 1, 10).getJsonArray("posts").stream()
                .map(JsonObject.class::cast).toList()));
        assertEquals(2, blog.navigation(NEW).getInteger("total"));
    }

    /** New ordinary articles use native title/date defaults and a stable public comment identity without hand-generated hashes. */
    @Test
    void defaultsNewArticleCommentIdentityWithoutChangingExplicitIds() {
        NormalPage authored = page(ARTICLE, "2017-06-01", "article", false);
        authored.data().remove("disqus_identifier");
        authored.data().remove("date");
        authored.data().remove("title");
        JsonObject post = blog(authored).post(authored);
        assertEquals("http://arquillian.org" + ARTICLE, post.getString("comment_id"));
        assertEquals("http://arquillian.org" + ARTICLE, post.getString("disqus_url"));
        assertEquals("2017-01-01", post.getString("date"));
        assertEquals("blog/article.adoc", post.getString("title"));
        authored.data().put("disqus_identifier", "explicit-historical-id");
        assertEquals("explicit-historical-id", blog(authored).post(authored).getString("comment_id"));
    }

    /** The shared body fragment retains release structure and literal examples while omitting article chrome and comments. */
    @Test
    void sharesBodyAcrossArticlesArchivesAndFeeds() throws IOException {
        SiteBlog blog = blog();
        Document single = render(blog, new BodyPage(OLD, "", true), true);
        Document body = Jsoup.parseBodyFragment(blog.body(OLD));
        assertEquals(single.selectFirst("article > .body").text(), body.body().text());
        assertTrue(body.select("article,header,.footer,#comments,script").isEmpty());
        String literal = blog.engine.getTemplate("partials/article.html").getFragment("body")
                .data("post", blog.posts().getFirst().copy().put("release", false))
                .data("source", new BodyPage(ARTICLE, "<p>Article body.</p><pre><code>{=literal} #{bean.value}</code></pre>"))
                .data("release", null).render();
        assertEquals("Article body. {=literal} #{bean.value}", Jsoup.parseBodyFragment(literal).text());
        assertNull(blog.source(OLD));
        assertThrows(IllegalArgumentException.class, () -> blog.body("/missing/"));
    }

    /** Legacy summaries stop after two period/exclamation sentences before the first code block, including safe short prose. */
    @Test
    void summarizesActualBodiesWithoutCuttingMultibyteProse() {
        assertEquals("First sentence! Second sentence....", SiteBlog.summarize("<p>First sentence! Second sentence. Third.</p>"));
        assertEquals("Intro without punctuation...", SiteBlog.summarize("<p>Intro without punctuation</p><pre>Code. Code.</pre><p>After.</p>"));
        assertEquals("One....", SiteBlog.summarize("<p>One.</p>"));
        assertEquals("你好🙂...", SiteBlog.summarize("<p>你好🙂</p>"));
        assertEquals("", SiteBlog.summarize("<pre>Code only.</pre>"));
        assertEquals("", SiteBlog.summarize(""));
    }

    /** Creates a genuine Roq site over small immutable page/catalog fixtures without network or filesystem inputs. */
    private static SiteBlog blog(NormalPage... pages) {
        SiteBlog blog = new SiteBlog();
        List<NormalPage> nativePages = new ArrayList<>(List.of(pages));
        nativePages.add(new NormalPage(new RoqUrl(ROOT, "/"), source("index.html", false), new JsonObject(), null));
        blog.site = new Site(new RoqUrl(ROOT, "/"), "images", new JsonObject(), List.copyOf(nativePages), new RoqCollections(Map.of()));
        blog.catalog = new SiteCatalog();
        blog.catalog.catalog = new JsonObject().put("releases", new JsonArray(List.of(release(OLD, "2017-01-01"), release(NEW, "2018-01-01"))))
                .put("identities", new JsonArray().add(new JsonObject().put("id", "known").put("name", "Known Author")
                        .put("github_id", "known").put("link", "/community/known/")
                        .put("avatar_url", "https://avatars.example/known?s=120")));
        blog.data = blog.catalog.catalog;
        return blog;
    }

    /** Supplies the catalog's small structured release fields in their published display order. */
    private static JsonObject release(String link, String date) {
        return new JsonObject().put("id", link).put("link", link).put("date", date).put("title", "Core Released")
                .put("author", "Known Author").put("author_link", "/community/known").put("comment_id", "release-id")
                .put("component", "Core").put("version", "1.0").put("tags", new JsonArray()
                        .add(new JsonObject().put("title", "release").put("href", "/blog/tags/release/")))
                .put("definition_title", "What is Core?").put("definition_html", "<p>Testing core.</p>")
                .put("details", new JsonArray(List.of(new JsonObject().put("label", "Component").put("html", "Core"),
                        new JsonObject().put("label", "Version").put("html", "1.0"),
                        new JsonObject().put("label", "Release date").put("html", date),
                        new JsonObject().put("label", "Released by").put("html", "Known Author"))))
                .put("artifacts_group", "org.core").put("artifacts", new JsonArray().add(new JsonObject().put("html", "core <a class='artifact' href='/artifact.jar'>jar</a>")))
                .put("issues_count", 1).put("issues_comment", "Fixed.")
                .put("resolved_issues", new JsonArray().add(new JsonObject().put("title", "Bug").put("issues_html", new JsonArray().add("<a href='/issue/1'>ONE-1</a> Fixed"))))
                .put("contributors", new JsonArray().add(new JsonObject().put("href", "/community/known").put("title", "Known Author")));
    }

    /** Builds native page metadata while keeping its source and public URL deliberately distinct. */
    private static NormalPage page(String link, String date, String tag, boolean draft) {
        return new NormalPage(new RoqUrl(ROOT, link), source("blog/" + tag + ".adoc", draft), metadata(date, tag), null);
    }

    /** Represents the explicit front matter authored during migration. */
    private static JsonObject metadata(String date, String tag) {
        return new JsonObject().put("title", "Article").put("date", date).put("author", "known")
                .put("tags", new JsonArray().add(tag)).put("disqus_identifier", "original-comment-id");
    }

    /** Constructs source metadata using the pinned Roq 2.2.0 API rather than mock page behavior. */
    private static PageSource source(String path, boolean draft) {
        TemplateSource template = TemplateSource.create(path, "asciidoc", new SourceFile(".", path), path, path,
                false, true, path.equals("index.html"), path.equals("index.html"));
        return new PageSource(template, draft, "2017-01-01T00:00:00Z", PageFiles.empty(), false);
    }

    /** Extracts public links to make ordering assertions independent of presentation metadata. */
    private static List<String> links(List<JsonObject> posts) {
        return posts.stream().map(row -> row.getString("link")).toList();
    }

    /** Parses the real layout bodies with native Qute so nested inserts and trusted rich fields are exercised. */
    private static Document render(SiteBlog blog, Page page, boolean release) throws IOException {
        Engine engine = Engine.builder().configureParser((id, variant) -> new ParserConfig('='))
                .addDefaults().addValueResolvers(new ReflectionValueResolver(), ValueResolvers.rawResolver())
                .addValueResolver(ValueResolver.builder().appliesTo(context -> context.getBase() instanceof JsonObject)
                        .resolveSync(context -> ((JsonObject) context.getBase()).getValue(context.getName())).build())
                .addNamespaceResolver(NamespaceResolver.builder("cdi").resolve(context -> switch (context.getName()) {
                    case "siteBlog" -> blog;
                    case "legacyMarkup" -> new LegacyMarkup();
                    default -> throw new IllegalArgumentException(context.getName());
                }).build()).build();
        for (String name : List.of("partials/article.html", "partials/release-body.html", "partials/tag-cloud.html")) {
            engine.putTemplate(name, engine.parse(Files.readString(Path.of("templates/" + name))));
        }
        blog.engine = engine;
        for (String name : List.of("blog", "post", "release")) {
            String template = Files.readString(Path.of("templates/layouts/" + name + ".html"));
            engine.putTemplate(name, engine.parse(template.substring(template.indexOf("---", 3) + 3)));
        }
        page.data().put("layout", release ? "release" : "post");
        String body = release ? "{#include release /}" : "";
        return Jsoup.parse(engine.parse("{#include blog}{#include post}" + body + "{/include}{/include}").data("page", page).render());
    }

    /** Supplies already-rendered native page content without asking Roq to activate a public blog route. */
    public static class BodyPage extends Page {
        private final String body;

        /** Retains a real page/source model while replacing only the content rendering boundary. */
        BodyPage(String link, String body) {
            this(link, body, false);
        }

        /** Marks generated fixtures so requesting their native layout content fails instead of hiding recursion. */
        BodyPage(String link, String body, boolean generated) {
            super(new RoqUrl(ROOT, link), new PageSource(SiteBlogTest.source("blog/article.adoc", false).template(),
                    false, "2017-01-01T00:00:00Z", PageFiles.empty(), generated), metadata("2017-06-01", "article"));
            this.body = body;
        }

        /** Returns literal renderer output to verify templates never evaluate examples a second time. */
        @Override
        public String content() {
            if (source().generated()) throw new AssertionError("Generated release must not resolve native page content");
            return body;
        }
    }
}
