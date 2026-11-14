package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks canonical preservation and actual rendered values using the published schema and deterministic local observations. */
class CatalogAssemblerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Confirms that omitted sources cannot remove or reformat any historical presentation snapshot. */
    @Test
    void preservesPublishedCatalogWithoutObservations() throws Exception {
        ObjectNode existing = published();
        assertEquals(existing, new CatalogAssembler().assemble(existing, JSON.objectNode(), JSON.objectNode()));
    }

    /** Exercises exact repository, Maven, tag, notes, identity and guide binding through consumed structured/fragments. */
    @Test
    void refreshesRenderableValuesAndRetainsPublicationIdentity() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode batch = batch(existing);
        ObjectNode mappings = mappings(existing);
        ObjectNode before = existing.deepCopy();
        ObjectNode beforeBatch = batch.deepCopy();
        ObjectNode beforeMappings = mappings.deepCopy();
        ObjectNode result = new CatalogAssembler().assemble(existing, batch, mappings);
        ObjectNode module = (ObjectNode) result.path("modules").get(0);
        ObjectNode release = (ObjectNode) result.path("releases").get(0);
        ObjectNode oldRelease = (ObjectNode) existing.path("releases").get(0);
        for (String field : List.of("id", "source", "source_id", "link", "comment_id", "comment_url", "disqus_url", "published_date", "published_order", "title", "author")) assertEquals(oldRelease.path(field), release.path(field), field);
        assertEquals("2026-11-01", release.path("date").asText());
        assertTrue(release.path("details").toString().contains("2026-11-01"));
        assertTrue(release.path("resolved_issues").isArray());
        assertTrue(release.path("resolved_issues").toString().contains("Safe issue"));
        assertFalse(release.path("resolved_issues").toString().contains("javascript:"));
        assertFalse(release.path("issues_comment").asText().contains("<script>"));
        assertTrue(module.path("artifact_blocks").toString().contains("9.0.0"));
        assertTrue(module.path("activity").path("message").asText().contains("latest"));
        assertEquals(2, module.path("release_history").size());
        assertEquals(1, result.path("releases").size(), "An additional Git tag does not become an announcement");
        assertEquals(existing.path("identities").get(0).path("avatar_url"), result.path("identities").get(0).path("avatar_url"));
        assertTrue(result.path("identities").get(0).path("bio_html").asText().contains("&lt;script&gt;"));
        assertTrue(result.path("guides").get(0).path("footer_html").asText().contains("Changed guide"));
        assertTrue(result.path("guides").get(0).path("authors_html").asText().contains("Nov 1, 2026"));
        assertTrue(result.path("guides").get(0).path("authors_html").asText().contains("Vineet Reynolds"), "Historical author credit survives later native authors");
        assertEquals(1, result.path("events").size(), "Historical events survive omitted sources");
        assertEquals(before, existing);
        assertEquals(beforeBatch, batch);
        assertEquals(beforeMappings, mappings);
        assertEquals(result, new CatalogAssembler().assemble(result, batch, mappings), "Repeated assembly is deterministic");
    }

    /** Requires reviewed complete seeds for new classifications, routes and publication identities. */
    @Test
    void addsExplicitlyMappedComponentAndAnnouncement() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode mappings = mappings(existing);
        ObjectNode module = existing.path("modules").get(0).deepCopy();
        module.put("id", "manual:module:new").put("source", "manual").put("source_id", "new")
                .put("link", "/modules/new-extension/").put("title", "New Extension").put("name", "New");
        module.withObject("directory").put("order", 75).put("label", "New Extension");
        module.putArray("release_history"); module.putArray("artifact_blocks");
        ObjectNode component = ((ArrayNode) mappings.path("components")).addObject().put("id", "manual:module:new")
                .put("repository_id", existing.path("repositories").get(0).path("id").asText());
        component.putArray("artifacts").addObject().put("groupId", "org.example").put("artifactId", "new-api");
        component.set("create", module);
        ObjectNode release = existing.path("releases").get(0).deepCopy();
        release.put("id", "manual:release:new").put("source", "manual").put("source_id", 1234)
                .put("link", "/blog/2026/11/13/new-extension-9-0-0/").put("module_link", "/modules/new-extension/").put("version", "9.0.0").put("issues_count", 0);
        mappings.putArray("announcements").addObject().put("id", "manual:release:new").put("module_id", "manual:module:new")
                .put("version", "9.0.0").set("create", release);
        ObjectNode batch = batch(existing);
        ObjectNode tag = ((ObjectNode) batch.path("tag_releases").get(1)).deepCopy();
        ((ObjectNode) tag.path("artifacts").get(0)).put("artifactId", "new-api");
        ((ArrayNode) batch.path("artifact_modules")).add(tag.path("artifacts").get(0).deepCopy());
        ((ArrayNode) batch.path("tag_releases")).add(tag);
        ObjectNode result = new CatalogAssembler().assemble(existing, batch, mappings);
        assertEquals(2, result.path("modules").size());
        assertEquals(2, result.path("releases").size());
        assertTrue(result.path("modules").get(1).path("artifact_blocks").toString().contains("new-api"));
        assertTrue(result.path("releases").get(1).path("details").toString().contains("2026-11-01"));
        ObjectNode invalid = mappings.deepCopy();
        ((ObjectNode) invalid.path("components").get(1).path("create")).put("type", "Guess from title");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, batch, invalid));
        ((ObjectNode) invalid.path("components").get(1).path("create")).remove("title");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, batch, invalid));
    }

    /** Rejects ambiguous ownership, unknown repositories, numeric source rebinding and unsafe routes before publication. */
    @Test
    void rejectsAmbiguousAndIncompleteBindings() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode batch = batch(existing);
        ObjectNode mappings = mappings(existing);
        ObjectNode conflicting = existing.path("identities").get(0).deepCopy();
        conflicting.put("id", "manual:other").put("github_id", "other").put("email", "other@example.org").put("source_id", "other").put("link", "/community/other/");
        ((ArrayNode) existing.path("identities")).add(conflicting);
        ((ObjectNode) batch.path("identity_observations").get(0)).put("email", "other@example.org");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, batch, mappings));
        ObjectNode unknown = batch(fixture());
        ((ObjectNode) unknown.path("repositories").get(0)).put("full_name", "unknown/repository");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(fixture(), unknown, JSON.objectNode()));
        ObjectNode partial = JSON.objectNode();
        partial.putArray("source_completeness").addObject().put("id", "github").put("complete", false).put("count", 0);
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(fixture(), partial, JSON.objectNode()));
        assertThrows(IOException.class, () -> CatalogFragments.anchor("javascript:alert(1)", "unsafe"));
        assertThrows(IOException.class, () -> CatalogFragments.anchor("relative-without-site-root", "unsafe"));
    }

    /** Keeps optional enrichment candidates inert unless their entire canonical-bound envelope was explicitly reviewed. */
    @Test
    void requiresExactReviewedEnrichment() throws Exception {
        ObjectNode existing = fixture();
        ((ObjectNode) existing.path("identities").get(0)).put("bio_html", "").put("gravatar_id", "abc");
        ObjectNode candidate = JSON.objectNode().put("identity_id", existing.path("identities").get(0).path("id").asText()).put("source", "gravatar").put("source_id", "one");
        candidate.putObject("source_payload").put("id", "one").put("hash", "abc");
        candidate.putObject("patch").put("bio", "Reviewed biography");
        ObjectNode batch = JSON.objectNode(); batch.putArray("enrichment_candidates").add(candidate);
        ObjectNode result = new CatalogAssembler().assemble(existing, batch, JSON.objectNode());
        assertEquals(existing.path("identities"), result.path("identities"));
        ObjectNode mappings = JSON.objectNode(); mappings.putArray("reviewed_enrichments").add(candidate);
        result = new CatalogAssembler().assemble(existing, batch, mappings);
        assertTrue(result.path("identities").get(0).path("bio_html").asText().contains("Reviewed biography"));
        candidate.withObject("patch").put("github_id", "stolen");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, batch, mappings));
    }

    /** Keeps reviewed manual profile values authoritative and rejects hidden numeric or artifact-path ambiguity. */
    @Test
    void preservesManualAuthorityAndRejectsHiddenCollisions() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode batch = batch(existing);
        ObjectNode mappings = mappings(existing);
        mappings.putArray("identity_overrides").addObject().put("id", existing.path("identities").get(0).path("id").asText()).put("company", "Reviewed company");
        ObjectNode result = new CatalogAssembler().assemble(existing, batch, mappings);
        assertEquals("Reviewed company", result.path("identities").get(0).path("company").asText());
        ObjectNode collision = existing.deepCopy();
        ObjectNode other = collision.path("repositories").get(0).deepCopy();
        other.put("id", "manual:repository:other").put("source", "manual").put("source_id", "other").put("full_name", "other/repository").put("github_repository_id", 7);
        ((ArrayNode) collision.path("repositories")).add(other);
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(collision, batch, mappings));
        ObjectNode ambiguous = batch.deepCopy();
        ObjectNode duplicate = ambiguous.path("artifact_modules").get(0).deepCopy(); duplicate.put("relative_path", "another/");
        ((ArrayNode) ambiguous.path("artifact_modules")).add(duplicate);
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, ambiguous, mappings));
        ObjectNode missing = batch.deepCopy(); ((ObjectNode) missing.path("artifact_modules").get(0)).put("artifactId", "renamed-upstream");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, missing, mappings), "Missing current artifacts need explicit component reconciliation");
    }

    /** Rejects a refreshed component when one of two explicitly required current coordinates is absent. */
    @Test
    void rejectsPartiallyMissingCurrentArtifacts() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode observations = batch(existing);
        ObjectNode mappings = mappings(existing);
        ((ArrayNode) mappings.path("components").get(0).path("artifacts")).addObject()
                .put("groupId", "org.example").put("artifactId", "missing-api");
        IOException failure = assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, observations, mappings));
        assertTrue(failure.getMessage().contains("org.example:missing-api"));
    }

    /** Keeps manual and curated presentation authoritative over a reviewed optional candidate from the same refresh. */
    @Test
    void reviewedEnrichmentFillsOnlyUnclaimedPresentation() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode person = (ObjectNode) existing.path("identities").get(0);
        person.remove("location"); person.put("bio_html", "").put("gravatar_id", "abc");
        String id = person.path("id").asText();
        ObjectNode candidate = JSON.objectNode().put("identity_id", id).put("source", "gravatar").put("source_id", "one");
        candidate.putObject("source_payload").put("id", "one").put("hash", "abc");
        candidate.putObject("patch").put("location", "Gravatar location").put("bio", "Gravatar biography")
                .put("blog", "https://gravatar.example/blog").put("company", "Gravatar company");
        ObjectNode observations = JSON.objectNode(); observations.putArray("enrichment_candidates").add(candidate);
        ObjectNode mappings = JSON.objectNode(); mappings.putArray("reviewed_enrichments").add(candidate.deepCopy());
        mappings.putArray("identity_overrides").addObject().put("id", id).put("location", "Manual location")
                .put("bio", "Manual biography").put("blog", "https://manual.example/blog")
                .put("company", "Manual company");
        JsonNode refreshed = new CatalogAssembler().assemble(existing, observations, mappings).path("identities").get(0);
        assertEquals("Manual location", refreshed.path("location").asText());
        assertEquals("Manual company", refreshed.path("company").asText());
        assertEquals("Manual biography", refreshed.path("bio").asText());
        assertTrue(refreshed.path("bio_html").asText().contains("Manual biography"));
        assertTrue(refreshed.path("profiles").toString().contains("https://manual.example/blog"));
        assertFalse(refreshed.path("profiles").toString().contains("gravatar.example"));
    }

    /** Applies a real Community envelope only for the already trusted username, preserving ownership and provenance. */
    @Test
    void reviewsActualCommunityEnvelopeWithoutChangingOwnership() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode person = (ObjectNode) existing.path("identities").get(0);
        person.put("jboss_username", "trusted-existing");
        ObjectNode candidate = new IdentityCollector(new DataHttpClient(java.net.URI.create("https://community.jboss.org/"), null)).community(person);
        ObjectNode observations = JSON.objectNode(); observations.putArray("enrichment_candidates").add(candidate);
        ObjectNode mappings = JSON.objectNode(); mappings.putArray("reviewed_enrichments").add(candidate.deepCopy());
        JsonNode refreshed = new CatalogAssembler().assemble(existing, observations, mappings).path("identities").get(0);
        assertEquals("trusted-existing", refreshed.path("jboss_username").asText());
        assertEquals("https://community.jboss.org/people/trusted-existing", refreshed.path("jbosscommunity").path("profile_url").asText());
        assertTrue(refreshed.path("observations").toString().contains("jbosscommunity"));
        ObjectNode mismatched = candidate.deepCopy();
        mismatched.put("source_id", "another-person");
        mismatched.withObject("patch").put("jboss_username", "another-person");
        mismatched.withObject("patch").withObject("jbosscommunity").put("username", "another-person");
        observations.withArray("enrichment_candidates").removeAll().add(mismatched);
        mappings.withArray("reviewed_enrichments").removeAll().add(mismatched.deepCopy());
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, observations, mappings));
    }

    /** Accepts real optional collector envelopes without granting their suggested account or email changes. */
    @Test
    void reviewsActualGravatarAndConfluenceEnvelopesWithoutTakingAccounts() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = switch (exchange.getRequestURI().getPath()) {
                case "/abc.json" -> """
                        {"entry":[{"id":"17","hash":"abc","profileUrl":"https://gravatar.com/trusted",
                        "displayName":"Suggested name","currentLocation":"Suggested place","email":"other@example.org",
                        "accounts":[{"shortname":"twitter","username":"other"}],
                        "urls":[{"title":"Personal blog","value":"https://suggested.example/blog"}]}]}
                        """;
                case "/display/~trusted" -> "<div id='user-profile'><span id='email'>Other at Example dot ORG</span></div>";
                default -> "Not found";
            };
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            ObjectNode existing = fixture();
            ObjectNode person = (ObjectNode) existing.path("identities").get(0);
            String name = person.path("name").asText();
            String email = person.path("email").asText();
            String blog = person.path("profiles").toString();
            person.remove("location"); person.put("gravatar_id", "abc");
            person.putObject("confluence").put("username", "trusted");
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            IdentityCollector collector = new IdentityCollector(new DataHttpClient(base, null));
            ObjectNode gravatar = collector.gravatar(person, Map.of());
            ObjectNode confluence = collector.confluenceProfile(person.path("id").asText(), "trusted", base.resolve("display/~trusted"));
            ObjectNode observations = JSON.objectNode(); observations.putArray("enrichment_candidates").add(gravatar).add(confluence);
            ObjectNode mappings = JSON.objectNode(); mappings.putArray("reviewed_enrichments").add(gravatar.deepCopy()).add(confluence.deepCopy());
            JsonNode refreshed = new CatalogAssembler().assemble(existing, observations, mappings).path("identities").get(0);
            assertEquals("Suggested place", refreshed.path("location").asText());
            assertEquals(name, refreshed.path("name").asText());
            assertEquals(email, refreshed.path("email").asText());
            assertEquals(blog, refreshed.path("profiles").toString());
            assertFalse(refreshed.has("twitter"));
            assertEquals("trusted", refreshed.path("confluence").path("username").asText());
            assertEquals(base.resolve("display/~trusted").toString(), refreshed.path("confluence").path("profile_url").asText());
            assertEquals(2, refreshed.path("observations").size());
            ObjectNode wrong = confluence.deepCopy(); wrong.put("source_id", "other");
            observations.withArray("enrichment_candidates").removeAll().add(wrong);
            mappings.withArray("reviewed_enrichments").removeAll().add(wrong.deepCopy());
            assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, observations, mappings));
        } finally {
            server.stop(0);
        }
    }

    /** Retains directory cards and future curated page fields when unrelated upstream facts refresh. */
    @Test
    void retainsDirectoryAndCuratedPageFields() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode module = (ObjectNode) existing.path("modules").get(0);
        module.putArray("extensions").addObject().put("name", "Reviewed extension")
                .put("groupId", "org.example").put("artifactId", "reviewed-extension");
        ObjectNode person = (ObjectNode) existing.path("identities").get(0);
        person.put("team_order", 3).put("roadie_order", 5)
                .put("lanyrd_profile_url", "https://lanyrd.com/profile/reviewed/")
                .put("google_profile_url", "https://plus.google.com/reviewed");
        ObjectNode result = new CatalogAssembler().assemble(existing, batch(existing), mappings(existing));
        JsonNode refreshedModule = result.path("modules").get(0);
        JsonNode refreshedPerson = result.path("identities").get(0);
        for (String field : List.of("directory", "extensions")) assertEquals(module.path(field), refreshedModule.path(field), field);
        for (String field : List.of("directory_cards", "team_order", "roadie_order", "lanyrd_profile_url", "google_profile_url"))
            assertEquals(person.path(field), refreshedPerson.path(field), field);
    }

    /** Rejects new pages that would make the native directories fail to render or reuse a listed rank. */
    @Test
    void requiresRenderableDirectorySeeds() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode module = existing.path("modules").get(0).deepCopy();
        module.put("id", "manual:module:second").put("source", "manual").put("source_id", "second")
                .put("link", "/modules/second-extension/");
        ObjectNode mappings = JSON.objectNode();
        mappings.putArray("components").addObject().put("id", "manual:module:second").set("create", module);
        module.remove("directory");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, JSON.objectNode(), mappings),
                "Every new module needs an explicit directory classification");
        module.set("directory", existing.path("modules").get(0).path("directory").deepCopy());
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, JSON.objectNode(), mappings),
                "Two listed modules cannot share a public directory rank");
        module.withObject("directory").put("listed", false);
        assertDoesNotThrow(() -> new CatalogAssembler().assemble(existing, JSON.objectNode(), mappings),
                "A new module can be explicitly unlisted");

        ObjectNode person = existing.path("identities").get(0).deepCopy();
        person.put("id", "manual:identity:second").put("source", "manual").put("source_id", "second")
                .put("link", "/community/second/").put("github_id", "second").put("email", "second@example.org");
        person.remove("directory_cards");
        ObjectNode profiles = JSON.objectNode();
        profiles.putArray("identity_profiles").addObject().put("id", "manual:identity:second").set("create", person);
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, JSON.objectNode(), profiles),
                "A new profile must explicitly choose zero or more directory cards");
        person.putArray("directory_cards");
        assertDoesNotThrow(() -> new CatalogAssembler().assemble(existing, JSON.objectNode(), profiles),
                "An explicitly unlisted profile has an empty card array");
    }

    /** Derives a new known-component announcement from one explicit legacy policy without per-version row seeds. */
    @Test
    void derivesCompleteNewVersionFromComponentPolicy() throws Exception {
        ObjectNode existing = fixture();
        ObjectNode batch = batch(existing);
        ObjectNode mappings = mappings(existing);
        ObjectNode policy = ((ObjectNode) mappings.path("components").get(0)).putObject("announcement_policy");
        ObjectNode tag = (ObjectNode) batch.path("tag_releases").get(1);
        tag.put("id", "git:tag:7:9.0.0.Final").put("source_id", "9.0.0.Final").put("tag", "9.0.0.Final").put("version", "9.0.0.Final").put("source_url", "https://github.com/example/example/tree/9.0.0.Final");
        ((ObjectNode) tag.path("artifacts").get(0)).put("version", "9.0.0.Final");
        policy.put("slug_prefix", "arquillian-algeron").put("since", "2026-11-01").put("author", "tag-committer")
                .put("publication_date", "release-date").put("comment_origin", "http://arquillian.org").put("comment_id", "canonical-url");
        policy.putArray("owners").add("arquillian"); policy.putArray("tags").add("release").add("extension").add("algeron-extension").add("extension");
        ObjectNode result = new CatalogAssembler().assemble(existing, batch, mappings);
        assertEquals(2, result.path("releases").size());
        JsonNode created = result.path("releases").get(1);
        assertEquals("9.0.0.Final", created.path("version").asText());
        assertEquals("/blog/2026/11/01/arquillian-algeron-9-0-0-Final/", created.path("link").asText());
        assertEquals(existing.path("identities").get(0).path("name"), created.path("author"));
        assertEquals("2026-11-01", created.path("published_date").asText());
        for (String field : List.of("details", "artifacts", "resolved_issues", "contributors", "tags")) assertTrue(created.path(field).isArray(), field);
        assertTrue(created.path("details").size() >= 4);
        assertTrue(created.path("artifacts").toString().contains("9.0.0"));
        assertEquals(List.of("release", "extension", "algeron-extension", "extension", "jbosscentral"), created.path("tags").valueStream().map(value -> value.path("title").asText()).toList());
        assertEquals(result, new CatalogAssembler().assemble(result, batch, mappings));
        ObjectNode invalid = mappings.deepCopy();
        ((ObjectNode) invalid.path("components").get(0).path("announcement_policy")).remove("author");
        assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, batch, invalid));
    }

    /** Keeps the rendered module hero current while retaining its license and unchanged captured metadata bytes. */
    @Test
    void refreshesLatestModuleMetadataWithoutReformattingUnchangedValues() throws Exception {
        ObjectNode existing = fixture(); ObjectNode batch = batch(existing); ObjectNode mappings = mappings(existing);
        String original = existing.path("modules").get(0).path("metadata_html").asText();
        ObjectNode refreshed = new CatalogAssembler().assemble(existing, batch, mappings);
        String current = refreshed.path("modules").get(0).path("metadata_html").asText();
        assertTrue(current.contains("9.0.0"), "The module hero must show the newest accepted tag version");
        assertTrue(current.contains("2026-11-01"), "The module hero must show its actual release date");
        assertEquals(org.jsoup.Jsoup.parseBodyFragment(original).selectFirst("a[target=license]").outerHtml(), org.jsoup.Jsoup.parseBodyFragment(current).selectFirst("a[target=license]").outerHtml());
        String unchanged = original.replace("1.0.1", "9.0.0").replace("2017-06-15", "2026-11-01");
        ((ObjectNode) existing.path("modules").get(0)).put("metadata_html", unchanged);
        assertEquals(unchanged, new CatalogAssembler().assemble(existing, batch, mappings).path("modules").get(0).path("metadata_html").asText());
        ((ObjectNode) existing.path("modules").get(0)).put("metadata_html", "");
        String generated = new CatalogAssembler().assemble(existing, batch, mappings).path("modules").get(0).path("metadata_html").asText();
        assertTrue(generated.contains("9.0.0") && generated.contains("2026-11-01"), "A complete new component with empty metadata still receives current hero fields");
    }

    /** Refreshes generator inputs from exact current Universe models and rejects incomplete or ambiguous coordinates. */
    @Test
    void refreshesUniverseExtensionsFromExactCurrentModels() throws Exception {
        ObjectNode existing = universeFixture();
        ObjectNode module = (ObjectNode) existing.path("modules").get(0);
        module.withObject("directory").put("future_field", "preserved");
        module.putArray("extensions").addObject().put("name", "Previous label").put("groupId", "org.arquillian.universe").put("artifactId", "arquillian-drone");
        ObjectNode repository = existing.path("repositories").get(0).deepCopy();
        String repositoryId = repository.path("id").asText();
        repository.put("id", "github:repository:8").put("source", "github").put("source_id", 8);
        ObjectNode batch = JSON.objectNode(); batch.putArray("repositories").add(repository);
        ArrayNode models = batch.putArray("artifact_modules");
        ObjectNode bom = models.addObject().put("repository", "github:repository:8").put("relative_path", "arquillian-universe/")
                .put("source_path", "arquillian-universe/pom.xml").put("groupId", "org.arquillian").put("artifactId", "arquillian-universe").put("version", "2.0").put("packaging", "pom");
        ArrayNode management = bom.putArray("dependency_management");
        for (String artifact : List.of("arquillian-drone", "arquillian-graphene")) {
            management.addObject().put("groupId", "org.arquillian.universe").put("artifactId", artifact).put("version", "2.0");
            models.addObject().put("repository", "github:repository:8").put("relative_path", artifact + "/").put("source_path", artifact + "/pom.xml")
                    .put("groupId", "org.arquillian.universe").put("artifactId", artifact).put("name", artifact.equals("arquillian-drone") ? "Drone & Friends" : "Graphene")
                    .put("version", "2.0").put("packaging", "pom");
        }
        ObjectNode before = batch.deepCopy();
        ObjectNode refreshed = new CatalogAssembler().assemble(existing, batch, JSON.objectNode());
        JsonNode extensions = refreshed.path("modules").get(0).path("extensions");
        assertEquals(MAPPER.readTree("[{\"name\":\"Drone & Friends\",\"groupId\":\"org.arquillian.universe\",\"artifactId\":\"arquillian-drone\"},{\"name\":\"Graphene\",\"groupId\":\"org.arquillian.universe\",\"artifactId\":\"arquillian-graphene\"}]"), extensions);
        assertEquals(module.path("directory"), refreshed.path("modules").get(0).path("directory"));
        assertEquals(repositoryId, refreshed.path("modules").get(0).path("repository_id").asText());
        assertEquals(before, batch);
        assertEquals(refreshed, new CatalogAssembler().assemble(refreshed, batch, JSON.objectNode()));
        assertEquals(existing, new CatalogAssembler().assemble(existing, JSON.objectNode(), JSON.objectNode()), "An omitted Universe source preserves existing generator inputs");
        for (String broken : List.of("missing-bom", "missing-model", "duplicate-path", "wrong-coordinate", "missing-name", "unresolved-coordinate", "duplicate-dependency", "empty-management")) {
            ObjectNode invalid = batch.deepCopy(); ArrayNode artifacts = (ArrayNode) invalid.path("artifact_modules");
            switch (broken) {
                case "missing-bom" -> artifacts.remove(0);
                case "missing-model" -> artifacts.remove(2);
                case "duplicate-path" -> artifacts.add(artifacts.get(1).deepCopy());
                case "wrong-coordinate" -> ((ObjectNode) artifacts.get(1)).put("groupId", "other.group");
                case "missing-name" -> ((ObjectNode) artifacts.get(1)).remove("name");
                case "unresolved-coordinate" -> ((ObjectNode) artifacts.get(0).path("dependency_management").get(0)).put("groupId", "${unresolved}");
                case "duplicate-dependency" -> ((ArrayNode) artifacts.get(0).path("dependency_management")).add(artifacts.get(0).path("dependency_management").get(0).deepCopy());
                case "empty-management" -> ((ObjectNode) artifacts.get(0)).putArray("dependency_management");
                default -> throw new AssertionError(broken);
            }
            assertThrows(IOException.class, () -> new CatalogAssembler().assemble(existing, invalid, JSON.objectNode()), broken);
        }
    }

    /** Selects the real canonical Universe module/repository without creating another editable generator model. */
    static ObjectNode universeFixture() throws IOException {
        ObjectNode catalog = fixture(); ObjectNode source = published();
        catalog.putArray("repositories"); catalog.putArray("modules"); catalog.putArray("releases");
        for (JsonNode row : source.path("modules")) if (row.path("link").asText().equals("/modules/arquillian-universe-bom-bom/")) {
            ((ArrayNode) catalog.path("modules")).add(row.deepCopy());
            for (JsonNode repository : source.path("repositories")) if (repository.path("id").equals(row.path("repository_id"))) ((ArrayNode) catalog.path("repositories")).add(repository.deepCopy());
        }
        return catalog;
    }

    /** Loads the actual authoritative catalog without changing it. */
    private static ObjectNode published() throws IOException { return (ObjectNode) MAPPER.readTree(Files.readString(Path.of("data/catalog.json"))); }

    /** Chooses realistic published component/profile/guide rows while limiting fixture size. */
    static ObjectNode fixture() throws IOException {
        ObjectNode published = published();
        ObjectNode catalog = JSON.objectNode();
        for (String domain : List.of("repositories", "releases", "identities", "modules", "guides", "events")) catalog.putArray(domain);
        ObjectNode module = published.path("modules").get(0).deepCopy();
        String repositoryId = module.path("repository_id").asText();
        for (JsonNode repository : published.path("repositories")) if (repository.path("id").asText().equals(repositoryId)) ((ArrayNode) catalog.path("repositories")).add(repository.deepCopy());
        ((ArrayNode) catalog.path("modules")).add(module);
        module.putArray("release_history");
        for (JsonNode release : published.path("releases")) if (release.path("module_link").equals(module.path("link"))) {
            ObjectNode row = release.deepCopy(); row.put("version", "1.0.1");
            ((ArrayNode) catalog.path("releases")).add(row); break;
        }
        ObjectNode person = published.path("identities").get(0).deepCopy();
        person.put("email", "trusted@example.org");
        ((ArrayNode) catalog.path("identities")).add(person);
        ((ArrayNode) catalog.path("guides")).add(published.path("guides").get(0).deepCopy());
        catalog.withArray("events").addObject().put("id", "historical:event").put("source", "manual").put("source_id", "past").put("title", "Past conference").put("start_date", "2016-01-01");
        return catalog;
    }

    /** Supplies explicit component selection and guide-path mappings without duplicating historical page data. */
    static ObjectNode mappings(ObjectNode existing) {
        ObjectNode mappings = JSON.objectNode();
        ObjectNode component = mappings.putArray("components").addObject().put("id", existing.path("modules").get(0).path("id").asText());
        component.putArray("artifacts").addObject().put("groupId", "org.example").put("artifactId", "example-api");
        mappings.putArray("guide_bindings").addObject().put("source_path", "guides/example.textile").put("id", existing.path("guides").get(0).path("id").asText());
        return mappings;
    }

    /** Creates a realistic isolated observation batch that changes every major rendered view. */
    static ObjectNode batch(ObjectNode existing) {
        ObjectNode batch = JSON.objectNode();
        ObjectNode repository = existing.path("repositories").get(0).deepCopy();
        repository.put("id", "github:repository:7").put("source", "github").put("source_id", 7).put("clone_url", "https://github.com/example/example.git");
        repository.putObject("last_commit").put("commit", "abcd").put("message", "latest repository work").put("author", "Trusted").put("date", "2026-11-01T00:00:00Z");
        repository.put("unreleased_commits", 3);
        batch.putArray("repositories").add(repository);
        ObjectNode model = JSON.objectNode().put("repository", "github:repository:7").put("relative_path", "").put("groupId", "org.example").put("artifactId", "example-api").put("packaging", "jar").put("version", "9.0.0");
        model.putArray("dependencies");
        batch.putArray("artifact_modules").add(model);
        ArrayNode tags = batch.putArray("tag_releases");
        for (String version : List.of("1.0.1", "9.0.0")) {
            ObjectNode tag = tags.addObject().put("id", "git:tag:7:" + version).put("source", "git:7").put("source_id", version).put("repository", "github:repository:7").put("tag", version).put("version", version).put("date", "2026-11-01T00:00:00Z").put("source_url", "https://github.com/example/example/tree/" + version);
            tag.putObject("released_by").put("name", "Untrusted name").put("email", "trusted@example.org");
            ObjectNode artifact = model.deepCopy(); artifact.put("version", version);
            tag.putArray("artifacts").add(artifact); tag.putArray("contributors");
        }
        ObjectNode notes = batch.putArray("release_notes").addObject().put("module_id", existing.path("modules").get(0).path("id").asText()).putObject("observation");
        notes.put("version", "1.0.1").put("source", "jira").put("source_id", "9").put("comment", "<script>unsafe</script>");
        notes.putObject("resolved_issues").putArray("Bug").add("<a href='javascript:alert(1)'>Safe issue</a><script>unsafe</script>");
        ObjectNode person = existing.path("identities").get(0).deepCopy();
        ObjectNode profile = batch.putArray("identity_observations").addObject().put("id", "github:user:413413").put("source", "github").put("source_id", 413413).put("github_id", person.path("github_id").asText()).put("email", "trusted@example.org").put("bio", "Changed <script>bio</script>").put("company", "Current company");
        profile.putObject("source_payload").put("id", 413413).put("login", person.path("github_id").asText());
        batch.withArray("identity_observations").addObject().put("id", "attribution:guide:example").put("source", "attribution").put("source_id", "guide:example")
                .put("kind", "guide-author").put("github_id", person.path("github_id").asText()).put("guide", existing.path("guides").get(0).path("id").asText());
        ObjectNode guide = batch.putArray("guide_history").addObject().put("source_path", "guides/example.textile").put("revision", "abcd");
        guide.putArray("changes").addObject().put("commit", "guide-commit-1").put("date", "2026-11-01T00:00:00Z").put("author", "Trusted").put("message", "Changed guide <script>text</script>");
        return batch;
    }

    /** Runs the same focused assertions in isolated draft classes without touching the shared Maven output. */
    public static void main(String[] arguments) throws Exception {
        CatalogAssemblerTest test = new CatalogAssemblerTest();
        test.preservesPublishedCatalogWithoutObservations();
        test.refreshesRenderableValuesAndRetainsPublicationIdentity();
        test.addsExplicitlyMappedComponentAndAnnouncement();
        test.rejectsAmbiguousAndIncompleteBindings();
        test.requiresExactReviewedEnrichment();
        test.preservesManualAuthorityAndRejectsHiddenCollisions();
        test.rejectsPartiallyMissingCurrentArtifacts();
        test.reviewedEnrichmentFillsOnlyUnclaimedPresentation();
        test.reviewsActualCommunityEnvelopeWithoutChangingOwnership();
        test.reviewsActualGravatarAndConfluenceEnvelopesWithoutTakingAccounts();
        test.retainsDirectoryAndCuratedPageFields();
        test.requiresRenderableDirectorySeeds();
        test.derivesCompleteNewVersionFromComponentPolicy();
        test.refreshesUniverseExtensionsFromExactCurrentModels();
        test.refreshesLatestModuleMetadataWithoutReformattingUnchangedValues();
        System.out.println("CatalogAssemblerTest: fifteen isolated checks passed");
    }
}
