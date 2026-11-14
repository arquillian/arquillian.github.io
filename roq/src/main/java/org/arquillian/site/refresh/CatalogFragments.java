package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;

/** Builds presentation fragments from structured values without trusting upstream HTML or changing unrelated snapshots. */
final class CatalogFragments {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Prevents construction of the stateless fragment helper. */
    private CatalogFragments() {}

    /** Escapes plain text using the same HTML parser used by the site adapters. */
    static String text(String value) { return new Element("span").text(value).html(); }

    /** Allows canonical site routes and HTTP links, rejecting executable or credential-bearing URLs. */
    static String safeLink(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (value.startsWith("/") && !value.startsWith("//") && !value.contains("\\")
                    && !value.contains("..") && uri.getRawAuthority() == null) return value;
            if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getUserInfo() == null) return value;
        } catch (IllegalArgumentException ignored) { }
        throw new IOException("Unsafe presentation URL");
    }

    /** Builds a safe anchor, escaping both its URL attribute and visible title. */
    static String anchor(String href, String title) throws IOException {
        return new Element("a").attr("href", safeLink(href)).text(title).outerHtml();
    }

    /** Sanitizes fetched rich notes while retaining simple formatting and safe external links. */
    static String clean(String html) {
        return Jsoup.clean(html, "", Safelist.basic().removeProtocols("a", "href", "ftp", "mailto")
                .addProtocols("a", "href", "http", "https"), new Document.OutputSettings().prettyPrint(false));
    }

    /** Updates a labeled fragment only when its semantic HTML differs, retaining unchanged historical whitespace. */
    static void detail(ObjectNode row, String field, String label, String html) throws IOException {
        ArrayNode details = array(row, field);
        for (JsonNode item : details) {
            if (item.path("label").asText().equals(label)) {
                if (!normalized(item.path("html").asText()).equals(normalized(html))) ((ObjectNode) item).put("html", html);
                return;
            }
        }
        details.addObject().put("label", label).put("html", html);
    }

    /** Compares parsed markup independently of source indentation without discarding links or attributes. */
    private static String normalized(String value) {
        return Jsoup.parseBodyFragment(value).body().html().replaceAll(">\\s+<", "><").strip();
    }

    /** Refreshes repository facts and activity in every module view that consumes them. */
    static void repository(ObjectNode module, JsonNode repository) throws IOException {
        String web = repository.path("html_url").asText();
        if (!web.isBlank()) {
            for (String field : List.of("facts", "repository_facts")) {
                detail(module, field, "Web URL", anchor(web, repository.path("path").asText()));
                detail(module, field, "Organization", anchor("https://github.com/" + repository.path("owner").asText(), repository.path("owner").asText()));
                if (repository.hasNonNull("clone_url")) detail(module, field, "Clone URL", anchor(repository.path("clone_url").asText(), repository.path("path").asText() + ".git"));
            }
        }
        if (repository.has("last_commit")) {
            JsonNode commit = repository.path("last_commit");
            ObjectNode activity = module.has("activity") ? (ObjectNode) module.path("activity") : module.putObject("activity");
            activity.put("unreleased_commits", repository.path("unreleased_commits").asInt())
                    .put("link", safeLink(web + "/commit/" + commit.path("commit").asText()))
                    .put("message", commit.path("message").asText()).put("author", commit.path("author").asText()).put("date", commit.path("date").asText());
        }
    }

    /** Updates the consumed hero version/date only when changed, retaining unchanged captured bytes and unrelated license markup. */
    static void metadata(ObjectNode module, String version, String date) throws IOException {
        Document document = Jsoup.parseBodyFragment(module.path("metadata_html").asText());
        document.outputSettings().prettyPrint(false);
        boolean changed = false;
        for (String label : List.of("Latest version:", "Release date:")) {
            String value = label.equals("Latest version:") ? version : date;
            Element item = null;
            for (Element candidate : document.select("li")) if (candidate.ownText().equals(label)) {
                if (item != null) throw new IOException("Ambiguous module hero metadata: " + label);
                item = candidate;
            }
            if (item == null) {
                item = document.body().appendElement("li");
                item.appendElement("i").addClass(label.equals("Latest version:") ? "icon-tag" : "icon-calendar");
                item.appendText(" " + label + " ");
                Element strong = item.appendElement("strong");
                if (label.equals("Latest version:")) strong.addClass("label").attr("style", "position: relative; top: -1px;");
                item.appendText(" ").appendElement("span").addClass("divider").text("|");
            }
            Element strong = item.selectFirst("strong");
            if (strong == null) throw new IOException("Module hero metadata needs a value: " + label);
            if (!strong.text().equals(value)) { strong.text(value); changed = true; }
        }
        if (changed) module.put("metadata_html", document.body().html());
    }

    /** Builds Maven download rows from resolved coordinates, never inventing unresolved external versions. */
    static ObjectNode artifact(JsonNode model) throws IOException {
        String group = CatalogAssembler.required(model, "groupId");
        String artifact = CatalogAssembler.required(model, "artifactId");
        String version = CatalogAssembler.required(model, "version");
        String packaging = model.path("packaging").asText("jar");
        for (String value : List.of(group, artifact, version, packaging)) {
            if (!value.matches("[A-Za-z0-9_.+-]+")) throw new IOException("Unresolved or unsafe artifact coordinate; configure publication explicitly");
        }
        String base = "https://repo1.maven.org/maven2/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version;
        ObjectNode row = JSON.objectNode().put("coordinates", group + ":" + artifact + ":" + packaging + ":" + version);
        ArrayNode links = row.putArray("links");
        StringBuilder html = new StringBuilder(text(group + " » " + artifact));
        for (String extension : packaging.equals("pom") ? List.of("pom") : List.of(packaging, "pom")) {
            String href = base + "." + extension;
            links.addObject().put("href", href).put("title", extension);
            html.append(' ').append(anchor(href, extension));
        }
        row.put("html", html.toString());
        return row;
    }

    /** Updates selected dependency versions while retaining unrelated headings, warnings and published coordinates. */
    static void artifacts(ObjectNode module, List<JsonNode> models) throws IOException {
        ArrayNode blocks = array(module, "artifact_blocks");
        if (blocks.valueStream().noneMatch(block -> block.path("kind").asText().equals("coordinates"))) {
            for (JsonNode model : models) {
                artifact(model);
                blocks.addObject().put("kind", "coordinates").put("text", "<dependency>\n  <groupId>" + model.path("groupId").asText() + "</groupId>\n  <artifactId>" + model.path("artifactId").asText() + "</artifactId>\n  <version>" + model.path("version").asText() + "</version>\n  <scope>test</scope>\n</dependency>");
            }
        }
        for (JsonNode item : blocks) {
            if (!item.path("kind").asText().equals("coordinates")) continue;
            String xml = item.path("text").asText();
            for (JsonNode model : models) {
                Element dependency = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser()).selectFirst("dependency");
                if (dependency == null || !dependency.select("groupId").text().equals(model.path("groupId").asText())
                        || !dependency.select("artifactId").text().equals(model.path("artifactId").asText())) continue;
                String version = CatalogAssembler.required(model, "version");
                artifact(model);
                if (!dependency.select("version").text().equals(version)) ((ObjectNode) item).put("text", xml.replaceFirst("(?s)<version>.*?</version>", "<version>" + version + "</version>"));
            }
        }
        ObjectNode published = null;
        for (JsonNode item : blocks) if (item.path("kind").asText().equals("published")) published = (ObjectNode) item;
        if (published == null) published = blocks.addObject().put("kind", "published");
        ArrayNode items = array(published, "items");
        ArrayNode selected = JSON.arrayNode();
        for (JsonNode model : models) {
            ObjectNode fresh = artifact(model);
            boolean replaced = false;
            for (int index = 0; index < items.size(); index++) {
                JsonNode previous = items.get(index);
                String[] coordinates = previous.path("coordinates").asText().split(":");
                if (coordinates.length >= 4 && coordinates[0].equals(model.path("groupId").asText())
                        && coordinates[1].equals(model.path("artifactId").asText())) {
                    if (!previous.path("coordinates").equals(fresh.path("coordinates"))) items.set(index, fresh.deepCopy());
                    replaced = true;
                }
            }
            if (!replaced) items.add(fresh.deepCopy());
            selected.add(fresh);
        }
        module.set("published_artifacts", selected);
        ArrayNode links = array(module, "artifact_links");
        for (JsonNode block : blocks) for (JsonNode item : block.path("items")) for (JsonNode link : item.path("links")) {
            if (links.valueStream().noneMatch(previous -> previous.path("href").equals(link.path("href")))) links.add(link.deepCopy());
        }
    }

    /** Preserves unchanged artifact HTML and unrelated historical rows while replacing changed exact Maven artifacts. */
    static void releaseArtifacts(ObjectNode release, List<JsonNode> models) throws IOException {
        ArrayNode artifacts = array(release, "artifacts");
        for (JsonNode model : models) {
            ObjectNode fresh = artifact(model);
            String path = "/" + model.path("groupId").asText().replace('.', '/') + "/" + model.path("artifactId").asText() + "/";
            int found = -1;
            for (int index = 0; index < artifacts.size(); index++) {
                for (JsonNode link : artifacts.get(index).path("links")) if (URI.create(safeLink(link.path("href").asText())).getPath().contains(path)) { found = index; break; }
                if (found >= 0) break;
            }
            if (found < 0) { artifacts.add(fresh); continue; }
            JsonNode previous = artifacts.get(found);
            boolean unchanged = true;
            for (JsonNode expected : fresh.path("links")) {
                String expectedPath = URI.create(expected.path("href").asText()).getPath();
                boolean present = false;
                for (JsonNode link : previous.path("links")) if (URI.create(safeLink(link.path("href").asText())).getPath().equals(expectedPath)) present = true;
                if (!present) unchanged = false;
            }
            if (!unchanged) artifacts.set(found, fresh);
        }
    }

    /** Rebuilds an announcement's consumed issue groups, including the count and escaped commentary. */
    static void notes(ObjectNode announcement, JsonNode observation) throws IOException {
        JsonNode groups = observation.path("resolved_issues");
        if (!groups.isObject()) throw new IOException("Release-note groups must be an object");
        ArrayNode rendered = JSON.arrayNode();
        int count = 0;
        for (java.util.Map.Entry<String, JsonNode> entry : groups.properties()) {
            if (!entry.getValue().isArray()) throw new IOException("Release-note group must be an array");
            ArrayNode issues = rendered.addObject().put("title", entry.getKey()).putArray("issues_html");
            for (JsonNode issue : entry.getValue()) {
                if (!issue.isTextual()) throw new IOException("Release-note issue must be HTML text");
                issues.add(clean(issue.asText()));
                count++;
            }
        }
        if (!announcement.path("resolved_issues").equals(rendered)) announcement.set("resolved_issues", rendered);
        announcement.put("issues_count", count);
        if (observation.has("comment")) announcement.put("issues_comment", text(observation.path("comment").asText()));
    }

    /** Renders a release-history row from its structured values without raw upstream HTML. */
    static String history(JsonNode row) throws IOException {
        String version = text(row.path("version").asText());
        if (!row.path("link").asText().isBlank()) version += " " + anchor(row.path("link").asText(), "view blog");
        if (!row.path("tag_url").asText().isBlank()) version += " " + anchor(row.path("tag_url").asText(), "view tag");
        return "<td>" + text(row.path("legacy_id").asText()) + "</td><td>" + version + "</td><td>"
                + text(row.path("date").asText()) + "</td><td>" + text(row.path("released_by").asText())
                + "</td><td>" + row.path("compiled_against_html").asText() + "</td>";
    }

    /** Updates plain profile biography and safe profile links, preserving reviewed avatar selection. */
    static void profile(ObjectNode person, JsonNode observation) throws IOException {
        if (observation.has("bio") && !Jsoup.parseBodyFragment(person.path("bio_html").asText()).text().equals(observation.path("bio").asText(""))) {
            person.set("bio", observation.path("bio").deepCopy());
            person.put("bio_html", "<p>" + text(observation.path("bio").asText("")) + "</p>");
        }
        for (String field : List.of("company", "location")) if (observation.has(field)) person.set(field, observation.get(field).deepCopy());
        if (observation.hasNonNull("blog") && !observation.path("blog").asText().isBlank()) {
            String href = safeLink(observation.path("blog").asText());
            ArrayNode profiles = array(person, "profiles");
            ObjectNode blog = null;
            for (JsonNode profile : profiles) if (profile.path("label").asText().equals("Blog")) blog = (ObjectNode) profile;
            if (blog == null) blog = profiles.addObject().put("label", "Blog");
            String html = anchor(href, "Blog");
            if (!normalized(blog.path("html").asText()).equals(normalized(html))) {
                blog.put("html", html).putArray("links").addObject().put("href", href).put("title", "Blog");
            }
        }
    }

    /** Updates exact author credits, last-update date and changelog while retaining share, feedback and anchor markup. */
    static void guide(ObjectNode guide, JsonNode observation, List<ObjectNode> authors) throws IOException {
        if (!authors.isEmpty()) {
            Element root = Jsoup.parseBodyFragment(guide.path("authors_html").asText()).body();
            List<Element> links = new ArrayList<>(root.select("a[href^=/community/]"));
            for (ObjectNode person : authors) {
                String href = person.path("link").asText();
                Element existing = links.stream().filter(link -> link.attr("href").replaceAll("/+$", "").equals(href.replaceAll("/+$", ""))).findFirst().orElse(null);
                if (existing != null) {
                    if (!existing.text().equals(person.path("name").asText())) existing.text(person.path("name").asText());
                } else {
                    String html = anchor(href, person.path("name").asText());
                    if (links.isEmpty()) { root.append(html); links.add(root.select("a").last()); }
                    else { Element previous = links.getLast(); previous.after(", " + html); links.add(previous.nextElementSibling()); }
                }
            }
            guide.put("authors_html", root.html());
        }
        if (!observation.path("changes").equals(guide.path("changes"))) {
            guide.set("changes", observation.path("changes").deepCopy());
            if (!observation.path("changes").isEmpty()) {
                LocalDate latest;
                try { latest = LocalDate.parse(observation.path("changes").get(0).path("date").asText().substring(0, 10)); }
                catch (RuntimeException failure) { throw new IOException("Guide history needs an ISO commit date"); }
                guide.put("last_update", latest.toString());
                Document header = Jsoup.parseBodyFragment(guide.path("authors_html").asText());
                header.outputSettings().prettyPrint(false);
                Element label = header.select("strong").stream().filter(element -> element.text().equals("Last Update:")).findFirst().orElse(null);
                if (label != null) {
                    String displayed = latest.format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH));
                    if (label.nextSibling() instanceof TextNode value) value.text(displayed);
                    else label.after(new TextNode(displayed));
                    guide.put("authors_html", header.body().html());
                }
            }
            Document footer = Jsoup.parseBodyFragment(guide.path("footer_html").asText());
            footer.outputSettings().prettyPrint(false);
            Element heading = footer.select("h4").stream().filter(element -> element.text().equals("Recent Changelog")).findFirst().orElse(null);
            if (heading == null) heading = footer.body().appendElement("h4").text("Recent Changelog");
            Element list = heading.nextElementSibling();
            if (list == null || !list.tagName().equals("ul")) { heading.after("<ul></ul>"); list = heading.nextElementSibling(); }
            list.empty();
            for (JsonNode change : observation.path("changes")) list.appendElement("li").text(change.path("date").asText() + ": " + change.path("message").asText().strip() + " by " + change.path("author").asText());
            guide.put("footer_html", footer.body().html());
        }
    }

    /** Requires arrays before mutation so malformed historical fragments fail without data loss. */
    static ArrayNode array(ObjectNode row, String field) throws IOException {
        if (!row.has(field)) return row.putArray(field);
        if (row.get(field) instanceof ArrayNode array) return array;
        throw new IOException("Expected array: " + field);
    }
}
