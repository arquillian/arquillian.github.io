package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks explicit, offline identity normalization without assigning ownership from names or Git hints. */
class IdentityNormalizerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IdentityNormalizer normalizer = new IdentityNormalizer();
    @TempDir Path directory;

    /** Keeps published identity and avatar choices while attaching exact upstream source provenance. */
    @Test
    void preservesPublishedIdentityAndSnapshotCompatibility() throws Exception {
        ArrayNode existing = rows("""
                [{"id":"published:identity:/community/alice/","source":"published","source_id":"/community/alice/",
                  "github_id":"alice","username":"alice","name":"Alice Curated","avatar_url":"/images/alice.png",
                  "link":"/community/alice/","bio_html":"historical"}]
                """);
        ArrayNode observations = rows("""
                [{"id":"github:user:7","source":"github","source_id":7,"github_id":"alice",
                  "name":"Alice Upstream","avatar_url":"https://avatars.example/alice","email":"Alice@example.org",
                  "source_payload":{"id":7,"login":"alice","name":"Alice Upstream"}}]
                """);
        JsonNode original = existing.deepCopy();
        ObjectNode normalized = normalizer.normalize(existing, observations, rows("[]"));
        JsonNode person = normalized.path("identities").get(0);
        assertEquals(1, normalized.path("identities").size());
        assertEquals("published:identity:/community/alice/", person.path("id").asText());
        assertEquals("published", person.path("source").asText());
        assertEquals("/community/alice/", person.path("source_id").asText());
        assertEquals("Alice Curated", person.path("name").asText());
        assertEquals("/images/alice.png", person.path("avatar_url").asText());
        assertEquals(observations.get(0), person.path("observations").get(0));
        assertEquals(original, existing);
        SnapshotStore store = new SnapshotStore(directory.resolve("catalog.json"));
        store.merge(mapper.createObjectNode().set("identities", existing));
        ObjectNode merged = store.merge(mapper.createObjectNode().set("identities", normalized.path("identities")));
        assertEquals(1, merged.path("identities").size());
        assertEquals(person, merged.path("identities").get(0));
        assertEquals(normalized, normalizer.normalize((ArrayNode) normalized.path("identities"), observations, rows("[]")));
    }

    /** Deduplicates repeated email observations while adding distinct emails' counts within each repository. */
    @Test
    void combinesManualAliasesEmailsAndRepositoryCounts() throws Exception {
        ArrayNode existing = rows("""
                [{"id":"alice","github_id":"alice","username":"alice","commits":100,
                  "repository_commits":{"retired":100}}]
                """);
        ArrayNode overrides = rows("""
                [{"id":"alice","name":"Manual Name","jboss_username":"a_jboss","aliases":["old-alice"],
                  "emails":["A@EXAMPLE.ORG","second@example.org"],"role":"Project Lead"}]
                """);
        ArrayNode observations = rows("""
                [{"id":"git:author:a@example.org","source":"git-email","source_id":"a@example.org",
                  "email":"a@example.org","name":"A Git Name","commits":3,"repository_commits":{"repo":3}},
                 {"id":"git:author:a@example.org","source":"git-email","source_id":"a@example.org",
                  "email":"a@example.org","name":"A Git Name","commits":3,"repository_commits":{"repo":3}},
                 {"id":"git:author:second@example.org","source":"git-email","source_id":"second@example.org",
                  "email":"second@example.org","commits":2,"repository_commits":{"repo":2}},
                 {"id":"guide:1","source":"attribution","source_id":"guide:1","kind":"guide-author",
                  "username":"old-alice","guide":"getting-started"}]
                """);
        ObjectNode result = normalizer.normalize(existing, observations, overrides);
        JsonNode person = result.path("identities").get(0);
        assertEquals("Manual Name", person.path("name").asText());
        assertEquals("Project Lead", person.path("role").asText());
        assertEquals(2, person.path("emails").size());
        assertEquals(5, person.path("repository_commits").path("repo").asInt());
        assertEquals(100, person.path("repository_commits").path("retired").asInt());
        assertEquals(105, person.path("commits").asInt());
        assertEquals("getting-started", person.path("guide_authorships").get(0).asText());
        assertTrue(result.path("unmatched").isEmpty());
        assertEquals(result, normalizer.normalize((ArrayNode) result.path("identities"), observations, overrides));
    }

    /** Retains explicit manual contributors even when GitHub never lists them. */
    @Test
    void retainsManualCreditsAndAppliesTeamLeadAndTranslationAttribution() throws Exception {
        ArrayNode overrides = rows("""
                [{"github_id":"ebadr","name":"Badr El Houari","commits":1,"jboss_username":"badr"}]
                """);
        ArrayNode observations = rows("""
                [{"id":"team:1","source":"attribution","source_id":"team:1","kind":"team",
                  "github_id":"ebadr","team":"core"},
                 {"id":"lead:1","source":"attribution","source_id":"lead:1","kind":"component-lead",
                  "jboss_username":"badr","component":"Drone"},
                 {"id":"guide:de","source":"attribution","source_id":"guide:de","kind":"guide-translator",
                  "username":"badr","guide":"getting-started-de","language":"de"}]
                """);
        ObjectNode result = normalizer.normalize(rows("[]"), observations, overrides);
        JsonNode person = result.path("identities").get(0);
        assertEquals("manual:identity:ebadr", person.path("id").asText());
        assertEquals(1, person.path("commits").asInt());
        assertTrue(person.path("core").asBoolean());
        assertTrue(person.path("translator").asBoolean());
        assertEquals("core", person.path("teams").get(0).asText());
        assertEquals("Drone", person.path("component_leads").get(0).asText());
        assertEquals("de", person.path("translations").get(0).path("language").asText());
        assertEquals(result, normalizer.normalize((ArrayNode) result.path("identities"), observations, overrides));
    }

    /** Does not turn names, spoofable GitHub noreply addresses or unmatched guide authors into profile ownership. */
    @Test
    void keepsNamesAndGitHintsDistinct() throws Exception {
        ArrayNode existing = rows("[{\"id\":\"alice\",\"github_id\":\"alice\",\"name\":\"Same Name\"}]");
        ArrayNode observations = rows("""
                [{"id":"github:user:9","source":"github","source_id":9,"github_id":"bob","name":"Same Name"},
                 {"id":"git:author:7+alice@users.noreply.github.com","source":"git-email",
                  "source_id":"7+alice@users.noreply.github.com","email":"7+alice@users.noreply.github.com",
                  "github_id_hint":"alice","github_numeric_id":"7","name":"Same Name",
                  "commits":2,"repository_commits":{"repo":2}},
                 {"id":"author:unknown","source":"attribution","source_id":"author:unknown",
                  "kind":"guide-author","username":"unmapped","guide":"a-guide"}]
                """);
        ObjectNode result = normalizer.normalize(existing, observations, rows("[]"));
        assertEquals(2, result.path("identities").size());
        assertEquals(2, result.path("unmatched").size());
        assertEquals(existing.get(0), result.path("identities").get(0));
    }

    /** Rejects account, alias, source-ID and email conflicts instead of merging unrelated people. */
    @Test
    void rejectsConflictingClaims() throws Exception {
        ArrayNode existing = rows("""
                [{"id":"a","github_id":"alice","email":"a@example.org"},
                 {"id":"b","github_id":"bob","email":"b@example.org"}]
                """);
        for (String override : new String[]{
                "[{\"id\":\"a\",\"github_id\":\"bob\"}]",
                "[{\"id\":\"a\",\"emails\":[\"b@example.org\"]}]",
                "[{\"id\":\"a\",\"aliases\":[\"bob\"]}]"}) {
            assertThrows(IOException.class, () -> normalizer.normalize(existing, rows("[]"), rows(override)));
        }
        ArrayNode claims = rows("""
                [{"id":"github:user:7","source":"github","source_id":7,"github_id":"alice"},
                 {"id":"github:user:8","source":"github","source_id":8,"github_id":"alice"}]
                """);
        assertThrows(IOException.class, () -> normalizer.normalize(existing, claims, rows("[]")));
        ArrayNode emailClaim = rows("""
                [{"id":"github:user:7","source":"github","source_id":7,"github_id":"bob","email":"a@example.org"}]
                """);
        assertThrows(IOException.class, () -> normalizer.normalize(existing, emailClaim, rows("[]")));
        ArrayNode protectedId = rows("[{\"id\":\"a\",\"source\":\"github\",\"source_id\":7}]");
        assertThrows(IOException.class, () -> normalizer.normalize(
                rows("[{\"id\":\"a\",\"source\":\"published\",\"source_id\":\"/community/a/\"}]"), rows("[]"), protectedId));
    }

    /** Fails invalid shapes, untrusted account fields and contradictory counts at the pure normalization boundary. */
    @Test
    void rejectsMalformedInputsWithoutMutatingThem() throws Exception {
        assertThrows(IOException.class, () -> normalizer.normalize(mapper.createObjectNode(), rows("[]"), rows("[]")));
        assertThrows(IOException.class, () -> normalizer.normalize(rows("[{\"id\":\"a\",\"emails\":\"bad\"}]"), rows("[]"), rows("[]")));
        for (String observation : new String[]{
                "[{\"id\":\"x\",\"source\":\"unknown\",\"source_id\":1}]",
                "[{\"id\":\"github:user:7\",\"source\":\"github\",\"source_id\":7,\"github_id\":\"a\",\"source_payload\":{\"id\":8}}]",
                "[{\"id\":\"git:author:a@b\",\"source\":\"git-email\",\"source_id\":\"a@b\",\"email\":\"a@b\",\"github_id\":\"a\"}]",
                "[{\"id\":\"git:author:a@b\",\"source\":\"git-email\",\"source_id\":\"a@b\",\"email\":\"a@b\",\"repository_commits\":{\"repo\":-1}}]"}) {
            ArrayNode input = rows(observation);
            JsonNode before = input.deepCopy();
            assertThrows(IOException.class, () -> normalizer.normalize(rows("[]"), input, rows("[]")));
            assertEquals(before, input);
        }
        ArrayNode contradictory = rows("""
                [{"id":"git:author:a@b","source":"git-email","source_id":"a@b","email":"a@b","repository_commits":{"repo":1}},
                 {"id":"git:author:a@b","source":"git-email","source_id":"a@b","email":"a@b","repository_commits":{"repo":2}}]
                """);
        assertThrows(IOException.class, () -> normalizer.normalize(rows("[{\"id\":\"a\",\"email\":\"a@b\"}]"), contradictory, rows("[]")));
    }

    /** Exercises every published canonical row without inventing unavailable original upstream IDs. */
    @Test
    void preservesCompletePublishedSnapshot() throws Exception {
        ArrayNode existing = (ArrayNode) mapper.readTree(Files.readString(Path.of("data/catalog.json"))).path("identities");
        ObjectNode normalized = normalizer.normalize(existing, rows("[]"), rows("[]"));
        assertEquals(existing.size(), normalized.path("identities").size());
        for (JsonNode row : existing) {
            JsonNode retained = null;
            for (JsonNode candidate : normalized.path("identities")) {
                if (candidate.path("id").equals(row.path("id"))) retained = candidate;
            }
            assertEquals(row, retained);
        }
    }

    /** Preserves manual and unpartitioned historical credit when only some repositories can be refreshed. */
    @Test
    void retainsHistoricalAndManualCreditWithLegacyContributorEmails() throws Exception {
        ArrayNode existing = rows("""
                [{"id":"alice","github_id":"alice","commits":100,
                  "contributor":{"name":"Original Author","emails":["a@b"]}}]
                """);
        ArrayNode observations = rows("""
                [{"id":"git:author:a@b","source":"git-email","source_id":"a@b","email":"a@b","repository_commits":{"repo":2}}]
                """);
        ArrayNode overrides = rows("[{\"id\":\"alice\",\"commits\":80}]");
        ObjectNode result = normalizer.normalize(existing, observations, overrides);
        JsonNode person = result.path("identities").get(0);
        assertTrue(result.path("unmatched").isEmpty());
        assertEquals(100, person.path("commits").asLong());
        assertEquals(2, person.path("repository_commits").path("repo").asLong());
        assertEquals(existing.get(0).path("contributor"), person.path("contributor"));
        ObjectNode repeated = normalizer.normalize(result.path("identities"), rows("[]"), overrides);
        assertEquals(100, repeated.path("identities").get(0).path("commits").asLong());
        ObjectNode historical = normalizer.normalize(rows("""
                [{"id":"alice","email":"a@b","commits":100,"repository_commits":{"repo":100}}]
                """), observations, rows("[]"));
        assertEquals(100, historical.path("identities").get(0).path("repository_commits").path("repo").asLong());
    }

    /** Retains credit missing from a partial repository breakdown while adding only the repository's observed increase. */
    @Test
    void preservesUnattributedHistoricalCreditWithPartialRepositoryCounts() throws Exception {
        ArrayNode existing = rows("""
                [{"id":"alice","email":"a@b","commits":100,"repository_commits":{"repo":2}}]
                """);
        ArrayNode observations = rows("""
                [{"id":"git:author:a@b","source":"git-email","source_id":"a@b","email":"a@b","repository_commits":{"repo":3}}]
                """);
        JsonNode before = existing.deepCopy();
        ObjectNode result = normalizer.normalize(existing, observations, rows("[]"));
        JsonNode person = result.path("identities").get(0);
        assertEquals(101, person.path("commits").asLong());
        assertEquals(98, person.path("historical_unattributed_commits").asLong());
        assertEquals(3, person.path("repository_commits").path("repo").asLong());
        assertEquals(before, existing);
        assertEquals(result, normalizer.normalize(result.path("identities"), observations, rows("[]")));
    }

    /** Refuses a generated manual ID that would replace an account-less historical profile and its curated metadata. */
    @Test
    void rejectsGeneratedManualIdCollisionWithoutMutatingInputs() throws Exception {
        ArrayNode existing = rows("""
                [{"id":"manual:identity:alice","source":"manual","source_id":"alice",
                  "name":"Historical Name","avatar_url":"/curated.png"}]
                """);
        ArrayNode overrides = rows("[{\"github_id\":\"alice\"}]");
        JsonNode before = existing.deepCopy();
        JsonNode originalOverrides = overrides.deepCopy();
        assertThrows(IOException.class, () -> normalizer.normalize(existing, rows("[]"), overrides));
        assertEquals(before, existing);
        assertEquals(originalOverrides, overrides);
    }

    /** Rejects fabricated selectors, malformed ledgers and overflowing count totals before returning any update. */
    @Test
    void validatesNestedCountsAndUnmatchedAttributions() throws Exception {
        ArrayNode existing = rows("[{\"id\":\"a\",\"email\":\"a@b\"}]");
        for (String input : new String[]{
                "[{\"id\":\"git:author:x@y\",\"source\":\"git-email\",\"source_id\":\"x@y\",\"email\":\"x@y\",\"identity_id\":\"a\",\"repository_commits\":{\"r\":1}}]",
                "[{\"id\":\"git:author:x@y\",\"source\":\"git-email\",\"source_id\":\"x@y\",\"email\":\"x@y\",\"emails\":[\"a@b\"],\"repository_commits\":{\"r\":1}}]",
                "[{\"id\":\"git:author:a@b\",\"source\":\"git-email\",\"source_id\":\"a@b\",\"email\":\"a@b\",\"repository_commits\":{\"r\":9223372036854775807,\"s\":1}}]",
                "[{\"id\":\"github:user:7\",\"source\":\"github\",\"source_id\":7,\"github_id\":\"other\",\"aliases\":[\"a\"]}]",
                "[{\"id\":\"x\",\"source\":\"attribution\",\"source_id\":\"x\",\"kind\":\"guide-translator\",\"username\":\"unknown\"}]"}) {
            assertThrows(IOException.class, () -> normalizer.normalize(existing, rows(input), rows("[]")));
        }
        ArrayNode observation = rows("[{\"id\":\"github:user:7\",\"source\":\"github\",\"source_id\":7,\"github_id\":\"bob\"}]");
        assertThrows(IOException.class, () -> normalizer.normalize(
                rows("[{\"id\":\"github:user:7\",\"github_id\":\"alice\"}]"), observation, rows("[]")));
        for (String invalid : new String[]{
                "[{\"id\":\"a\",\"git_author_counts\":\"bad\"}]",
                "[{\"id\":\"a\",\"repository_commits\":{\"r\":-1}}]",
                "[{\"id\":\"a\",\"commits\":\"bad\"}]"}) {
            assertThrows(IOException.class, () -> normalizer.normalize(rows(invalid), rows("[]"), rows("[]")));
        }
    }

    /** Accepts the repository's manual mappings, including mixed-case GitHub IDs and multiple emails for one person. */
    @Test
    void acceptsCurrentManualConfiguration() throws Exception {
        ObjectMapper yaml = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
        ArrayNode existing = (ArrayNode) mapper.readTree(Files.readString(Path.of("data/catalog.json"))).path("identities");
        ArrayNode overrides = (ArrayNode) yaml.readTree(Files.readString(Path.of("data/identities.yml")));
        overrides.addAll((ArrayNode) yaml.readTree(Files.readString(Path.of("data/github_mapping.yml"))));
        ObjectNode result = normalizer.normalize(existing, rows("[]"), overrides);
        for (JsonNode row : existing) {
            assertTrue(java.util.stream.StreamSupport.stream(result.path("identities").spliterator(), false)
                    .anyMatch(candidate -> candidate.path("id").equals(row.path("id"))));
        }
        JsonNode person = java.util.stream.StreamSupport.stream(result.path("identities").spliterator(), false)
                .filter(candidate -> candidate.path("github_id").asText().equals("piotrnowicki")).findFirst().orElseThrow();
        assertTrue(person.path("emails").toString().contains("info@piotrnowicki.com"));
    }

    /** Parses inline fixture arrays so tests describe the actual JSON boundary. */
    private ArrayNode rows(String json) throws IOException {
        return (ArrayNode) mapper.readTree(json);
    }
}
