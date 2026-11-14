package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the public CLI/coordinator with local HTTP/Git and temporary copies of the authoritative six-domain schema. */
class RefreshCoordinatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
    @TempDir Path directory;

    /** Proves full staged refresh, complete mapped additions, deterministic publication, dry run and fetched-default advancement. */
    @Test
    void refreshesAllDomainsAndFetchedDefaultBranch() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            byte[] original = Files.readAllBytes(fixture.catalog);
            ObjectNode dryRun = RefreshCli.run(new String[] {"--config", fixture.configuration.toString(), "--dry-run"}, Map.of("SITE_TOKEN", "fixture-secret"), CLOCK);
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            assertEquals(2, dryRun.path("modules").size());
            assertEquals(2, dryRun.path("releases").size());
            JsonNode person = find(dryRun.path("identities"), fixture.personId);
            assertEquals("Current company", person.path("company").asText());
            assertTrue(dryRun.path("identities").valueStream().allMatch(identity -> identity.path("directory_cards").isArray()),
                    "Unlinked observed identities remain explicitly unlisted in the native contributor directory");
            assertTrue(person.path("core").asBoolean());
            assertTrue(person.path("commits").asLong() >= 1);
            assertTrue(find(dryRun.path("guides"), fixture.guideId).path("footer_html").asText().contains("Fixture guide history"));
            assertFalse(dryRun.path("events").get(0).path("active").asBoolean());
            assertFalse(dryRun.path("events").get(0).path("upcoming").asBoolean());
            ObjectNode published = fixture.refresh(false);
            assertEquals(dryRun, published);
            byte[] first = Files.readAllBytes(fixture.catalog);
            assertEquals(published, fixture.refresh(false));
            assertArrayEquals(first, Files.readAllBytes(fixture.catalog));
            assertTrue(fixture.authenticated.get() > 0);
            String firstCommit = published.path("repositories").get(0).path("commit").asText();
            Files.writeString(fixture.origin.resolve("README.md"), "Advanced remote default branch\n");
            fixture.commit("New fetched default", "2026-11-14T12:00:00Z");
            ObjectNode second = fixture.refresh(true);
            assertNotEquals(firstCommit, second.path("repositories").get(0).path("commit").asText());
            assertTrue(find(second.path("modules"), fixture.moduleId).path("activity").path("message").asText().contains("New fetched default"));
            assertArrayEquals(first, Files.readAllBytes(fixture.catalog));
            try (Git cache = Git.open(directory.resolve("cache/github-7.git").toFile())) {
                assertEquals(firstCommit, cache.getRepository().resolve("HEAD").name(), "Bare HEAD stays stale while origin tracking advances");
            }
        }
    }

    /** Retains byte-identical catalog data after a late binding failure or malformed explicitly enabled retired service. */
    @Test
    void preservesBytesOnLaterStageAndRetiredSourceFailure() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.refresh(false);
            byte[] original = Files.readAllBytes(fixture.catalog);
            ObjectNode invalid = fixture.settings.deepCopy();
            ((ObjectNode) invalid.path("mappings").path("guide_bindings").get(0)).put("id", "unknown:guide");
            fixture.writeConfiguration(invalid);
            assertThrows(IOException.class, () -> fixture.refresh(false));
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            assertThrows(IOException.class, () -> fixture.refresh(true), "Dry run applies the same late-stage validation");
            invalid = fixture.settings.deepCopy();
            invalid.putObject("lanyrd").put("enabled", true).put("base_uri", fixture.base.toString()).put("query", "Arquillian");
            fixture.writeConfiguration(invalid);
            assertThrows(IOException.class, () -> fixture.refresh(false));
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            fixture.failureStatus = 503;
            fixture.writeConfiguration(fixture.settings);
            assertThrows(IOException.class, () -> fixture.refresh(false));
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
        }
    }

    /** Leaves the publication byte-identical when a refreshed component resolves only one required current artifact. */
    @Test
    void rejectsPartialCurrentArtifactMappingAtomically() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            byte[] original = Files.readAllBytes(fixture.catalog);
            ObjectNode settings = fixture.settings.deepCopy();
            ((ArrayNode) settings.path("mappings").path("components").get(0).path("artifacts")).addObject()
                    .put("groupId", "org.example").put("artifactId", "missing-api");
            fixture.writeConfiguration(settings);
            IOException failure = assertThrows(IOException.class, () -> fixture.refresh(false));
            assertTrue(failure.getMessage().contains("org.example:missing-api"));
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            assertThrows(IOException.class, () -> fixture.refresh(true));
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
        }
    }

    /** Classifies a session by its own time even when its multiday conference began earlier. */
    @Test
    void classifiesSessionBeforeConferenceFallback() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            ObjectNode existing = (ObjectNode) MAPPER.readTree(Files.readString(fixture.catalog));
            ObjectNode session = ((ArrayNode) existing.path("events")).addObject()
                    .put("id", "lanyrd:session:future").put("source", "lanyrd").put("source_id", "future")
                    .put("conference_start_date", "2026-11-12").put("start_datetime", "2026-11-13T14:00:00Z")
                    .put("end_datetime", "2026-11-13T15:00:00Z").put("timezone", "UTC");
            Files.writeString(fixture.catalog, MAPPER.writeValueAsString(existing));
            byte[] original = Files.readAllBytes(fixture.catalog);
            JsonNode upcoming = find(fixture.refresh(true).path("events"), "lanyrd:session:future");
            assertTrue(upcoming.path("upcoming").asBoolean(), "Future session starts after the refresh clock");
            assertTrue(upcoming.path("active").asBoolean(), "Future session has not ended");
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            session.put("start_datetime", "2026-11-13T11:00:00Z");
            Files.writeString(fixture.catalog, MAPPER.writeValueAsString(existing));
            JsonNode underway = find(fixture.refresh(true).path("events"), "lanyrd:session:future");
            assertFalse(underway.path("upcoming").asBoolean());
            assertTrue(underway.path("active").asBoolean());
            session.remove("start_datetime"); session.remove("end_datetime");
            session.put("conference_start_date", "2026-11-14");
            Files.writeString(fixture.catalog, MAPPER.writeValueAsString(existing));
            JsonNode conferenceOnly = find(fixture.refresh(true).path("events"), "lanyrd:session:future");
            assertTrue(conferenceOnly.path("upcoming").asBoolean());
            assertTrue(conferenceOnly.path("active").asBoolean());
        }
    }

    /** Rejects contradictory trusted identity selectors before the only possible publication. */
    @Test
    void rejectsAmbiguousIdentityWithoutWriting() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            ObjectNode existing = (ObjectNode) MAPPER.readTree(Files.readString(fixture.catalog));
            ObjectNode person = find(existing.path("identities"), fixture.personId).deepCopy();
            person.put("id", "manual:identity:other").put("source", "manual").put("source_id", "other")
                    .put("github_id", "other").put("email", "collision@example.org").put("link", "/community/other/");
            ((ArrayNode) existing.path("identities")).add(person);
            Files.writeString(fixture.catalog, MAPPER.writeValueAsString(existing));
            fixture.profileEmail = "collision@example.org";
            byte[] original = Files.readAllBytes(fixture.catalog);
            assertThrows(IOException.class, () -> fixture.refresh(false));
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
        }
    }

    /** Exercises strict CLI/config syntax, named environment tokens, protected origins and disabled retired services. */
    @Test
    void validatesCliConfigurationAndTokens() throws Exception {
        for (String[] arguments : List.of(new String[0], new String[] {"--config"}, new String[] {"--token", "secret"}, new String[] {"--dry-run", "--dry-run"})) {
            assertThrows(IOException.class, () -> RefreshCli.run(arguments, Map.of(), CLOCK));
        }
        Path fallbackPath = Path.of("target/refresh.yml");
        Files.deleteIfExists(fallbackPath);
        RefreshConfig fallback = RefreshConfig.load(fallbackPath, Map.of("SITE_GITHUB_TOKEN", "fixture-secret"));
        assertTrue(fallback.data().path("cache").asText().endsWith(".cache/refresh"));
        assertEquals("SITE_GITHUB_TOKEN", fallback.data().path("github").path("token_env").asText());
        assertEquals(3, fallback.data().path("github").path("teams").size());
        assertEquals(13, fallback.data().path("github").path("release_sources").size());
        assertEquals(5, fallback.data().path("jira").path("release_sources").size());
        assertFalse(Files.exists(fallbackPath));
        try (Fixture fixture = new Fixture(directory)) {
            assertThrows(IOException.class, () -> RefreshConfig.load(fixture.configuration, Map.of()));
            ObjectNode invalid = fixture.settings.deepCopy(); invalid.put("token", "must-not-log"); fixture.writeConfiguration(invalid);
            IOException failure = assertThrows(IOException.class, () -> RefreshConfig.load(fixture.configuration, Map.of("SITE_TOKEN", "fixture-secret")));
            assertFalse(failure.getMessage().contains("must-not-log"));
            invalid = fixture.settings.deepCopy(); ((ObjectNode) invalid.path("github")).put("base_uri", "https://user:secret@example.org/"); fixture.writeConfiguration(invalid);
            assertThrows(IOException.class, () -> RefreshConfig.load(fixture.configuration, Map.of("SITE_TOKEN", "fixture-secret")));
            invalid = fixture.settings.deepCopy(); invalid.putObject("lanyrd").put("enabled", false).put("base_uri", "http://unavailable.invalid/"); fixture.writeConfiguration(invalid);
            assertNotNull(fixture.refresh(true), "Disabled retired sources perform no validation fetch");
            invalid = fixture.settings.deepCopy(); invalid.putObject("guides").put("prefix", "guides").put("revision", "other-branch"); fixture.writeConfiguration(invalid);
            assertThrows(IOException.class, () -> fixture.refresh(true));
            invalid = fixture.settings.deepCopy(); ((ObjectNode) invalid.path("github")).put("minimum_repositories", "oops"); fixture.writeConfiguration(invalid);
            assertThrows(IOException.class, () -> RefreshConfig.load(fixture.configuration, Map.of("SITE_TOKEN", "fixture-secret")));
            invalid = fixture.settings.deepCopy(); ((ObjectNode) invalid.path("github").path("repositories").get(0)).put("contributors", "false"); fixture.writeConfiguration(invalid);
            assertThrows(IOException.class, () -> RefreshConfig.load(fixture.configuration, Map.of("SITE_TOKEN", "fixture-secret")));
        }
    }

    /** Refreshes later native-file commits and automatically announces a trusted new version under one component policy. */
    @Test
    void refreshesNativeGuideHistoryAndNewKnownComponentVersion() throws Exception {
        try (Fixture fixture = new Fixture(directory)) {
            fixture.refresh(false);
            byte[] original = Files.readAllBytes(fixture.catalog);
            ObjectNode settings = fixture.settings.deepCopy();
            ObjectNode policy = ((ObjectNode) settings.path("mappings").path("components").get(0)).putObject("announcement_policy");
            policy.put("slug_prefix", "arquillian-algeron").put("since", "2026-11-14").put("author", "tag-committer")
                    .put("publication_date", "release-date").put("comment_origin", "http://arquillian.org").put("comment_id", "canonical-url");
            policy.putArray("owners").add("arquillian"); policy.putArray("tags").add("release").add("extension").add("algeron-extension");
            String nativePath = "content/guides/example.adoc";
            ((ObjectNode) settings.path("mappings").path("guide_bindings").get(0)).put("native_path", nativePath);
            Files.createDirectories(fixture.origin.resolve("content/guides"));
            Files.writeString(fixture.origin.resolve(nativePath), "---\ntitle: Example\nauthors: [38leinad]\nsource_path: guides/example.textile\n---\n= Native guide\nCorrection\n");
            Files.delete(fixture.origin.resolve("guides/example.textile"));
            for (String path : List.of("pom.xml", "new/pom.xml")) Files.writeString(fixture.origin.resolve(path), Files.readString(fixture.origin.resolve(path)).replace("1.0.1", "1.0.2"));
            fixture.commit("Native guide correction", "2026-11-15T12:00:00Z");
            assertNull(RepositoryCollector.read(fixture.git.getRepository(), "HEAD", "guides/example.textile"));
            fixture.git.tag().setName("1.0.2").setAnnotated(false).setSigned(false).call();
            fixture.notesVersion = "1.0.2";
            fixture.writeConfiguration(settings);
            ObjectNode preview = fixture.refresh(true);
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            assertEquals(3, preview.path("releases").size());
            JsonNode release = preview.path("releases").valueStream().filter(row -> row.path("module_id").asText().equals(fixture.moduleId) && row.path("version").asText().equals("1.0.2")).findFirst().orElseThrow();
            assertEquals("/blog/2026/11/15/arquillian-algeron-1-0-2/", release.path("link").asText());
            assertEquals("2026-11-15", release.path("published_date").asText());
            assertEquals("Daniel Platz", release.path("author").asText());
            assertTrue(release.path("artifacts").toString().contains("1.0.2"));
            assertTrue(release.path("resolved_issues").toString().contains("Resolved Jira issue"));
            JsonNode guide = find(preview.path("guides"), fixture.guideId);
            assertEquals("guides/example.textile", guide.path("source_path").asText());
            assertEquals(nativePath, guide.path("native_path").asText());
            assertEquals(2, guide.path("changes").size());
            assertEquals(2, guide.path("changes").valueStream().map(row -> row.path("commit").asText()).distinct().count());
            assertTrue(guide.path("footer_html").asText().contains("Fixture guide history"));
            assertTrue(guide.path("footer_html").asText().contains("Native guide correction"));
            assertTrue(guide.path("authors_html").asText().contains("Nov 15, 2026"));
            assertEquals(preview, fixture.refresh(false));
            byte[] published = Files.readAllBytes(fixture.catalog);
            fixture.refresh(false);
            assertArrayEquals(published, Files.readAllBytes(fixture.catalog));
        }
    }

    /** Confirms preview shares source identity/collision checks but never creates a file on first use. */
    @Test
    void previewUsesPublicationValidationWithoutWriting() throws Exception {
        Path catalog = directory.resolve("new/catalog.json");
        SnapshotStore store = new SnapshotStore(catalog);
        ObjectNode updates = JSON.objectNode(); updates.putArray("repositories").addObject().put("id", "manual:r").put("source", "manual").put("source_id", "r");
        assertEquals(1, store.preview(updates).path("repositories").size());
        assertFalse(Files.exists(catalog.getParent()));
        store.merge(updates);
        byte[] original = Files.readAllBytes(catalog);
        ((ObjectNode) updates.path("repositories").get(0)).put("source_id", "rebound");
        assertThrows(IOException.class, () -> store.preview(updates));
        assertArrayEquals(original, Files.readAllBytes(catalog));
    }

    /** Refreshes actual generator fields from an advancing bare remote and retains publication bytes after incomplete Universe input. */
    @Test
    void refreshesUniverseFromFetchedBareRemote() throws Exception {
        Path origin = directory.resolve("universe-origin"); Path remote = directory.resolve("universe.git");
        try (Fixture fixture = new Fixture(directory); Git git = Git.init().setInitialBranch("main").setDirectory(origin.toFile()).call()) {
            universeProject(origin, "Drone &amp; Friends");
            universeCommit(git, "Initial Universe release", "2026-11-10T12:00:00Z");
            git.tag().setName("2.0.0").setAnnotated(false).setSigned(false).call();
            try (Git bare = Git.cloneRepository().setURI(origin.toUri().toString()).setBare(true).setDirectory(remote.toFile()).call()) {
                assertTrue(bare.getRepository().isBare());
            }
            ObjectNode universe = CatalogAssemblerTest.universeFixture();
            ObjectNode existing = (ObjectNode) MAPPER.readTree(Files.readString(fixture.catalog));
            ((ArrayNode) existing.path("repositories")).addAll((ArrayNode) universe.path("repositories"));
            ((ArrayNode) existing.path("modules")).addAll((ArrayNode) universe.path("modules"));
            Files.writeString(fixture.catalog, MAPPER.writeValueAsString(existing));
            String moduleId = universe.path("modules").get(0).path("id").asText();
            JsonNode oldHistory = universe.path("modules").get(0).path("release_history").get(0).deepCopy();
            ObjectNode settings = fixture.settings.deepCopy();
            ((ArrayNode) settings.path("github").path("repositories")).addObject().put("repository", "arquillian/arquillian-universe-bom")
                    .put("origin", remote.toUri().toString()).put("contributors", false);
            fixture.writeConfiguration(settings);
            ObjectNode published = fixture.refresh(false);
            JsonNode module = find(published.path("modules"), moduleId);
            assertEquals("Drone & Friends", module.path("extensions").get(0).path("name").asText());
            assertEquals(List.of("arquillian-drone", "arquillian-graphene"), module.path("extensions").valueStream().map(row -> row.path("artifactId").asText()).toList());
            assertEquals("2.0.0", module.path("release_history").get(0).path("version").asText());
            assertTrue(module.path("metadata_html").asText().contains("2.0.0"));
            assertTrue(module.path("metadata_html").asText().contains("2026-11-10"));
            assertEquals(oldHistory, module.path("release_history").valueStream().filter(row -> row.path("version").equals(oldHistory.path("version"))).findFirst().orElseThrow());
            assertEquals(universe.path("modules").get(0).path("release_history").valueStream().toList(), module.path("release_history").valueStream().skip(1).toList(), "All historical rows retain their field bytes and same-day order");
            byte[] original = Files.readAllBytes(fixture.catalog);
            String firstCommit = git.getRepository().resolve("HEAD").name();
            universeProject(origin, "Updated Drone"); universeCommit(git, "Rename current Universe extension", "2026-11-14T12:00:00Z");
            git.push().setRemote(remote.toUri().toString()).setRefSpecs(new org.eclipse.jgit.transport.RefSpec("refs/heads/main:refs/heads/main")).call();
            ObjectNode preview = fixture.refresh(true);
            assertEquals("Updated Drone", find(preview.path("modules"), moduleId).path("extensions").get(0).path("name").asText());
            assertArrayEquals(original, Files.readAllBytes(fixture.catalog));
            try (Git cache = Git.open(directory.resolve("cache/github-8.git").toFile())) {
                assertEquals(firstCommit, cache.getRepository().resolve("HEAD").name(), "Generator input must follow the fetched default, not stale bare HEAD");
            }
            assertEquals(preview, fixture.refresh(false));
            byte[] current = Files.readAllBytes(fixture.catalog); fixture.refresh(false);
            assertArrayEquals(current, Files.readAllBytes(fixture.catalog));
            Path bom = origin.resolve("arquillian-universe/pom.xml");
            Files.writeString(bom, Files.readString(bom).replace("<groupId>org.arquillian.universe</groupId>", "<groupId>missing.group</groupId>"));
            universeCommit(git, "Incomplete Universe coordinates", "2026-11-15T12:00:00Z");
            git.push().setRemote(remote.toUri().toString()).setRefSpecs(new org.eclipse.jgit.transport.RefSpec("refs/heads/main:refs/heads/main")).call();
            assertThrows(IOException.class, () -> fixture.refresh(false));
            assertArrayEquals(current, Files.readAllBytes(fixture.catalog));
        }
    }

    /** Builds real recursive POM inputs so the fixture exercises ArtifactScanner's dependency-management and name contract. */
    private static void universeProject(Path origin, String droneName) throws IOException {
        for (String artifact : List.of("arquillian-universe", "arquillian-drone", "arquillian-graphene")) Files.createDirectories(origin.resolve(artifact));
        Files.writeString(origin.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>org.arquillian.universe</groupId><artifactId>arquillian-universe-parent</artifactId><version>2.0.0</version><packaging>pom</packaging><modules><module>arquillian-universe</module><module>arquillian-drone</module><module>arquillian-graphene</module></modules></project>");
        Files.writeString(origin.resolve("arquillian-universe/pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>org.arquillian</groupId><artifactId>arquillian-universe</artifactId><version>2.0.0</version><packaging>pom</packaging><dependencyManagement><dependencies><dependency><groupId>org.arquillian.universe</groupId><artifactId>arquillian-drone</artifactId><version>2.0.0</version></dependency><dependency><groupId>org.arquillian.universe</groupId><artifactId>arquillian-graphene</artifactId><version>2.0.0</version></dependency></dependencies></dependencyManagement></project>");
        for (String artifact : List.of("arquillian-drone", "arquillian-graphene")) Files.writeString(origin.resolve(artifact + "/pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>org.arquillian.universe</groupId><artifactId>" + artifact + "</artifactId><version>2.0.0</version><packaging>pom</packaging><name>" + (artifact.equals("arquillian-drone") ? droneName : "Graphene") + "</name></project>");
    }

    /** Records deterministic fixture revisions without inheriting user signing settings. */
    private static void universeCommit(Git git, String message, String date) throws Exception {
        PersonIdent person = new PersonIdent("Trusted", "trusted@example.org", Instant.parse(date), ZoneOffset.UTC);
        git.add().addFilepattern(".").call(); git.commit().setSign(false).setMessage(message).setAuthor(person).setCommitter(person).call();
    }

    /** Finds one canonical row without depending on SnapshotStore's sorted array order. */
    private static ObjectNode find(JsonNode rows, String id) {
        for (JsonNode row : rows) if (row.path("id").asText().equals(id)) return (ObjectNode) row;
        throw new AssertionError("Missing canonical fixture row " + id);
    }

    /** Owns a local service and Git origin so tests exercise real collectors without production network/catalog effects. */
    private static final class Fixture implements AutoCloseable {
        private final Path root;
        private final Path catalog;
        private final Path origin;
        private final Path configuration;
        private final Git git;
        private final HttpServer server;
        private final URI base;
        private final ObjectNode settings;
        private final String personId;
        private final String moduleId;
        private final String guideId;
        private final AtomicInteger authenticated = new AtomicInteger();
        private String profileEmail = "trusted@example.org";
        private String notesVersion = "1.0.1";
        private int failureStatus;

        /** Creates deterministic local HTTP/Git inputs and a temporary published-schema snapshot copy. */
        private Fixture(Path directory) throws Exception {
            root = directory;
            Files.createDirectories(directory);
            catalog = directory.resolve("catalog.json"); origin = directory.resolve("origin"); configuration = directory.resolve("refresh.json");
            git = Git.init().setInitialBranch("main").setDirectory(origin.toFile()).call();
            Files.createDirectories(origin.resolve("new")); Files.createDirectories(origin.resolve("guides"));
            Files.writeString(origin.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>example-api</artifactId><version>1.0.1</version><modules><module>new</module></modules></project>");
            Files.writeString(origin.resolve("new/pom.xml"), "<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>new-api</artifactId><version>1.0.1</version></project>");
            Files.writeString(origin.resolve("guides/example.textile"), "---\ntitle: Example\nauthor: 38leinad\n---\nGuide body\n");
            commit("Fixture guide history", "2026-11-01T12:00:00Z");
            git.tag().setName("1.0.1").setAnnotated(false).setSigned(false).call();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            server.createContext("/", this::respond); server.start();
            ObjectNode existing = CatalogAssemblerTest.fixture();
            personId = existing.path("identities").get(0).path("id").asText(); moduleId = existing.path("modules").get(0).path("id").asText(); guideId = existing.path("guides").get(0).path("id").asText();
            Files.writeString(catalog, MAPPER.writeValueAsString(existing));
            settings = JSON.objectNode().put("catalog", catalog.toString()).put("cache", directory.resolve("cache").toString()).put("site_repository", origin.toString())
                    .put("manual_identities", Path.of("data/identities.yml").toAbsolutePath().normalize().toString()).put("github_mapping", Path.of("data/github_mapping.yml").toAbsolutePath().normalize().toString());
            ObjectNode mappings = CatalogAssemblerTest.mappings(existing);
            mappings.putArray("identity_overrides").addObject().put("id", personId).put("jboss_username", "trusted-jboss");
            ObjectNode module = existing.path("modules").get(0).deepCopy(); module.put("id", "manual:module:new").put("source", "manual").put("source_id", "new").put("link", "/modules/new-extension/").put("title", "New Extension").put("name", "New");
            module.withObject("directory").put("order", 75).put("label", "New Extension");
            module.putArray("release_history"); module.putArray("artifact_blocks");
            ObjectNode component = ((ArrayNode) mappings.path("components")).addObject().put("id", "manual:module:new").put("repository_id", module.path("repository_id").asText()); component.set("create", module);
            component.putArray("artifacts").addObject().put("groupId", "org.example").put("artifactId", "new-api");
            ObjectNode release = existing.path("releases").get(0).deepCopy(); release.put("id", "manual:release:new").put("source", "manual").put("source_id", "new").put("link", "/blog/2026/11/13/new-extension-1-0-1/").put("module_link", "/modules/new-extension/").put("issues_count", 0);
            mappings.putArray("announcements").addObject().put("id", "manual:release:new").put("module_id", "manual:module:new").put("version", "1.0.1").set("create", release);
            settings.set("mappings", mappings);
            ObjectNode github = settings.putObject("github").put("base_uri", base.toString()).put("token_env", "SITE_TOKEN");
            github.putArray("organizations").add("arquillian"); github.putArray("repositories").addObject().put("repository", "arquillian/arquillian-algeron").put("origin", origin.toUri().toString());
            github.putArray("users").add("38leinad"); github.putArray("teams").addObject().put("id", "1").put("team", "core");
            github.putArray("release_sources").addObject().put("repository", "arquillian/arquillian-algeron").put("module_id", moduleId);
            ObjectNode jira = settings.putObject("jira").put("base_uri", base.toString()); jira.putArray("release_sources").addObject().put("project", "EX:1").put("module_id", moduleId);
            jira.putArray("component_sources").addObject().put("project", "EX:1").putObject("components").put("example", moduleId);
            settings.putObject("guides").put("prefix", "guides").put("limit", 5);
            writeConfiguration(settings);
        }

        /** Publishes or previews only this fixture's temporary catalog using named environment credentials. */
        private ObjectNode refresh(boolean dryRun) throws IOException {
            clearHttpCache();
            return new RefreshCoordinator(RefreshConfig.load(configuration, Map.of("SITE_TOKEN", "fixture-secret")), CLOCK).refresh(dryRun);
        }

        /** Removes only HTTP response entries so each fixture call observes its deliberately changed service payloads. */
        private void clearHttpCache() throws IOException {
            Path cache = root.resolve("cache");
            if (!Files.isDirectory(cache)) return;
            try (var paths = Files.walk(cache)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(value -> value.toString().endsWith(".body") || value.toString().endsWith(".headers")).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }

        /** Writes caller-selected valid/invalid configuration for trust-boundary regressions. */
        private void writeConfiguration(ObjectNode value) throws IOException { Files.writeString(configuration, MAPPER.writeValueAsString(value)); }

        /** Creates author/committer-identical deterministic revisions without invoking a command-line Git process. */
        private void commit(String message, String date) throws Exception {
            PersonIdent person = new PersonIdent("Trusted", "trusted@example.org", Instant.parse(date), ZoneOffset.UTC);
            git.add().addFilepattern(".").call(); git.commit().setSign(false).setMessage(message).setAuthor(person).setCommitter(person).call();
        }

        /** Serves realistic collector payloads and fails unknown/retired source routes explicitly. */
        private void respond(HttpExchange exchange) throws IOException {
            if ("Bearer fixture-secret".equals(exchange.getRequestHeaders().getFirst("Authorization"))) authenticated.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            String profile = "{\"id\":413413,\"login\":\"38leinad\",\"email\":\"" + profileEmail + "\",\"company\":\"Current company\",\"bio\":\"Updated biography\"}";
            String repository = "{\"id\":7,\"name\":\"arquillian-algeron\",\"full_name\":\"arquillian/arquillian-algeron\",\"owner\":{\"login\":\"arquillian\"},\"pushed_at\":\"2026-11-01\",\"html_url\":\"https://github.com/arquillian/arquillian-algeron\",\"clone_url\":\"https://github.com/arquillian/arquillian-algeron.git\"}";
            String body = switch (path) {
                case "/orgs/arquillian/repos" -> "[" + repository + "]";
                case "/repos/arquillian/arquillian-algeron" -> repository;
                case "/repos/arquillian/arquillian-universe-bom" -> "{\"id\":8,\"name\":\"arquillian-universe-bom\",\"full_name\":\"arquillian/arquillian-universe-bom\",\"owner\":{\"login\":\"arquillian\"},\"html_url\":\"https://github.com/arquillian/arquillian-universe-bom\",\"clone_url\":\"https://github.com/arquillian/arquillian-universe-bom.git\"}";
                case "/repos/arquillian/arquillian-algeron/contributors", "/teams/1/members" -> "[" + profile + "]";
                case "/users/38leinad" -> profile;
                case "/repos/arquillian/arquillian-algeron/milestones" -> "[{\"id\":8,\"number\":1,\"title\":\"" + notesVersion + "\"}]";
                case "/repos/arquillian/arquillian-algeron/issues" -> "[{\"id\":9,\"number\":1,\"title\":\"Fixed issue\",\"html_url\":\"https://github.com/example/issues/1\",\"labels\":[]}]";
                case "/rest/api/latest/project/EX" -> "{\"versions\":[{\"id\":1,\"name\":\"" + notesVersion + "\",\"released\":true}]}";
                case "/rest/api/latest/project/EX/components" -> "[{\"self\":\"" + base + "component/1\"}]";
                case "/component/1" -> "{\"id\":1,\"description\":\"Component :: example\",\"lead\":{\"name\":\"trusted-jboss\",\"displayName\":\"Trusted\"}}";
                case "/secure/ReleaseNote.jspa" -> "<textarea id='editcopy'>&lt;h2&gt;Bug&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;Resolved Jira issue&lt;/li&gt;&lt;/ul&gt;</textarea>";
                default -> "Malformed retired service";
            };
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(failureStatus == 0 ? 200 : failureStatus, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        }

        /** Releases the local service and repository without touching the production workspace or shared caches. */
        @Override public void close() { server.stop(0); git.close(); }
    }

    /** Runs local integration assertions from isolated classes while another phase owns the shared Maven outputs. */
    public static void main(String[] arguments) throws Exception {
        RefreshCoordinatorTest test = new RefreshCoordinatorTest();
        test.directory = Files.createTempDirectory("roq-refresh-1-"); test.refreshesAllDomainsAndFetchedDefaultBranch();
        test.directory = Files.createTempDirectory("roq-refresh-2-"); test.preservesBytesOnLaterStageAndRetiredSourceFailure();
        test.directory = Files.createTempDirectory("roq-refresh-partial-"); test.rejectsPartialCurrentArtifactMappingAtomically();
        test.directory = Files.createTempDirectory("roq-refresh-session-"); test.classifiesSessionBeforeConferenceFallback();
        test.directory = Files.createTempDirectory("roq-refresh-3-"); test.rejectsAmbiguousIdentityWithoutWriting();
        test.directory = Files.createTempDirectory("roq-refresh-4-"); test.validatesCliConfigurationAndTokens();
        test.directory = Files.createTempDirectory("roq-refresh-5-"); test.previewUsesPublicationValidationWithoutWriting();
        test.directory = Files.createTempDirectory("roq-refresh-6-"); test.refreshesNativeGuideHistoryAndNewKnownComponentVersion();
        test.directory = Files.createTempDirectory("roq-refresh-7-"); test.refreshesUniverseFromFetchedBareRemote();
        System.out.println("RefreshCoordinatorTest: nine isolated checks passed");
    }
}
