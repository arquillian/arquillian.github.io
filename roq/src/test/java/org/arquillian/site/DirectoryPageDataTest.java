package org.arquillian.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.arquillian.site.refresh.SnapshotStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Protects directory membership and presentation while independently retaining every historical field. */
class DirectoryPageDataTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonNode catalog;

    /** Loads the single checked-in snapshot through the same validation boundary as explicit refreshes. */
    @BeforeAll
    static void loadCatalog() throws IOException {
        catalog = new SnapshotStore(Path.of("data/catalog.json")).read();
    }

    /** The reporter remains a real module page while the published directory omits its unsupported category. */
    @Test
    void retainsExactModuleDirectoryMembership() {
        List<JsonNode> listed = catalog.path("modules").valueStream()
                .filter(row -> row.path("directory").path("listed").asBoolean()).toList();
        assertEquals(121, listed.size());
        assertEquals(14, listed.stream().filter(row -> row.path("directory").path("obsolete").asBoolean()).count());
        assertEquals(121, listed.stream().map(row -> row.path("directory").path("order").asInt()).distinct().count());
        JsonNode core = row("modules", "/modules/core-platform/");
        assertEquals("Core", core.path("directory").path("label").asText());
        assertEquals("platform", core.path("directory").path("category").asText());
        assertEquals(1, core.path("directory").path("order").asInt(-1));
        JsonNode reporter = row("modules", "/modules/arquillian-reporter-test-reports/");
        assertEquals("test_reports", reporter.path("directory").path("category").asText());
        assertFalse(reporter.path("directory").path("listed").asBoolean(true));
        assertEquals(10, catalog.path("modules").valueStream().filter(module -> module.has("documentation")).count());
    }

    /** Historical card occurrences are retained without treating rank as a fabricated contribution count. */
    @Test
    void retainsContributorOccurrencesAndPublishedRank() {
        List<JsonNode> cards = catalog.path("identities").valueStream()
                .flatMap(row -> row.path("directory_cards").valueStream()).toList();
        assertEquals(357, cards.size());
        assertEquals(357, cards.stream().map(card -> card.path("order").asInt()).distinct().count());
        JsonNode first = row("identities", "/community/aslakknutsen/").path("directory_cards").get(0);
        assertEquals(0, first.path("order").asInt(-1));
        assertEquals("https://avatars.githubusercontent.com/u/132158?s=140", first.path("avatar_url").asText());
        assertEquals("https://avatars.githubusercontent.com/u/132158?s=60", first.path("modal_avatar_url").asText());
        JsonNode duplicate = row("identities", "/community/piotrnowicki/").path("directory_cards");
        assertEquals(List.of(138, 343), duplicate.valueStream().map(card -> card.path("order").asInt()).toList());
        assertEquals("Pedro Kowalski", duplicate.get(0).path("name_override").asText());
        assertFalse(duplicate.get(1).has("name_override"));
        assertEquals("piotrnowicki-343", duplicate.get(1).path("modal_id").asText());
        assertTrue(row("identities", "/community/flashboss/").path("directory_cards").isEmpty());
        assertTrue(row("identities", "/community/maneo/").path("directory_cards").isEmpty());
        assertFalse(cards.stream().anyMatch(card -> card.has("commits") || card.has("commit_count")));
    }

    /** Hashes cover all old fields and nested order rather than comparing only the rows selected by extraction. */
    @Test
    void preservesEveryOldFieldAndArrayOrder() throws IOException, NoSuchAlgorithmException {
        Map<String, String> expected = Map.of(
                "repositories", "3d2d824de31cb8d7e186d689bb14f2fa0cbdeb00445ed9d853ad42cab039b877",
                "releases", "08fece3dc318e801780366f7fa3bd0c5f66e763fe6afb076870f5aff0b3f3337",
                "identities", "d729b0c31adc2a5045ebcae7b1c51d5e14eab8c9c30ca93303c7e053c96f7003",
                "modules", "0ac0babd1d824278b92186bab4dd37d95c9f6a2dafd5ca3dc732590b331f10a9",
                "guides", "dc91ba55819a66b028f130a8bde71dfebb7f24b6a90e9410422d24aee1ad3561",
                "events", "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945");
        for (Map.Entry<String, String> domain : expected.entrySet()) {
            ArrayNode rows = catalog.path(domain.getKey()).deepCopy();
            for (JsonNode row : rows) {
                if (domain.getKey().equals("modules")) ((ObjectNode) row).remove(List.of("directory", "extensions"));
                if (domain.getKey().equals("identities")) ((ObjectNode) row).remove(List.of(
                        "directory_cards", "team_order", "roadie_order", "lanyrd_profile_url", "google_profile_url"));
            }
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(MAPPER.writeValueAsBytes(sorted(rows))));
            assertEquals(domain.getValue(), actual, domain.getKey());
        }
    }

    /** Finds literal published routes so expectations are independent of the ignored extraction tool. */
    private static JsonNode row(String domain, String link) {
        return catalog.path(domain).valueStream().filter(row -> row.path("link").asText().equals(link))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + link));
    }

    /** Canonicalizes object keys only, preserving all historical array order in the retention fingerprint. */
    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = MAPPER.createObjectNode();
            TreeSet<String> keys = new TreeSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> result.set(key, sorted(node.path(key))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            node.forEach(value -> result.add(sorted(value)));
            return result;
        }
        return node;
    }
}
