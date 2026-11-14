package org.arquillian.site.refresh;

import static org.arquillian.site.refresh.DataHttpClient.encode;
import static org.arquillian.site.refresh.DataHttpClient.required;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/** Collects legacy Jira release notes and component leads from an explicitly configured deployment. */
public final class JiraCollector {
    private static final Pattern COMPONENT = Pattern.compile(" :: (\\S+)$");
    private final DataHttpClient client;

    /** Retains the service client without contacting Jira during initialization. */
    public JiraCollector(DataHttpClient client) {
        this.client = java.util.Objects.requireNonNull(client);
    }

    /** Keys released versions by numeric project identity while preserving historical release-key prefixes. */
    public ArrayNode releases(String projectKeyAndId, String prefix) throws IOException {
        String[] project = project(projectKeyAndId);
        URI uri = client.endpoint("rest/api/latest/project/" + encode(project[0]));
        JsonNode versions = client.json(uri, Duration.ofDays(1)).path("versions");
        if (!versions.isArray()) {
            throw new IOException("Missing Jira versions array");
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode version : versions) {
            if (!version.path("released").isBoolean()) {
                throw new IOException("Missing Jira released flag");
            }
            if (!version.path("released").booleanValue()) {
                continue;
            }
            String id = required(version, "id");
            String name = required(version, "name");
            URI notes = client.endpoint("secure/ReleaseNote.jspa?projectId=" + encode(project[1])
                    + "&version=" + encode(id) + "&_sscc=t");
            ObjectNode release = sourceRow("version", project[1], version, notes);
            release.put("legacy_id", id).put("key", prefix == null ? name : prefix + "_" + name)
                    .put("version", name).put("project", project[0]).put("html_url", notes.toString());
            if (version.has("description")) {
                release.set("comment", version.get("description").deepCopy());
            }
            release.set("resolved_issues", issueGroups(client.text(notes)));
            result.add(release);
        }
        return result;
    }

    /** Keys components by numeric project identity and retains their current locator and legacy lead mapping. */
    public ArrayNode componentLeads(String projectKeyAndId) throws IOException {
        String[] project = project(projectKeyAndId);
        String key = project[0];
        JsonNode components = client.json(client.endpoint("rest/api/latest/project/" + encode(key) + "/components"));
        if (!components.isArray()) {
            throw new IOException("Expected a Jira components array");
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode summary : components) {
            URI uri;
            try {
                uri = URI.create(required(summary, "self"));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid Jira component URI");
            }
            JsonNode component = client.json(uri);
            Matcher suffix = COMPONENT.matcher(component.path("description").asText(""));
            if (!component.path("lead").isObject() || !suffix.find()) {
                continue;
            }
            ObjectNode lead = sourceRow("component", project[1], component, uri);
            lead.put("project", key).put("component", suffix.group(1)).put("name", required(component.path("lead"), "displayName"))
                    .put("jboss_username", required(component.path("lead"), "name"));
            result.add(lead);
        }
        return result;
    }

    /** Decodes legacy textarea markup without silently accepting a login page as empty release notes. */
    private ObjectNode issueGroups(String html) throws IOException {
        Document document = Jsoup.parse(html.replace("\\\"", "\""));
        Element container = document.getElementById("editcopy");
        if (container == null) {
            document = Jsoup.parse(Parser.unescapeEntities(html, false).replace("\\\"", "\""));
            container = document.getElementById("editcopy");
        }
        if (container == null) {
            throw new IOException("Missing Jira release-note container");
        }
        if (container.normalName().equals("textarea") || container.children().isEmpty()) {
            container = Jsoup.parseBodyFragment(container.wholeText().replace("\\n", "")).body();
        }
        ObjectNode groups = JsonNodeFactory.instance.objectNode();
        for (Element list : container.children()) {
            if (!list.normalName().equals("ul")) {
                continue;
            }
            Element heading = list.previousElementSibling();
            if (heading == null || heading.text().replace("\\n", "").isBlank()) {
                throw new IOException("Missing Jira release-note issue group heading");
            }
            String type = heading.text().replace("\\n", "").strip();
            ArrayNode group = groups.has(type) ? (ArrayNode) groups.get(type) : groups.putArray(type);
            for (Element issue : list.children()) {
                if (issue.normalName().equals("li")) {
                    group.add(issue.html().replace("\\n", "").strip());
                }
            }
        }
        return groups;
    }

    /** Uses numeric project and entity IDs so project-key renames cannot rebind catalog identity. */
    private ObjectNode sourceRow(String kind, String project, JsonNode payload, URI uri) throws IOException {
        ObjectNode row = JsonNodeFactory.instance.objectNode()
                .put("id", "jira:" + kind + ":" + project + ":" + required(payload, "id"))
                .put("source", "jira").put("project_id", project).put("source_url", uri.toString());
        row.set("source_id", payload.get("id").deepCopy());
        row.set("source_payload", payload.deepCopy());
        return row;
    }

    /** Retains the legacy explicit project ID because release-note URLs require it. */
    private static String[] project(String value) {
        String[] parts = value == null ? new String[0] : value.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || !parts[1].matches("[0-9]+")) {
            throw new IllegalArgumentException("Jira project must have KEY:numeric-id form");
        }
        return parts;
    }
}
