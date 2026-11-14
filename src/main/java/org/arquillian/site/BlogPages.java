package org.arquillian.site;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Derives disposable native blog routes from canonical release data and editable authored frontmatter. */
public final class BlogPages {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final String POST = "/blog/\\d{4}/\\d{2}/\\d{2}/[A-Za-z0-9_-][A-Za-z0-9._-]*/";

    /** Prevents construction of the build-time entry point. */
    private BlogPages() {
    }

    /** Runs before compilation through Java source-file launch so a fresh checkout needs no compiled classes. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 5) throw new IllegalArgumentException(
                "Usage: BlogPages.java <catalog.json> <content/blog> <generated.json> <blogreleases.json> <blogarchives.json>");
        generate(Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]), Path.of(arguments[3]), Path.of(arguments[4]));
    }

    /** Validates every authority and collision before replacing either generated index; article bodies are never read. */
    static void generate(Path catalog, Path blog, Path nativeIndex, Path releasesOutput, Path archivesOutput) throws IOException {
        List<Path> outputs = List.of(releasesOutput.toAbsolutePath().normalize(), archivesOutput.toAbsolutePath().normalize());
        for (Path output : outputs) {
            if (output.startsWith(blog.toRealPath())) throw new IllegalArgumentException("Generated metadata must not replace blog sources");
            for (Path input : List.of(catalog, nativeIndex)) if (same(output, input)) {
                throw new IllegalArgumentException("Generated metadata must not replace authoritative inputs");
            }
        }
        if (same(outputs.getFirst(), outputs.getLast())) throw new IllegalArgumentException("Generated indexes must have distinct paths");
        JsonNode source = JSON.readTree(catalog.toFile());
        if (source == null || !source.isObject() || !source.path("releases").isArray()) {
            throw new IllegalArgumentException("Catalog releases must be an array");
        }
        JsonNode nativePages = JSON.readTree(nativeIndex.toFile());
        if (nativePages == null || !nativePages.isArray()) throw new IllegalArgumentException("Native route index must be an array");
        Set<String> identifiers = new HashSet<>();
        Set<String> references = new HashSet<>();
        Set<String> routes = new HashSet<>();
        for (JsonNode row : nativePages) {
            unique(identifiers, required(row, "id", "native page"), "native id");
            unique(references, required(row, "ref", "native page"), "native reference");
            unique(routes, required(row, "link", "native page"), "native route");
        }
        Map<String, ObjectNode> posts = new LinkedHashMap<>();
        Map<String, ObjectNode> releases = new LinkedHashMap<>();
        for (JsonNode row : source.path("releases")) {
            String reference = required(row, "id", "release");
            if (!reference.matches("[A-Za-z0-9][A-Za-z0-9:._/-]*")) throw new IllegalArgumentException("Malformed release id: " + reference);
            String link = postLink(row, reference);
            unique(references, reference, "catalog reference");
            unique(routes, link, "canonical route");
            ObjectNode page = JSON.createObjectNode().put("id", identifier(reference)).put("link", link)
                    .put("title", required(row, "title", reference)).put("date", date(row, "date", reference)).put("ref", reference)
                    .put("layout", "release");
            unique(identifiers, page.path("id").asText(), "page id");
            releases.put(link, page);
            ObjectNode post = page.deepCopy().put("date", row.has("published_date") ? date(row, "published_date", reference) : page.path("date").asText());
            post.set("tags", tags(row, reference));
            posts.put(link, post);
        }
        Set<String> authored = new HashSet<>();
        try (java.util.stream.Stream<Path> files = Files.walk(blog)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                if (!file.getFileName().toString().matches(".*\\.(adoc|asciidoc|md|markdown|html)$")) continue;
                JsonNode row = frontmatter(file);
                if (row.has("draft") && !row.path("draft").isBoolean()) throw new IllegalArgumentException("Invalid draft flag: " + file);
                if (row.path("draft").asBoolean()) continue;
                String link = required(row, "link", file.toString());
                unique(authored, link, "authored route");
                if (link.equals("/blog/tags/")) {
                    unique(routes, link, "canonical route");
                    continue;
                }
                postLink(row, file.toString());
                ObjectNode existing = posts.get(link);
                if (existing == null) unique(routes, link, "canonical route");
                if (row.has("id")) unique(identifiers, required(row, "id", file.toString()), "authored id");
                ObjectNode post = existing == null ? JSON.createObjectNode().put("link", link) : existing.deepCopy();
                if (row.has("title")) post.put("title", required(row, "title", file.toString()));
                if (row.has("date")) post.put("date", date(row, "date", file.toString()));
                if (row.has("tags")) post.set("tags", tags(row, file.toString()));
                else if (existing == null) post.set("tags", JSON.createArrayNode());
                if (row.has("author") || existing == null) required(row, "author", file.toString());
                posts.put(link, post);
            }
        }
        // The authored tag-cloud fragment owns this public path even when absent from a controlled fixture.
        if (!authored.contains("/blog/tags/")) unique(routes, "/blog/tags/", "canonical route");
        List<ObjectNode> releasePages = releases.entrySet().stream().filter(entry -> !authored.contains(entry.getKey()))
                .map(Map.Entry::getValue).sorted(Comparator.comparing(row -> row.path("link").asText())).toList();
        Map<String, Integer> tagCounts = new TreeMap<>();
        for (ObjectNode post : posts.values()) for (JsonNode tag : post.path("tags")) {
            tagCounts.merge(tag.asText(), 1, Integer::sum);
        }
        List<ObjectNode> archives = new ArrayList<>();
        archive(archives, "", "/blog/", posts.size(), routes, identifiers);
        for (Map.Entry<String, Integer> tag : tagCounts.entrySet()) {
            archive(archives, tag.getKey(), tagLink(tag.getKey()), tag.getValue(), routes, identifiers);
        }
        archives.sort(Comparator.comparing(row -> row.path("link").asText()));
        byte[] releaseBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(releasePages);
        byte[] archiveBytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(archives);
        write(outputs.getFirst(), releaseBytes);
        write(outputs.getLast(), archiveBytes);
    }

    /** Reads precisely the delimited YAML header so literal examples and template syntax remain opaque. */
    private static JsonNode frontmatter(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            if (!"---".equals(reader.readLine())) throw new IllegalArgumentException("Missing blog frontmatter: " + file);
            StringBuilder yaml = new StringBuilder();
            for (String line; (line = reader.readLine()) != null;) {
                if (line.equals("---")) {
                    JsonNode data = YAML.readTree(yaml.toString());
                    if (data == null || !data.isObject()) throw new IllegalArgumentException("Blog frontmatter must be an object: " + file);
                    return data;
                }
                yaml.append(line).append('\n');
            }
            throw new IllegalArgumentException("Unclosed blog frontmatter: " + file);
        }
    }

    /** Derives the minimal archive metadata while rejecting routes or IDs owned by another source. */
    private static void archive(List<ObjectNode> pages, String tag, String base, int count, Set<String> routes, Set<String> identifiers) {
        int total = Math.max(1, (count + 4) / 5);
        for (int number = 1; number <= total; number++) {
            String link = number == 1 ? base : base + "page/" + number + "/";
            String id = identifier("blog-archive:" + link);
            unique(routes, link, "archive route");
            unique(identifiers, id, "archive id");
            pages.add(JSON.createObjectNode().put("id", id).put("link", link).put("tag", tag).put("number", number)
                    .put("base_path", base).put("title", "Arquillian Blog").put("body_class", "blog index").put("site_title", "Arquillian"));
        }
    }

    /** Accepts canonical blog paths only, preserving the exact date and slug rather than slugifying source filenames. */
    private static String postLink(JsonNode row, String context) {
        String link = required(row, "link", context);
        if (!link.matches(POST)) throw new IllegalArgumentException("Unsafe blog route in " + context + ": " + link);
        LocalDate.parse(link.substring(6, 16).replace('/', '-'));
        return link;
    }

    /** Normalizes only tag representation, preserving repeated labels because published pagination counts occurrences. */
    private static JsonNode tags(JsonNode row, String context) {
        JsonNode input = row.path("tags");
        if (!input.isArray()) throw new IllegalArgumentException("Tags must be an array in " + context);
        com.fasterxml.jackson.databind.node.ArrayNode tags = JSON.createArrayNode();
        for (JsonNode entry : input) {
            String title = entry.isObject() ? required(entry, "title", context) : entry.isTextual() ? entry.asText() : "";
            if (title.isBlank() || title.equals(".") || title.equals("..")) throw new IllegalArgumentException("Invalid tag in " + context);
            String link = tagLink(title);
            if (entry.isObject() && !link.equals(required(entry, "href", context))) throw new IllegalArgumentException("Noncanonical tag link in " + context);
            tags.add(title);
        }
        return tags;
    }

    /** Encodes exactly one tag path segment with the same standard-library encoding as the runtime blog API. */
    private static String tagLink(String tag) {
        return "/blog/tags/" + URLEncoder.encode(tag, StandardCharsets.UTF_8).replace("+", "%20") + "/";
    }

    /** Validates dates before native Roq pages can fail later during augmentation. */
    private static String date(JsonNode row, String field, String context) {
        String date = required(row, field, context);
        try { return LocalDate.parse(date).toString(); }
        catch (java.time.DateTimeException failure) { throw new IllegalArgumentException("Invalid " + field + " in " + context + ": " + date, failure); }
    }

    /** Rejects missing, mistyped and blank values with the source context needed for repair. */
    private static String required(JsonNode row, String field, String context) {
        JsonNode value = row.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Missing or invalid " + field + " in " + context);
        return value.asText();
    }

    /** Rejects each duplicate at its boundary before any generated file is written. */
    private static void unique(Set<String> values, String value, String kind) {
        if (!values.add(value)) throw new IllegalArgumentException("Duplicate " + kind + ": " + value);
    }

    /** Produces stable native collection identifiers without storing another source-controlled identity map. */
    private static String identifier(String reference) {
        return UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Recognizes lexical, symlink and hard-link aliases before opening any generated output. */
    private static boolean same(Path first, Path second) throws IOException {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }

    /** Atomically replaces one disposable index only after the complete generation has passed validation. */
    private static void write(Path output, byte[] content) throws IOException {
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), ".blog-generated-", ".json");
        try {
            Files.write(temporary, content);
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
