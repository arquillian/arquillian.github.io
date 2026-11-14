package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises explicit refresh against local services without changing checked-in snapshots. */
class RefreshCollectorsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> bodies = new HashMap<>();
    private final Map<String, String> links = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private final Map<String, String> authorization = new HashMap<>();
    private final Map<String, Integer> requests = new HashMap<>();
    private HttpServer server;
    private DataHttpClient client;
    @TempDir Path directory;

    /** Starts a loopback-only fixture server so tests never use production credentials. */
    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            requests.merge(path, 1, Integer::sum);
            authorization.put(path, exchange.getRequestHeaders().getFirst("Authorization"));
            if (links.containsKey(path)) {
                exchange.getResponseHeaders().set("Link", links.get(path));
            }
            byte[] body = bodies.getOrDefault(path, "missing fixture").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statuses.getOrDefault(path, bodies.containsKey(path) ? 200 : 404), body.length);
            try (exchange; java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        client = new DataHttpClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture-token");
    }

    /** Closes the fixture server even when an assertion fails. */
    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Follows all Link pages and refuses cycles, foreign hosts, redirects and malformed payloads. */
    @Test
    void validatesRequestsAndPagination() throws Exception {
        bodies.put("/pages?page=1", "[{\"id\":1}]");
        bodies.put("/pages?page=2", "[{\"id\":2}]");
        links.put("/pages?page=1", "<" + client.endpoint("pages?page=2") + ">; rel=\"next\", </last>; rel=\"last\"");
        assertEquals(2, client.pages(client.endpoint("pages?page=1")).size());
        assertEquals("Bearer fixture-token", authorization.get("/pages?page=2"));
        links.put("/pages?page=2", "<" + client.endpoint("pages?page=1") + ">; rel=\"next\"");
        assertThrows(IOException.class, () -> client.pages(client.endpoint("pages?page=1")));
        links.put("/pages?page=1", "<https://example.org/data>; rel=\"next\"");
        assertThrows(IOException.class, () -> client.pages(client.endpoint("pages?page=1")));
        bodies.put("/invalid", "{broken");
        bodies.put("/trailing", "{} {}");
        bodies.put("/object", "{}");
        statuses.put("/redirect", 302);
        assertThrows(IOException.class, () -> client.json(client.endpoint("invalid")));
        assertThrows(IOException.class, () -> client.json(client.endpoint("trailing")));
        assertThrows(IOException.class, () -> client.pages(client.endpoint("object")));
        assertThrows(IOException.class, () -> client.text(client.endpoint("missing")));
        assertThrows(IOException.class, () -> client.text(client.endpoint("redirect")));
        assertThrows(IOException.class, () -> client.text(URI.create("https://example.org/private")));
        assertThrows(IllegalArgumentException.class,
                () -> new DataHttpClient(URI.create("http://example.org"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new DataHttpClient(client.endpoint(""), null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new DataHttpClient(client.endpoint(""), "secret\tvalue"));
    }

    /** Rejects oversized success, error and paginated bodies before retaining untrusted refresh data. */
    @Test
    void rejectsOversizedTextResponses() {
        String oversized = "x".repeat(2 * 1024 * 1024 + 1);
        bodies.put("/large", oversized);
        bodies.put("/large-page", oversized);
        bodies.put("/large-error", oversized);
        statuses.put("/large-error", 404);
        assertTrue(assertThrows(IOException.class, () -> client.text(client.endpoint("large")))
                .getMessage().contains("two MiB"));
        assertTrue(assertThrows(IOException.class, () -> client.pages(client.endpoint("large-page")))
                .getMessage().contains("two MiB"));
        assertTrue(assertThrows(IOException.class, () -> client.text(client.endpoint("large-error")))
                .getMessage().contains("two MiB"));
    }

    /** Reuses a successful cached response across clients without contacting the service again. */
    @Test
    void reusesCachedResponses() throws Exception {
        bodies.put("/cached", "{\"value\":1}");
        DataHttpClient cached = new DataHttpClient(client.endpoint(""), "fixture-token", Duration.ofSeconds(30), directory);
        assertEquals(1, cached.json(cached.endpoint("cached")).path("value").asInt());
        bodies.put("/cached", "{\"value\":2}");
        DataHttpClient offline = new DataHttpClient(client.endpoint(""), null, Duration.ofSeconds(30), directory);
        assertEquals(1, offline.json(offline.endpoint("cached")).path("value").asInt());
        assertEquals(1, requests.get("/cached"));
    }

    /** Keeps GitHub release grouping and immutable milestone identity across repository renames. */
    @Test
    void collectsGitHubReleasesAndRepositoryIdentityData() throws Exception {
        bodies.put("/repos/arquillian/cube/milestones?state=closed&per_page=100", """
                [{"id":5,"number":2,"title":"1.0","description":"Notes","url":"https://api.github.com/milestones/5"}]
                """);
        bodies.put("/repos/arquillian/cube/issues?milestone=2&state=closed&per_page=100", """
                [{"id":7,"number":9,"title":"Fix <tags>","html_url":"https://github.com/arquillian/cube/issues/9",
                  "labels":[{"name":"Bug"},{"name":"Ignored"}]}]
                """);
        links.put("/repos/arquillian/cube/issues?milestone=2&state=closed&per_page=100",
                "</issue-page-2>; rel=\"next\"");
        bodies.put("/issue-page-2", """
                [{"id":8,"number":10,"title":"Unlabelled","html_url":"https://github.com/arquillian/cube/issues/10"}]
                """);
        GitHubCollector github = new GitHubCollector(client);
        JsonNode release = github.releases("arquillian/cube", "cube").get(0);
        assertEquals("github:milestone:5", release.path("id").asText());
        assertEquals("cube_1.0", release.path("key").asText());
        assertEquals("1.0", release.path("legacy_id").asText());
        assertEquals(5, release.path("source_id").asInt());
        assertTrue(release.path("resolved_issues").path("Bug").get(0).asText().contains("Fix &lt;tags&gt;"));
        assertEquals(1, release.path("resolved_issues").path("Other").size());
        assertEquals(2, release.path("issues").size());
        bodies.put("/repos/renamed/cube/milestones?state=closed&per_page=100",
                bodies.get("/repos/arquillian/cube/milestones?state=closed&per_page=100"));
        bodies.put("/repos/renamed/cube/issues?milestone=2&state=closed&per_page=100", "[]");
        ArrayNode renamed = github.releases("renamed/cube", "cube");
        assertEquals(release.path("id"), renamed.get(0).path("id"));
        SnapshotStore store = new SnapshotStore(directory.resolve("github-catalog.json"));
        ObjectNode observation = mapper.createObjectNode();
        observation.putArray("releases").add(release);
        store.merge(observation);
        observation.set("releases", renamed);
        JsonNode historical = store.merge(observation).path("releases");
        assertEquals(1, historical.size());
        assertEquals("renamed/cube", historical.get(0).path("repository").asText());
        assertEquals(release.path("source_payload"), historical.get(0).path("source_payload"));

        String repository = """
                {"id":11,"name":"cube","full_name":"arquillian/cube","owner":{"login":"arquillian"},
                 "pushed_at":"2020-01-01","clone_url":"https://github.com/arquillian/cube.git",
                 "html_url":"https://github.com/arquillian/cube","topics":["testing"]}
                """;
        bodies.put("/orgs/arquillian/repos?per_page=100", "[" + repository + ","
                + "{\"id\":12,\"name\":\"arquillian_deprecated\",\"pushed_at\":\"2020-01-01\"}]");
        bodies.put("/repos/arquillian/cube", repository);
        bodies.put("/repos/arquillian/cube/contributors?per_page=100", "[{\"id\":44,\"login\":\"Someone\",\"contributions\":3}]");
        bodies.put("/users/robot%5Bbot%5D", "{\"id\":45,\"login\":\"robot[bot]\",\"name\":\"Robot\"}");
        bodies.put("/teams/146643/members?per_page=100", "[{\"id\":44,\"login\":\"Someone\"}]");
        assertEquals(1, github.repositories("arquillian").size());
        assertEquals("github:repository:11", github.repository("arquillian/cube").path("id").asText());
        assertEquals(3, github.contributors("arquillian/cube").get(0).path("contributions").asInt());
        assertEquals("robot[bot]", github.user("robot[bot]").path("github_id").asText());
        assertEquals("someone", github.teamMembers("146643").get(0).path("github_id").asText());
    }

    /** Preserves legacy Jira grouping and immutable version/component identity across project renames. */
    @Test
    void collectsLegacyJiraReleaseGroupsAndComponentLeads() throws Exception {
        bodies.put("/rest/api/latest/project/ARQ", """
                {"versions":[{"id":"31","name":"1.0","description":"Release","released":true},
                             {"id":"32","name":"2.0","released":false}]}
                """);
        bodies.put("/secure/ReleaseNote.jspa?projectId=12&version=31&_sscc=t", """
                <textarea id="editcopy">&lt;h2&gt;Bug\\n&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;
                [ARQ-1] - Fish &amp;amp; chips &lt;a href="https://issues.example/browse/ARQ-1"&gt;ARQ-1&lt;/a&gt;
                &lt;/li&gt;&lt;/ul&gt;&lt;h2&gt;Feature&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;ARQ-2&lt;/li&gt;&lt;/ul&gt;</textarea>
                """);
        bodies.put("/rest/api/latest/project/ARQ/components", "[{\"id\":\"3\",\"self\":\""
                + client.endpoint("rest/api/latest/component/3") + "\"}]");
        bodies.put("/rest/api/latest/component/3", """
                {"id":"3","description":"Arquillian Core :: core","lead":{"name":"jdoe","displayName":"Jane Doe"}}
                """);
        JiraCollector jira = new JiraCollector(client);
        ArrayNode releases = jira.releases("ARQ:12", null);
        assertEquals(1, releases.size());
        JsonNode release = releases.get(0);
        assertEquals("jira:version:12:31", release.path("id").asText());
        assertEquals("1.0", release.path("key").asText());
        assertEquals("31", release.path("source_id").asText());
        assertTrue(release.path("resolved_issues").path("Bug").get(0).asText().contains("Fish &amp; chips"));
        assertEquals(1, release.path("resolved_issues").path("Feature").size());
        JsonNode lead = jira.componentLeads("ARQ:12").get(0);
        assertEquals("jira:component:12:3", lead.path("id").asText());
        assertEquals("core", lead.path("component").asText());
        assertEquals("jdoe", lead.path("jboss_username").asText());
        assertEquals("Jane Doe", lead.path("name").asText());
        bodies.put("/rest/api/latest/project/RENAMED", bodies.get("/rest/api/latest/project/ARQ"));
        bodies.put("/rest/api/latest/project/RENAMED/components", bodies.get("/rest/api/latest/project/ARQ/components"));
        ArrayNode renamedReleases = jira.releases("RENAMED:12", null);
        ArrayNode renamedLeads = jira.componentLeads("RENAMED:12");
        assertEquals(release.path("id"), renamedReleases.get(0).path("id"));
        assertEquals(lead.path("id"), renamedLeads.get(0).path("id"));
        SnapshotStore store = new SnapshotStore(directory.resolve("jira-catalog.json"));
        ObjectNode observation = mapper.createObjectNode();
        observation.set("releases", releases);
        observation.putArray("modules").add(lead);
        store.merge(observation);
        observation.set("releases", renamedReleases);
        observation.set("modules", renamedLeads);
        JsonNode historical = store.merge(observation);
        assertEquals(1, historical.path("releases").size());
        assertEquals(1, historical.path("modules").size());
        assertEquals("RENAMED", historical.path("releases").get(0).path("project").asText());
        assertEquals("RENAMED", historical.path("modules").get(0).path("project").asText());
        assertEquals("12", historical.path("modules").get(0).path("project_id").asText());
        assertEquals(lead.path("source_payload"), historical.path("modules").get(0).path("source_payload"));
        assertThrows(IllegalArgumentException.class, () -> jira.releases("ARQ", null));
        bodies.put("/secure/ReleaseNote.jspa?projectId=12&version=31&_sscc=t", "<html>Login required</html>");
        assertThrows(IOException.class, () -> jira.releases("ARQ:12", null));
    }

    /** Preserves history and other domains while producing byte-stable, validated snapshots. */
    @Test
    void mergesSnapshotsWithoutRemovingOrRebindingExistingIdentities() throws Exception {
        Path path = directory.resolve("catalog.json");
        SnapshotStore store = new SnapshotStore(path);
        ObjectNode initial = mapper.createObjectNode();
        initial.putArray("repositories").addObject().put("id", "repo:2").put("name", "Second");
        initial.putArray("identities").addObject().put("id", "github:user:1").put("source", "github")
                .put("source_id", 1).put("github_id", "one");
        store.merge(initial);
        ObjectNode update = mapper.createObjectNode();
        update.putArray("repositories").addObject().put("name", "First").put("id", "repo:1");
        store.merge(update);
        byte[] previous = Files.readAllBytes(path);
        assertEquals(2, store.read().path("repositories").size());
        assertEquals("repo:1", store.read().path("repositories").get(0).path("id").asText());
        assertEquals(1, store.read().path("identities").size());
        assertEquals(0, store.read().path("events").size());
        store.merge(update);
        assertArrayEquals(previous, Files.readAllBytes(path));

        ObjectNode duplicate = mapper.createObjectNode();
        duplicate.putArray("repositories").addObject().put("id", "same");
        ((ArrayNode) duplicate.get("repositories")).addObject().put("id", "same");
        assertThrows(IOException.class, () -> store.merge(duplicate));
        ObjectNode renamed = mapper.createObjectNode();
        renamed.putArray("identities").addObject().put("id", "github:user:NEW").put("source", "github")
                .put("source_id", 1).put("github_id", "one");
        assertThrows(IOException.class, () -> store.merge(renamed));
        ObjectNode changedAccount = mapper.createObjectNode();
        changedAccount.putArray("identities").addObject().put("id", "github:user:1").put("source", "github")
                .put("source_id", 1).put("github_id", "different");
        assertThrows(IOException.class, () -> store.merge(changedAccount));
        ObjectNode reusedAccount = mapper.createObjectNode();
        reusedAccount.putArray("identities").addObject().put("id", "github:user:2").put("source", "github")
                .put("source_id", 2).put("github_id", "one");
        assertThrows(IOException.class, () -> store.merge(reusedAccount));
        ObjectNode invalid = mapper.createObjectNode().put("repositories", "wrong shape");
        assertThrows(IOException.class, () -> store.merge(invalid));
        ObjectNode unknown = mapper.createObjectNode();
        unknown.putArray("unexpected");
        assertThrows(IOException.class, () -> store.merge(unknown));
        bodies.put("/failed-refresh", "upstream unavailable");
        statuses.put("/failed-refresh", 503);
        assertThrows(IOException.class, () -> {
            ObjectNode fetched = mapper.createObjectNode();
            fetched.set("repositories", client.pages(client.endpoint("failed-refresh")));
            store.merge(fetched);
        });
        assertArrayEquals(previous, Files.readAllBytes(path));
        ObjectNode empty = mapper.createObjectNode();
        empty.putArray("repositories");
        store.merge(empty);
        assertArrayEquals(previous, Files.readAllBytes(path));
        ObjectNode reordered = store.read();
        ArrayNode repositories = (ArrayNode) reordered.get("repositories");
        JsonNode first = repositories.remove(0);
        repositories.add(first);
        Files.writeString(path, mapper.writeValueAsString(reordered));
        store.merge(mapper.createObjectNode());
        assertArrayEquals(previous, Files.readAllBytes(path));
    }

    /** Keeps the original bytes when the filesystem refuses to stage the replacement. */
    @Test
    void retainsSnapshotWhenWritingFails() throws Exception {
        Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path path = directory.resolve("catalog.json");
        SnapshotStore store = new SnapshotStore(path);
        store.merge(mapper.createObjectNode());
        byte[] previous = Files.readAllBytes(path);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
        try {
            Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
            Assumptions.assumeFalse(Files.isWritable(directory));
            ObjectNode update = mapper.createObjectNode();
            update.putArray("repositories").addObject().put("id", "new");
            assertThrows(IOException.class, () -> store.merge(update));
            assertArrayEquals(previous, Files.readAllBytes(path));
        } finally {
            Files.setPosixFilePermissions(directory, permissions);
        }
    }
}
