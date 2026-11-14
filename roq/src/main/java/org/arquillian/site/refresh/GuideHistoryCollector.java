package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Collects original guide front matter and path-filtered history from the current repository at a selected revision. */
public final class GuideHistoryCollector {
    private static final Pattern TRANSLATION = Pattern.compile("^(.*?)_([a-z]{2}(?:_[a-z]{2})?)(\\..*)$");
    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    /** Creates an explicit collector without reading Git objects or source files. */
    public GuideHistoryCollector() {}

    /** Returns original guide rows, language relations and recent changes without checkout or source writes. */
    public ArrayNode collect(Repository repository, String revision, String guidePrefix, int limit) throws IOException {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Guide history limit must be between 1 and 1000");
        String commit = RepositoryCollector.commit(repository, revision).name();
        String prefix = RepositoryCollector.path(guidePrefix);
        prefix = prefix.isEmpty() ? "" : prefix + "/";
        Map<String, ObjectNode> guides = new TreeMap<>();
        try (TreeWalk tree = new TreeWalk(repository)) {
            tree.addTree(RepositoryCollector.commit(repository, commit).getTree());
            tree.setRecursive(true);
            while (tree.next()) {
                String path = tree.getPathString();
                if (!path.startsWith(prefix)) continue;
                String filename = path.substring(path.lastIndexOf('/') + 1);
                if (filename.startsWith("index.") || filename.startsWith("generator.") || filename.startsWith("_")
                        || !filename.matches(".*\\.(textile|adoc|asciidoc|md|markdown|html(?:\\.haml)?)$")) continue;
                String source = RepositoryCollector.read(repository, commit, path);
                ObjectNode metadata = frontMatter(source, path);
                ObjectNode row = JsonNodeFactory.instance.objectNode().put("id", "git:guide:" + path).put("source", "site-git")
                        .put("source_id", path).put("source_path", path).put("source_url", path).put("revision", commit);
                row.setAll(metadata);
                // Source identity is independent of front matter so metadata cannot rebind the catalog row.
                row.put("id", "git:guide:" + path).put("source", "site-git").put("source_id", path).put("source_path", path).put("revision", commit).put("source_url", path);
                row.set("source_payload", metadata.deepCopy());
                row.set("changes", RepositoryCollector.history(repository, commit, null, path, limit));
                Matcher translation = TRANSLATION.matcher(path);
                if (translation.matches()) {
                    row.put("language", translation.group(2)).put("language_parent", translation.group(1) + translation.group(3)).put("translation", true);
                } else {
                    if (!row.has("language")) row.put("language", "en");
                    row.put("translation", false);
                }
                guides.put(path, row);
            }
        }
        for (ObjectNode guide : guides.values()) {
            ArrayNode languages = guide.putArray("languages");
            for (ObjectNode candidate : guides.values()) {
                if (candidate.path("language_parent").asText().equals(guide.path("source_path").asText())) {
                    languages.addObject().put("source_path", candidate.path("source_path").asText()).put("language", candidate.path("language").asText());
                }
            }
            if (guide.has("language_parent") && !guides.containsKey(guide.path("language_parent").asText())) {
                guide.putArray("diagnostics").add("Translation parent is absent at this revision: " + guide.path("language_parent").asText());
            }
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        guides.values().forEach(result::add);
        return result;
    }

    /** Parses YAML front matter only, retaining original fields and rejecting unterminated or non-object metadata. */
    private ObjectNode frontMatter(String source, String path) throws IOException {
        String normalized = source.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) return JsonNodeFactory.instance.objectNode();
        int end = normalized.indexOf("\n---", 4);
        if (end < 0) throw new IOException("Unterminated guide front matter: " + path);
        JsonNode metadata = yaml.readTree(normalized.substring(4, end));
        if (metadata == null || metadata.isNull()) return JsonNodeFactory.instance.objectNode();
        if (!(metadata instanceof ObjectNode object)) throw new IOException("Guide front matter must be an object: " + path);
        return object;
    }
}
