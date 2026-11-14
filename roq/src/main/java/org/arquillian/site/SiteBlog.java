package org.arquillian.site;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.Engine;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;

/** Combines native authored metadata with catalog release announcements for the historical blog. */
@Named("siteBlog")
@ApplicationScoped
public class SiteBlog {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd, uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final Comparator<JsonObject> ORDER = Comparator
            .comparing((JsonObject post) -> post.getString("date")).reversed()
            .thenComparingInt(post -> post.getInteger("published_order", Integer.MAX_VALUE))
            .thenComparing(post -> post.getString("link"));
    @Inject
    Site site;
    @Inject
    SiteCatalog catalog;
    @Inject
    @Named("catalog")
    JsonObject data;

    /** Renders the same trusted body fragment used by single pages, archives and Atom. */
    @Inject
    Engine engine;

    /** Returns newest-first metadata, applying authored display fields without duplicating release announcements. */
    public List<JsonObject> posts() {
        Map<String, JsonObject> posts = new LinkedHashMap<>();
        for (JsonObject release : catalog.latestReleases(Integer.MAX_VALUE)) {
            posts.put(release.getString("link"), release.copy().put("release", true));
        }
        for (Page page : site.allPages()) {
            String link = page.url().path();
            if (!authoredPage(page)
                    || !link.matches("/blog/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9._-]+/")) continue;
            JsonObject release = posts.get(link);
            posts.put(link, release == null ? page.data().copy().put("link", link).put("release", false)
                    .put("title", page.title()).put("date", page.data().containsKey("date") ? page.data().getString("date") : page.date().toLocalDate().toString())
                    : authored(release, page.data()));
        }
        return posts.values().stream().map(row -> metadata(row, row.getBoolean("release"))).sorted(ORDER).toList();
    }

    /** Returns a bounded newest-first selection for the shared sidebar. */
    public List<JsonObject> latest(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Post limit must not be negative: " + limit);
        return posts().stream().limit(limit).toList();
    }

    /** Returns exact-tag filtered pagination with stable ordering. */
    public JsonObject page(String tag, int number, int size) {
        if (number < 1 || size < 1) throw new IllegalArgumentException("Blog page number and size must be positive");
        List<JsonObject> matching = posts().stream().flatMap(post -> tag == null || tag.isEmpty() ? Stream.of(post)
                : post.getJsonArray("tags").stream().map(JsonObject.class::cast)
                        .filter(value -> tag.equals(value.getString("title"))).map(_ -> post)).toList();
        int pages = (int) ((matching.size() + size - 1L) / size);
        return new JsonObject().put("posts", new JsonArray(matching.stream().skip((number - 1L) * size).limit(size).toList()))
                .put("total", matching.size()).put("pages", pages).put("number", number)
                .put("previous", number > 1 && number <= pages ? number - 1 : null)
                .put("next", number < pages ? number + 1 : null);
    }

    /** Returns sorted tag counts for data-driven navigation. */
    public List<JsonObject> tags() {
        Map<String, Integer> counts = new TreeMap<>();
        for (JsonObject post : posts()) {
            for (Object value : post.getJsonArray("tags")) {
                counts.merge(((JsonObject) value).getString("title"), 1, Integer::sum);
            }
        }
        int minimum = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        // The published Awestruct tag cloud uses six frequency groups above its minimum.
        return counts.entrySet().stream().map(entry -> new JsonObject().put("title", entry.getKey())
                .put("href", "http://arquillian.org" + tagLink(entry.getKey())).put("count", entry.getValue())
                .put("weight", maximum == minimum ? 0 : (int) Math.ceil(6.0 * (entry.getValue() - minimum) / (maximum - minimum))))
                .toList();
    }

    /** Resolves public article metadata from the native page rather than its source path. */
    public JsonObject post(Page page) {
        String link = page.url().path();
        return posts().stream().filter(post -> link.equals(post.getString("link"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing blog post: " + link));
    }

    /** Resolves older/newer neighbors and chronological post numbering. */
    public JsonObject navigation(String link) {
        List<JsonObject> posts = posts();
        for (int index = 0; index < posts.size(); index++) {
            if (!link.equals(posts.get(index).getString("link"))) continue;
            return new JsonObject().put("previous", index + 1 < posts.size() ? posts.get(index + 1) : null)
                    .put("next", index > 0 ? posts.get(index - 1) : null)
                    .put("number", posts.size() - index).put("total", posts.size());
        }
        throw new IllegalArgumentException("Missing blog post: " + link);
    }

    /** Resolves only exact authored public URLs; generated page content must never recurse into its own layout. */
    public Page source(String link) {
        return site.allPages().stream().filter(page -> authoredPage(page) && link.equals(page.url().path()))
                .findFirst().orElse(null);
    }

    /** Excludes Roq's from-data release documents, whose source flag does not identify them as generated. */
    private static boolean authoredPage(Page page) {
        return !page.draft() && !page.source().generated()
                && !(page instanceof DocumentPage document && "blogreleases".equals(document.collectionId()));
    }

    /** Renders only the shared body with explicit metadata and source, without article chrome or comments. */
    public String body(String link) {
        JsonObject post = posts().stream().filter(row -> link.equals(row.getString("link"))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing blog post: " + link));
        return engine.getTemplate("partials/article.html").getFragment("body").data("post", post)
                .data("source", source(link)).data("release", post.getBoolean("release") ? release(link) : null).render();
    }

    /** Computes the legacy two-sentence homepage summary from the actual shared body before its first code block. */
    public String excerpt(String link) {
        return summarize(body(link));
    }

    /** Preserves legacy sentence boundaries while making empty, short and multibyte authored prose safe. */
    static String summarize(String html) {
        Matcher pre = Pattern.compile("<pre\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        String text = Jsoup.parseBodyFragment(pre.find() ? html.substring(0, pre.start()) : html).text();
        if (text.isEmpty()) return "";
        Matcher sentences = Pattern.compile("[.!]( |$)").matcher(text);
        if (!sentences.find()) return text + "...";
        int start = Math.min(sentences.start() + 1, text.length() - 1);
        int end = sentences.find(start) ? sentences.start() : text.indexOf(' ', start);
        return text.substring(0, end < 0 ? text.length() : end + 1) + "...";
    }

    /** Adds the latest-announcement link to a copy of the structured release metadata. */
    public JsonObject release(String link) {
        JsonObject release = catalog.find("releases", link).copy();
        String latest = release.getString("latest_link");
        if (!release.containsKey("latest_link")) {
            latest = posts().stream().filter(post -> release.getString("component").equals(post.getString("component")))
                    .map(post -> post.getString("link")).findFirst().orElse(link);
        }
        return release.put("latest_link", latest).put("latest", latest == null || latest.equals(link));
    }

    /** Applies the four legacy authored display overrides while retaining release details, ranks and historical thread IDs. */
    private JsonObject authored(JsonObject release, JsonObject source) {
        JsonObject row = release.copy();
        for (String field : List.of("title", "date", "tags")) {
            if (source.containsKey(field)) row.put(field, source.getValue(field));
        }
        if (source.containsKey("date")) row.remove("published_date");
        if (source.containsKey("author")) {
            String author = required(source, "author");
            JsonObject previous = identity(release.getString("author"), release.getString("author_link", ""));
            JsonObject current = identity(author, "");
            if (current.isEmpty() || previous.isEmpty() || !current.equals(previous)) {
                for (String field : List.of("author_link", "author_avatar_url", "author_avatar_title")) row.remove(field);
            }
            row.put("author", author);
        }
        return row;
    }

    /** Derives display fields without overwriting the catalog's release date or homepage avatar metadata. */
    private JsonObject metadata(JsonObject row, boolean release) {
        String link = required(row, "link");
        String author = required(row, "author");
        JsonObject identity = identity(author, row.getString("author_link", ""));
        LocalDate date = LocalDate.parse(row.getString("published_date", required(row, "date")));
        String identifier = release ? required(row, "comment_id")
                : row.getString("disqus_identifier", "http://arquillian.org" + link);
        String disqus = row.getString("disqus_url", "http://arquillian.org" + link);
        String avatar = row.getString("author_avatar_url", identity.getString("avatar_url", ""))
                .replaceAll("([?&])s=\\d+", "$1s=44");
        JsonArray tags = new JsonArray();
        for (Object value : row.getJsonArray("tags", new JsonArray())) {
            if (value instanceof JsonObject tag) tags.add(tag.copy());
            else if (value instanceof String tag) tags.add(new JsonObject().put("title", tag).put("href", tagLink(tag)));
            else throw new IllegalArgumentException("Invalid blog tag in " + link);
        }
        return new JsonObject().put("link", link).put("title", required(row, "title"))
                .put("date", date.toString()).put("display_date", DATE.format(date)).put("month", MONTH.format(date))
                .put("day", "%02d".formatted(date.getDayOfMonth()))
                .put("author", identity.getString("name", author))
                .put("author_link", row.getString("author_link", identity.getString("link", "")))
                .put("avatar_url", avatar).put("avatar_title", row.getString("author_avatar_title", ""))
                .put("homepage_avatar_title", "Posted by " + identity.getString("name", author) + " on " + DATE.format(date))
                .put("comment_id", identifier).put("disqus_url", disqus)
                .put("comment_url", row.getString("comment_url", disqus + "#disqus_thread"))
                .put("comment_id_js", javascript(identifier)).put("disqus_url_js", javascript(disqus))
                .put("tags", tags).put("release", release).put("component", row.getString("component", ""))
                .put("published_order", row.getInteger("published_order", Integer.MAX_VALUE));
    }

    /** Resolves optional author attribution from the existing native identity rows, retaining unknown authors as text. */
    private JsonObject identity(String author, String link) {
        for (Object value : data.getJsonArray("identities", new JsonArray())) {
            JsonObject identity = (JsonObject) value;
            if (author.equals(identity.getString("github_id"))
                    || author.equals(identity.getString("id")) || (!link.isEmpty()
                    && link.replaceFirst("/+$", "").equals(identity.getString("link", "").replaceFirst("/+$", "")))) return identity;
        }
        return new JsonObject();
    }

    /** Encodes one tag as a path segment so native source tags retain canonical public links. */
    private static String tagLink(String tag) {
        return "/blog/tags/" + URLEncoder.encode(tag, StandardCharsets.UTF_8).replace("+", "%20") + "/";
    }

    /** Keeps exact comment identity while preventing a JavaScript string from terminating its enclosing script. */
    private static String javascript(String value) {
        return Json.encode(value).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    /** Rejects incomplete migration metadata before it can produce broken attribution or comment threads. */
    private static String required(JsonObject row, String field) {
        String value = row.getString(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing blog " + field + ": " + row.getString("link"));
        return value;
    }
}
