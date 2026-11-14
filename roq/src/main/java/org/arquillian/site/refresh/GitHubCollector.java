package org.arquillian.site.refresh;

import static org.arquillian.site.refresh.DataHttpClient.encode;
import static org.arquillian.site.refresh.DataHttpClient.required;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.jsoup.nodes.Element;

/** Collects GitHub repository, account and closed-milestone data only when explicitly invoked. */
public final class GitHubCollector {
    private static final Set<String> EXCLUDED = Set.of("arquillian-selenium-bom", "arquillian-container-gae",
            "arquillian_deprecated", "arquillian-container-jruby", "smart-testing-dogfood-repo");
    private final DataHttpClient client;

    /** Shares the configured service origin and authentication without performing a request. */
    public GitHubCollector(DataHttpClient client) {
        this.client = java.util.Objects.requireNonNull(client);
    }

    /** Collects every organization repository page using the legacy repository exclusions. */
    public ArrayNode repositories(String organization) throws IOException {
        URI uri = client.endpoint("orgs/" + encode(organization) + "/repos?per_page=100");
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode repository : client.pages(uri)) {
            String name = required(repository, "name");
            if (repository.hasNonNull("pushed_at") && !EXCLUDED.contains(name)) {
                result.add(repositoryRow(repository, uri));
            }
        }
        return result;
    }

    /** Fetches an explicitly configured repository outside the primary organization as well. */
    public ObjectNode repository(String ownerAndRepository) throws IOException {
        URI uri = client.endpoint("repos/" + repositoryPath(ownerAndRepository));
        return repositoryRow(client.json(uri), uri);
    }

    /** Preserves each account's API contribution count for later commit-author matching. */
    public ArrayNode contributors(String ownerAndRepository) throws IOException {
        return accounts(client.endpoint("repos/" + repositoryPath(ownerAndRepository) + "/contributors?per_page=100"));
    }

    /** Fetches a public account profile with properly escaped bot usernames. */
    public ObjectNode user(String login) throws IOException {
        URI uri = client.endpoint("users/" + encode(login));
        return accountRow(client.json(uri), uri);
    }

    /** Collects every legacy numeric-team membership page for later role assignment. */
    public ArrayNode teamMembers(String teamId) throws IOException {
        return accounts(client.endpoint("teams/" + encode(teamId) + "/members?per_page=100"));
    }

    /** Uses immutable milestone IDs while preserving legacy keys, first-label issue groups and pagination. */
    public ArrayNode releases(String ownerAndRepository, String prefix) throws IOException {
        String repositoryPath = repositoryPath(ownerAndRepository);
        URI milestones = client.endpoint("repos/" + repositoryPath + "/milestones?state=closed&per_page=100");
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode milestone : client.pages(milestones)) {
            String title = required(milestone, "title");
            URI issuesUri = client.endpoint("repos/" + repositoryPath + "/issues?milestone="
                    + encode(required(milestone, "number")) + "&state=closed&per_page=100");
            ArrayNode issues = client.pages(issuesUri);
            ObjectNode release = sourceRow("milestone:", milestone, milestones);
            release.put("legacy_id", title).put("key", prefix == null ? title : prefix + "_" + title)
                    .put("version", title).put("repository", ownerAndRepository).put("html_url", issuesUri.toString());
            copy(milestone, release, "description", "comment");
            release.set("issues", issues);
            ObjectNode groups = release.putObject("resolved_issues");
            for (JsonNode issue : issues) {
                required(issue, "id");
                String number = required(issue, "number");
                String issueTitle = required(issue, "title");
                String url = required(issue, "html_url");
                JsonNode labels = issue.path("labels");
                if (!labels.isMissingNode() && !labels.isNull() && !labels.isArray()) {
                    throw new IOException("Invalid GitHub issue labels array");
                }
                String type = !labels.isArray() || labels.isEmpty() ? "Other" : required(labels.get(0), "name");
                ArrayNode group = groups.has(type) ? (ArrayNode) groups.get(type) : groups.putArray(type);
                group.add(new Element("a").attr("href", url).text("#" + number + " " + issueTitle).outerHtml());
            }
            result.add(release);
        }
        return result;
    }

    /** Normalizes account observations while retaining complete upstream payloads. */
    private ArrayNode accounts(URI uri) throws IOException {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (JsonNode account : client.pages(uri)) {
            result.add(accountRow(account, uri));
        }
        return result;
    }

    /** Retains the legacy lowercase account key and optional public profile details. */
    private ObjectNode accountRow(JsonNode account, URI uri) throws IOException {
        ObjectNode result = sourceRow("user:", account, uri);
        result.put("github_id", required(account, "login").toLowerCase(Locale.ROOT));
        for (String field : new String[]{"name", "html_url", "avatar_url", "email", "gravatar_id", "blog", "location",
                "bio", "company", "created_at", "contributions"}) {
            copy(account, result, field, field);
        }
        return result;
    }

    /** Supplies repository fields consumed by follow-up repository and artifact scanning. */
    private ObjectNode repositoryRow(JsonNode repository, URI uri) throws IOException {
        ObjectNode result = sourceRow("repository:", repository, uri);
        result.put("path", required(repository, "name")).put("relative_path", "")
                .put("owner", required(repository.path("owner"), "login"));
        for (String field : new String[]{"full_name", "description", "clone_url", "git_url", "html_url", "commits_url", "topics"}) {
            copy(repository, result, field, field);
        }
        return result;
    }

    /** Keeps immutable source IDs separate from mutable labels and legacy presentation keys. */
    private ObjectNode sourceRow(String kind, JsonNode payload, URI uri) throws IOException {
        String id = required(payload, "id");
        if (!payload.isObject()) {
            throw new IOException("Expected a GitHub source object");
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode().put("id", "github:" + kind + id)
                .put("source", "github").put("source_url", payload.path("url").asText(uri.toString()));
        result.set("source_id", payload.get("id").deepCopy());
        result.set("source_payload", payload.deepCopy());
        return result;
    }

    /** Copies available source values without fabricating absent metadata. */
    private static void copy(JsonNode source, ObjectNode target, String field, String destination) {
        if (source.has(field)) {
            target.set(destination, source.get(field).deepCopy());
        }
    }

    /** Validates owner/repository input before encoding its two path segments independently. */
    private static String repositoryPath(String repository) {
        String[] parts = repository == null ? new String[0] : repository.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Repository must have owner/name form");
        }
        return encode(parts[0]) + "/" + encode(parts[1]);
    }
}
