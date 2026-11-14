package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Resolves explicit refresh observations against canonical people without trusting display names or Git account hints. */
public final class IdentityNormalizer {
    private static final List<String> ACCOUNTS = List.of("github_id", "jboss_username", "username");
    private static final List<String> PROTECTED = List.of("id", "source", "source_id", "github_id", "jboss_username", "username");
    private static final List<String> PROFILE_FIELDS = List.of("github_id", "name", "html_url", "avatar_url", "email", "gravatar_id",
            "blog", "location", "bio", "company", "created_at", "contributions", "source_url", "source_payload");

    /**
     * Returns caller-owned {@code identities} and {@code unmatched} arrays, retaining absent historical data.
     * Inputs are arrays of canonical rows, source observations and explicit manual override rows; no input is mutated.
     * Git email observations can attribute counts to already trusted emails, but cannot establish account ownership.
     * @throws IOException when malformed inputs or conflicting claims require human review
     */
    public ObjectNode normalize(JsonNode existing, JsonNode observations, JsonNode overrides) throws IOException {
        Map<String, ObjectNode> people = new TreeMap<>();
        for (JsonNode row : rows(existing)) {
            String id = text(row, "id");
            if (people.putIfAbsent(id, row.deepCopy()) != null) throw new IOException("Duplicate canonical identity: " + id);
        }
        index(people);
        for (JsonNode override : rows(overrides)) {
            ObjectNode person = locate(people, override, false);
            if (person == null) {
                String account = text(override, "github_id");
                String id = override.has("id") ? text(override, "id") : "manual:identity:" + lower(account);
                person = JsonNodeFactory.instance.objectNode().put("id", id).put("source", "manual").put("source_id", lower(account));
                if (people.putIfAbsent(id, person) != null) throw new IOException("Canonical manual ID collision requires review");
            }
            for (String key : PROTECTED) {
                if (person.has(key) && override.has(key) && !(ACCOUNTS.contains(key)
                        ? lower(text(person, key)).equals(lower(text(override, key))) : person.get(key).equals(override.get(key)))) {
                    throw new IOException("Protected identity change requires review: " + key);
                }
            }
            merge(person, override, true);
            index(people);
        }
        ArrayNode unmatched = JsonNodeFactory.instance.arrayNode();
        Map<String, JsonNode> counts = new HashMap<>();
        List<JsonNode> incoming = rows(observations);
        // Profiles establish trusted relationships before count and attribution observations are resolved.
        for (String source : List.of("github", "git-email", "attribution")) {
            for (JsonNode observation : incoming) {
                validateObservation(observation);
                if (!source.equals(observation.path("source").asText())) continue;
                ObjectNode person = locate(people, observation, true);
                if (person == null && source.equals("github")) {
                    person = JsonNodeFactory.instance.objectNode().put("id", text(observation, "id")).put("source", "github");
                    person.set("source_id", observation.get("source_id").deepCopy());
                    if (people.putIfAbsent(text(person, "id"), person) != null) throw new IOException("Canonical ID collision requires review");
                }
                if (person == null) {
                    add(unmatched, observation);
                    continue;
                }
                switch (source) {
                    case "github" -> {
                        for (String field : ACCOUNTS) {
                            if (person.has(field) && observation.has(field) && !person.get(field).equals(observation.get(field))) {
                                throw new IOException("Account change requires review: " + field);
                            }
                        }
                        for (JsonNode previous : sources(person)) {
                            if (previous.path("source").asText().equals("github")
                                    && !previous.path("source_id").asText().equals(observation.path("source_id").asText())) {
                                throw new IOException("GitHub source ID changed for canonical account");
                            }
                        }
                        merge(person, observation, false);
                    }
                    case "git-email" -> applyCounts(person, observation, counts);
                    case "attribution" -> attribute(person, observation);
                    default -> throw new IOException("Unsupported identity source");
                }
                add(array(person, "observations"), observation);
                index(people);
            }
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ArrayNode identities = result.putArray("identities");
        people.values().forEach(identities::add);
        result.set("unmatched", unmatched);
        return result;
    }

    /** Validates row containers before copying anything into the result. */
    private static List<JsonNode> rows(JsonNode value) throws IOException {
        if (value == null || !value.isArray()) throw new IOException("Identity input must be an array");
        List<JsonNode> rows = new java.util.ArrayList<>();
        for (JsonNode row : value) {
            if (!row.isObject()) throw new IOException("Identity row must be an object");
            rows.add(row);
        }
        return rows;
    }

    /** Indexes trusted account, email, stable-ID and source-ID keys and rejects ambiguous ownership. */
    private static Map<String, ObjectNode> index(Map<String, ObjectNode> people) throws IOException {
        Map<String, ObjectNode> index = new HashMap<>();
        for (ObjectNode person : people.values()) {
            validateCounts(person);
            for (String key : keys(person, false)) {
                ObjectNode previous = index.putIfAbsent(key, person);
                if (previous != null && previous != person) throw new IOException("Conflicting identity relationship requires review");
            }
            for (JsonNode observation : sources(person)) {
                if (observation.has("source")) {
                    String key = "source:" + text(observation, "source") + ":" + scalar(observation, "source_id");
                    ObjectNode previous = index.putIfAbsent(key, person);
                    if (previous != null && previous != person) throw new IOException("Source identity claimed by multiple people");
                }
            }
        }
        return index;
    }

    /** Resolves every supplied exact key together, so a contradictory selector cannot silently choose its first match. */
    private static ObjectNode locate(Map<String, ObjectNode> people, JsonNode row, boolean observation) throws IOException {
        Map<String, ObjectNode> index = index(people);
        ObjectNode found = null;
        for (String key : keys(row, observation)) {
            ObjectNode candidate = index.get(key);
            if (candidate != null && found != null && candidate != found) throw new IOException("Conflicting identity selectors require review");
            if (candidate != null) found = candidate;
        }
        return found;
    }

    /** Builds normalized exact keys; Git account hints and personal names deliberately supply no keys. */
    private static Set<String> keys(JsonNode row, boolean observation) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        if ((!observation || row.path("source").asText().equals("github")) && row.has("id")) keys.add("id:" + text(row, "id"));
        if (row.has("identity_id")) keys.add("id:" + text(row, "identity_id"));
        for (String field : ACCOUNTS) {
            if (row.hasNonNull(field)) keys.add("account:" + lower(text(row, field)));
        }
        for (String field : List.of("aliases", "emails")) {
            if (row.has(field)) {
                if (!row.get(field).isArray()) throw new IOException("Identity field must be an array: " + field);
                for (JsonNode value : row.get(field)) keys.add((field.equals("aliases") ? "account:" : "email:") + lower(string(value)));
            }
        }
        if (row.hasNonNull("email")) keys.add("email:" + lower(text(row, "email")));
        if (row.has("contributor")) {
            if (!row.get("contributor").isObject()) throw new IOException("Contributor must be an object");
            if (row.path("contributor").has("emails")) {
                JsonNode emails = row.path("contributor").path("emails");
                if (!emails.isArray()) throw new IOException("Contributor emails must be an array");
                for (JsonNode email : emails) keys.add("email:" + lower(string(email)));
            }
        }
        if (row.has("source") || row.has("source_id")) keys.add("source:" + text(row, "source") + ":" + scalar(row, "source_id"));
        return keys;
    }

    /** Copies manual choices or fills missing upstream values without replacing canonical identity or avatar choices. */
    private static void merge(ObjectNode person, JsonNode incoming, boolean manual) throws IOException {
        validateCounts(incoming);
        if (manual && incoming.has("commits")) {
            person.put("manual_commits", incoming.path("commits").asLong());
            person.put("commits", Math.max(person.path("commits").asLong(), incoming.path("commits").asLong()));
        }
        for (Map.Entry<String, JsonNode> entry : incoming.properties()) {
            String field = entry.getKey();
            if (!manual && !PROFILE_FIELDS.contains(field)) continue;
            if (manual && field.equals("commits")) continue;
            if (List.of("emails", "aliases", "teams").contains(field)) {
                if (!entry.getValue().isArray()) throw new IOException("Identity field must be an array: " + field);
                for (JsonNode value : entry.getValue()) add(array(person, field), field.equals("emails")
                        ? JsonNodeFactory.instance.textNode(lower(string(value))) : value);
            } else if (!PROTECTED.contains(field) && (manual || !person.hasNonNull(field))) {
                person.set(field, entry.getValue().deepCopy());
            } else if (ACCOUNTS.contains(field) && !person.has(field)) person.put(field, lower(text(incoming, field)));
        }
        if (incoming.hasNonNull("email")) add(array(person, "emails"), JsonNodeFactory.instance.textNode(lower(text(incoming, "email"))));
    }

    /** Validates source identity and counts before trusting a collector row or even retaining an unmatched hint. */
    private static void validateObservation(JsonNode row) throws IOException {
        String source = text(row, "source");
        String id = text(row, "id");
        String sourceId = scalar(row, "source_id");
        keys(row, true);
        validateCounts(row);
        if (row.has("source_payload") && !row.get("source_payload").isObject()) throw new IOException("Source payload must be an object");
        if (source.equals("github")) {
            text(row, "github_id");
            for (String field : List.of("identity_id", "aliases", "emails", "contributor", "username", "jboss_username",
                    "observations", "git_author_counts", "repository_commits", "manual_commits", "historical_commits", "commits")) {
                if (row.has(field)) throw new IOException("GitHub observation contains non-profile identity claims: " + field);
            }
            if (!sourceId.matches("[1-9][0-9]*") || !id.equals("github:user:" + sourceId)
                    || row.path("source_payload").has("id") && !row.path("source_payload").path("id").asText().equals(sourceId)
                    || row.path("source_payload").has("login") && !lower(row.path("source_payload").path("login").asText()).equals(lower(text(row, "github_id")))) {
                throw new IOException("Invalid GitHub source identity");
            }
        } else if (source.equals("git-email")) {
            if (!sourceId.equals(lower(text(row, "email"))) || !id.equals("git:author:" + sourceId)
                    || ACCOUNTS.stream().anyMatch(row::has) || row.has("identity_id") || row.has("aliases") || row.has("contributor")) {
                throw new IOException("Git hints cannot claim account ownership");
            }
            for (JsonNode email : row.path("emails")) if (!lower(string(email)).equals(sourceId)) throw new IOException("Git hint contains unrelated email");
            if (!row.path("repository_commits").isObject()) throw new IOException("Git author requires repository counts");
        } else if (source.equals("attribution")) {
            switch (text(row, "kind")) {
                case "team" -> {
                    if (!List.of("core", "speaker", "translator").contains(text(row, "team"))) throw new IOException("Unsupported legacy team");
                }
                case "component-lead" -> text(row, "component");
                case "guide-author" -> text(row, "guide");
                case "guide-translator" -> { text(row, "guide"); text(row, "language"); }
                default -> throw new IOException("Unsupported identity attribution kind");
            }
        } else throw new IOException("Unsupported identity source: " + source);
    }

    /** Replaces email counts while retaining historical repository floors and credit missing from a partial breakdown. */
    private static void applyCounts(ObjectNode person, JsonNode row, Map<String, JsonNode> seen) throws IOException {
        if (!person.has("git_author_counts") && person.has("repository_commits")) {
            person.set("historical_repository_commits", person.get("repository_commits").deepCopy());
            long represented = 0;
            for (JsonNode count : person.get("repository_commits")) represented = sum(represented, count.asLong());
            person.put("historical_unattributed_commits", Math.max(0, person.path("commits").asLong() - represented));
        }
        if (!person.has("git_author_counts") && person.has("commits") && !person.has("repository_commits")) {
            person.put("historical_commits", person.path("commits").asLong());
        }
        ObjectNode ledger = person.has("git_author_counts") ? (ObjectNode) person.get("git_author_counts") : person.putObject("git_author_counts");
        ObjectNode totals = person.has("repository_commits") ? (ObjectNode) person.get("repository_commits") : person.putObject("repository_commits");
        String email = lower(text(row, "email"));
        ObjectNode author = ledger.has(email) ? (ObjectNode) ledger.get(email) : ledger.putObject(email);
        for (Map.Entry<String, JsonNode> count : row.path("repository_commits").properties()) {
            String key = email + "\u0000" + count.getKey();
            JsonNode previous = seen.putIfAbsent(key, count.getValue());
            if (previous != null && !previous.equals(count.getValue())) throw new IOException("Conflicting repeated repository counts");
            author.set(count.getKey(), count.getValue().deepCopy());
            long sum = 0;
            for (JsonNode contribution : ledger) sum = sum(sum, contribution.path(count.getKey()).asLong());
            totals.put(count.getKey(), Math.max(sum, person.path("historical_repository_commits").path(count.getKey()).asLong()));
        }
        long sum = 0;
        for (JsonNode total : totals) sum = sum(sum, total.asLong());
        person.put("commits", Math.max(Math.max(person.path("historical_commits").asLong(), person.path("manual_commits").asLong()),
                sum(sum, person.path("historical_unattributed_commits").asLong())));
    }

    /** Rejects malformed legacy and normalized ledgers before any numeric coercion or mutable cast. */
    private static void validateCounts(JsonNode row) throws IOException {
        for (String field : List.of("commits", "manual_commits", "historical_commits", "historical_unattributed_commits", "contributions")) {
            if (row.hasNonNull(field)) number(row.get(field));
        }
        if (row.has("repository_commits")) countMap(row.get("repository_commits"));
        if (row.has("historical_repository_commits")) countMap(row.get("historical_repository_commits"));
        if (row.has("git_author_counts")) {
            if (!row.get("git_author_counts").isObject()) throw new IOException("Git author ledger must be an object");
            for (Map.Entry<String, JsonNode> author : row.get("git_author_counts").properties()) {
                if (author.getKey().isBlank()) throw new IOException("Missing ledger email");
                countMap(author.getValue());
            }
        }
    }

    /** Validates every repository count and overflow even for an unmatched observation. */
    private static void countMap(JsonNode value) throws IOException {
        if (!value.isObject()) throw new IOException("Repository counts must be an object");
        long total = 0;
        for (Map.Entry<String, JsonNode> count : value.properties()) {
            if (count.getKey().isBlank()) throw new IOException("Missing repository count key");
            total = sum(total, number(count.getValue()));
        }
    }

    /** Requires a nonnegative signed-long count rather than silently truncating arbitrary JSON values. */
    private static long number(JsonNode value) throws IOException {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) throw new IOException("Invalid commit count");
        return value.asLong();
    }

    /** Converts arithmetic overflow into the same reviewable input failure as other malformed observations. */
    private static long sum(long left, long right) throws IOException {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException cause) { throw new IOException("Commit count overflow", cause); }
    }

    /** Applies explicit account-based roles and author credits while retaining original attribution observations. */
    private static void attribute(ObjectNode person, JsonNode row) throws IOException {
        switch (text(row, "kind")) {
            case "team" -> {
                String team = text(row, "team");
                if (!List.of("core", "speaker", "translator").contains(team)) throw new IOException("Unsupported legacy team");
                add(array(person, "teams"), row.get("team"));
                person.put(team, true);
            }
            case "component-lead" -> add(array(person, "component_leads"), JsonNodeFactory.instance.textNode(text(row, "component")));
            case "guide-author" -> add(array(person, "guide_authorships"), JsonNodeFactory.instance.textNode(text(row, "guide")));
            case "guide-translator" -> {
                person.put("translator", true);
                add(array(person, "translations"), JsonNodeFactory.instance.objectNode().put("guide", text(row, "guide")).put("language", text(row, "language")));
            }
            default -> throw new IOException("Unsupported identity attribution kind");
        }
    }

    /** Includes the protected primary source together with retained upstream provenance for conflict checks. */
    private static List<JsonNode> sources(ObjectNode person) throws IOException {
        List<JsonNode> sources = new java.util.ArrayList<>();
        if (person.has("source")) sources.add(person);
        if (person.has("observations")) sources.addAll(rows(person.get("observations")));
        return sources;
    }

    /** Returns a validated mutable array so scalar historical data never disappears through coercion. */
    private static ArrayNode array(ObjectNode person, String field) throws IOException {
        if (!person.has(field)) return person.putArray(field);
        if (!(person.get(field) instanceof ArrayNode array)) throw new IOException("Identity field must be an array: " + field);
        return array;
    }

    /** Retains distinct original observations while making repeated normalization idempotent. */
    private static void add(ArrayNode array, JsonNode value) {
        for (JsonNode existing : array) if (existing.equals(value)) return;
        array.add(value.deepCopy());
    }

    /** Requires a nonblank textual field at the JSON trust boundary. */
    private static String text(JsonNode row, String field) throws IOException {
        return string(row.path(field));
    }

    /** Requires a scalar source ID while preserving its original JSON representation elsewhere. */
    private static String scalar(JsonNode row, String field) throws IOException {
        JsonNode value = row.path(field);
        if (value.isIntegralNumber()) return value.asText();
        return string(value);
    }

    /** Rejects implicit Jackson string coercion for account and attribution selectors. */
    private static String string(JsonNode value) throws IOException {
        if (!value.isTextual() || value.asText().isBlank() || !value.asText().equals(value.asText().strip())) throw new IOException("Expected nonblank identity string");
        return value.asText();
    }

    /** Makes case normalization independent of the refresh machine's locale. */
    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
