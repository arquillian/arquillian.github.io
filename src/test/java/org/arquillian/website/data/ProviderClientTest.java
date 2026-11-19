package org.arquillian.website.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.arquillian.website.data.github.GitHubClient;
import org.arquillian.website.data.identity.IdentityProvider;
import org.arquillian.website.data.identity.IdentityRecord;
import org.arquillian.website.data.jira.JiraClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies typed provider mapping against deterministic JSON fixtures. */
class ProviderClientTest {

    @TempDir
    Path cacheDirectory;

    /** Confirms GitHub repositories and contributors map through the shared cache seam. */
    @Test
    void mapsGitHubFixtures() throws Exception {
        try (FixtureServer server = new FixtureServer("[{\"name\":\"arquillian-core\",\"description\":\"Core\","
                + "\"owner\":{\"login\":\"arquillian\"},\"html_url\":\"https://github.com/arquillian/arquillian\","
                + "\"git_url\":\"git://github.com/arquillian/arquillian.git\",\"clone_url\":\"https://github.com/arquillian/arquillian.git\","
                + "\"commits_url\":\"https://api.github.com/repos/arquillian/arquillian/commits{/sha}\"}]")) {
            GitHubClient client = new GitHubClient(http());

            GitHubClient.Repository repository = client.repositories(server.uri(), policy("github-repositories")).getFirst();

            assertEquals("arquillian-core", repository.name());
            assertEquals("arquillian", repository.owner());
            assertEquals(URI.create("https://github.com/arquillian/arquillian.git"),
                    GitHubClient.normalizeCloneUrl(URI.create(repository.gitUrl())));
            try (FixtureServer releaseServer = new FixtureServer("[{\"number\":1,\"title\":\"1.0.0.Final\"}]")) {
                assertEquals("1.0.0.Final",
                        client.releases(releaseServer.uri(), policy("github-releases")).getFirst().title());
            }
        }
    }

    /** Confirms ordered GitHub pages combine without sharing cache entries. */
    @Test
    void combinesGitHubPages() throws Exception {
        try (FixtureServer first = new FixtureServer("[{\"name\":\"one\"}]" );
                FixtureServer second = new FixtureServer("[{\"name\":\"two\"}]")) {
            List<GitHubClient.Repository> repositories = new GitHubClient(http()).repositories(
                    List.of(first.uri(), second.uri()), policy("github-page"));

            assertEquals(List.of("one", "two"), repositories.stream().map(GitHubClient.Repository::name).toList());
        }
    }

    /** Confirms optional provider fields degrade to stable empty values. */
    @Test
    void mapsMissingOptionalFields() throws Exception {
        try (FixtureServer server = new FixtureServer(200, "[{\"name\":\"partial\",\"owner\":{}}]")) {
            GitHubClient.Repository repository = new GitHubClient(http()).repositories(
                    server.uri(), policy("github-missing-fields")).getFirst();

            assertEquals("partial", repository.name());
            assertEquals("", repository.description());
            assertEquals("", repository.owner());
        }
    }

    /** Confirms provider failures remain visible instead of becoming empty data. */
    @Test
    void reportsRateLimitFailure() throws Exception {
        try (FixtureServer server = new FixtureServer(429, "{\"message\":\"rate limit\"}")) {
            assertThrows(IOException.class,
                    () -> new GitHubClient(http()).repositories(server.uri(), policy("github-rate-limit")));
        }
    }

    /** Confirms JIRA project versions and component leads map into stable records. */
    @Test
    void mapsJiraFixtures() throws Exception {
        try (FixtureServer server = new FixtureServer("{\"key\":\"ARQ\",\"name\":\"Arquillian\","
                + "\"versions\":[{\"id\":\"1\",\"name\":\"1.0.0.Final\",\"released\":true,"
                + "\"description\":\"Initial release\"}]}")) {
            JiraClient.Project project = new JiraClient(http()).project(server.uri(), policy("jira-project"));

            assertEquals("ARQ", project.key());
            assertTrue(project.versions().getFirst().released());
        }
    }

    /** Confirms manual identity YAML remains available without provider credentials. */
    @Test
    void loadsManualIdentityFixture() throws Exception {
        Path mapping = cacheDirectory.resolve("identities.yml");
        Files.writeString(mapping, "- github_id: aslakknutsen\n  role: Project Lead\n");

        IdentityRecord identity = new IdentityProvider().load(mapping).getFirst();

        assertEquals("aslakknutsen", identity.githubId());
        assertEquals("Project Lead", identity.role());
    }

    /** Creates a normal cached client for provider fixture requests. */
    private CachedHttpClient http() {
        return new CachedHttpClient(cacheDirectory, HttpClient.newHttpClient(), RefreshPolicy.NORMAL);
    }

    /** Creates a cache policy with the provider-specific stable key used by a fixture. */
    private CachePolicy policy(String key) {
        return new CachePolicy(key, Duration.ofHours(1), true);
    }

    /** Serves one deterministic provider response without network dependencies. */
    private static final class FixtureServer implements AutoCloseable {
        private final HttpServer server;

        FixtureServer(String body) throws IOException {
            this(200, body);
        }

        FixtureServer(int status, String body) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/fixture", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            });
            server.start();
        }

        /** Returns the fixture endpoint URI. */
        URI uri() {
            return URI.create("http://localhost:" + server.getAddress().getPort() + "/fixture");
        }

        /** Stops the fixture endpoint. */
        @Override
        public void close() {
            server.stop(0);
        }
    }
}
