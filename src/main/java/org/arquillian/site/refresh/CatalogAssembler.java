package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Assembles complete renderable catalog rows from exact upstream bindings without I/O or input mutation. */
public final class CatalogAssembler {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final List<String> DOMAINS = List.of("repositories", "releases", "identities", "modules", "guides", "events");
    private static final Set<String> TYPES = Set.of("Bom", "Container Adapter", "Daemon", "Extension", "Obsolete Container Adapter",
            "Obsolete Extension", "Platform", "Shrinkwrap", "Tck", "Test Reports", "Test Runner", "Tool", "Tool Plugin");
    private static final Set<String> DIRECTORY_CATEGORIES = Set.of("bom", "platform", "tool", "daemon", "container_adapter",
            "extension", "test_runner", "tool_plugin", "shrinkwrap", "tck", "test_reports");
    private static final Set<String> OPTIONAL_PRESENTATION = Set.of("bio", "location", "company", "blog", "homepage", "urls", "name_cloak");
    private static final Set<String> GRAVATAR_ACCOUNT_FIELDS = Set.of("gravatar_id", "gravatar", "preferred_username", "names", "name",
            "email", "emails", "twitter", "github", "facebook", "linkedin", "google", "flickr", "youtube", "vimeo",
            "stackoverflow", "wordpress", "tumblr", "blogger", "lanyrd");

    /** Creates a pure assembler with no clock, service client or snapshot-store dependency. */
    public CatalogAssembler() {}

    /** Returns a complete candidate, retaining all historical rows and requiring explicit safe seeds for additions. */
    public ObjectNode assemble(ObjectNode existingCatalog, ObjectNode observations, ObjectNode mappings) throws IOException {
        ObjectNode candidate = existingCatalog.deepCopy();
        for (String domain : DOMAINS) index(candidate, domain);
        for (JsonNode evidence : rows(observations, "source_completeness")) {
            if (!evidence.path("complete").isBoolean() || !evidence.path("complete").asBoolean()
                    || !evidence.path("count").canConvertToInt() || evidence.path("count").asInt() < evidence.path("minimum").asInt()) {
                throw new IOException("Incomplete refresh source: " + evidence.path("id").asText());
            }
        }
        seed(candidate, "repositories", mappings, "repository_bindings");
        seed(candidate, "modules", mappings, "components");
        seed(candidate, "releases", mappings, "announcements");
        seed(candidate, "identities", mappings, "identity_profiles");
        seed(candidate, "guides", mappings, "guide_bindings");
        Map<String, ObjectNode> repositories = index(candidate, "repositories");
        Map<String, String> repositoryIds = repositories(candidate, observations, mappings);
        normalize(candidate, observations, mappings, repositoryIds);
        Map<String, ObjectNode> people = index(candidate, "identities");
        Map<String, ObjectNode> modules = index(candidate, "modules");
        Map<String, ObjectNode> componentMappings = mappingIndex(mappings, "components", "id");
        Set<String> refreshedRepositories = new HashSet<>();
        for (JsonNode observation : rows(observations, "repositories")) refreshedRepositories.add(repositoryIds.get(required(observation, "id")));
        for (ObjectNode module : modules.values()) {
            String repositoryId = module.path("repository_id").asText();
            JsonNode binding = componentMappings.getOrDefault(required(module, "id"), JSON.objectNode());
            if (binding.has("repository_id")) {
                if (!repositoryId.isBlank() && !repositoryId.equals(required(binding, "repository_id"))) throw new IOException("Component repository change requires review");
                repositoryId = required(binding, "repository_id");
                module.put("repository_id", repositoryId);
            }
            ObjectNode repository = repositories.get(repositoryId);
            if (repository == null) throw new IOException("Component needs a canonical repository binding: " + module.path("id").asText());
            List<JsonNode> selected = select(rows(observations, "artifact_modules"), module, binding, repositoryIds);
            if (refreshedRepositories.contains(repositoryId)) {
                for (JsonNode requiredArtifact : rows(binding, "artifacts")) {
                    String coordinate = required(requiredArtifact, "groupId") + ":" + required(requiredArtifact, "artifactId");
                    if (selected.stream().noneMatch(model -> coordinate.equals(model.path("groupId").asText() + ":" + model.path("artifactId").asText()))) {
                        throw new IOException("Missing current component artifact " + coordinate + "; review components artifacts/relative_path binding: " + module.path("id").asText());
                    }
                }
            }
            if (refreshedRepositories.contains(repositoryId) && selected.isEmpty()) throw new IOException("Current component artifacts are absent; review components artifacts/relative_path binding: " + module.path("id").asText());
            if (!selected.isEmpty()) {
                module.set("build_modules", JSON.arrayNode().addAll(selected));
                CatalogFragments.repository(module, repository);
                universe(module, repository, rows(observations, "artifact_modules"), repositoryIds);
            }
            List<JsonNode> tags = new ArrayList<>();
            for (JsonNode tag : rows(observations, "tag_releases")) {
                if (!repositoryId.equals(repositoryIds.getOrDefault(tag.path("repository").asText(), tag.path("repository").asText()))) continue;
                List<JsonNode> artifacts = select(rows(tag, "artifacts"), module, binding, repositoryIds);
                if (!artifacts.isEmpty()) { tags.add(tag); history(module, tag, artifacts, candidate, people, mappings); }
            }
            tags.sort(Comparator.comparing((JsonNode tag) -> tag.path("date").asText()).thenComparing(tag -> tag.path("tag").asText()));
            if (!tags.isEmpty()) {
                List<JsonNode> history = rows(module, "release_history");
                // Stable date sorting preserves the published order of historical same-day releases.
                history.sort(Comparator.comparing((JsonNode row) -> row.path("date").asText()).reversed());
                module.set("release_history", JSON.arrayNode().addAll(history));
                JsonNode latest = tags.getLast();
                CatalogFragments.metadata(module, required(latest, "version"), date(required(latest, "date")));
            }
            List<JsonNode> published = tags.isEmpty() ? selected : select(rows(tags.getLast(), "artifacts"), module, binding, repositoryIds);
            if (!published.isEmpty()) CatalogFragments.artifacts(module, published);
            if (!selected.isEmpty() || !tags.isEmpty()) {
                contributors(module, repository.path("contributors"), people, false);
                for (ObjectNode person : people.values()) {
                    if (contains(person.path("component_leads"), module.path("id").asText())) {
                        module.set("lead", personView(person));
                        module.putArray("lead_links").addObject().put("href", required(person, "link")).put("title", required(person, "name"));
                    }
                }
            }
        }
        notes(candidate, observations);
        guides(candidate, observations, mappings, people);
        for (JsonNode event : rows(observations, "events")) mergeEvent(candidate, event);
        reviewed(candidate, observations, mappings);
        latest(candidate);
        directories(candidate);
        for (String domain : DOMAINS) index(candidate, domain);
        return candidate;
    }

    /** Adds only reviewed complete seeds; an existing canonical ID cannot be replaced by a creation mapping. */
    private static void seed(ObjectNode catalog, String domain, ObjectNode mappings, String field) throws IOException {
        Map<String, ObjectNode> existing = index(catalog, domain);
        for (JsonNode binding : rows(mappings, field)) {
            if (!binding.has("create")) continue;
            if (!(binding.get("create") instanceof ObjectNode row)) throw new IOException("Creation seed must be an object: " + field);
            String id = required(binding, "id");
            if (!id.equals(required(row, "id"))) throw new IOException("Creation mapping ID and seed disagree");
            if (existing.containsKey(id)) continue;
            ObjectNode created = row.deepCopy();
            validateNew(domain, created);
            CatalogFragments.array(catalog, domain).add(created);
            existing.put(id, created);
        }
    }

    /** Requires every consumed field that cannot safely be derived for a newly published page. */
    private static void validateNew(String domain, ObjectNode row) throws IOException {
        for (String field : List.of("id", "source")) required(row, field);
        if (!(row.path("source_id").isTextual() || row.path("source_id").isIntegralNumber()) || row.path("source_id").asText().isBlank()) throw new IOException("Creation seed needs a scalar source_id");
        if (!domain.equals("repositories")) {
            String prefix = switch (domain) { case "modules" -> "modules"; case "identities" -> "community"; case "guides" -> "guides"; default -> "blog"; };
            String link = required(row, "link");
            if (!link.matches("/" + prefix + "/[A-Za-z0-9_./-]+/") || link.contains("..")) throw new IOException("New page needs an explicit canonical route: " + domain);
        }
        List<String> texts = switch (domain) {
            case "repositories" -> List.of("full_name", "html_url", "path", "owner");
            case "modules" -> List.of("title", "name", "type", "repository_id");
            case "identities" -> List.of("name", "avatar_url");
            case "guides" -> List.of("title", "language", "baseline_link", "edit_url");
            case "releases" -> List.of("title", "slug", "date", "published_date", "author", "author_link", "author_avatar_url", "comment_id", "comment_url", "disqus_url", "component", "module_link", "version", "tag_url", "released_by");
            default -> List.of();
        };
        for (String field : texts) required(row, field);
        List<String> arrays = switch (domain) {
            case "modules" -> List.of("facts", "repository_facts", "lead_links", "release_history", "artifact_links", "hero_blocks", "repository_badges", "artifact_blocks", "contributors");
            case "identities" -> List.of("profiles", "teams");
            case "guides" -> List.of("translations", "chapters");
            case "releases" -> List.of("details", "artifacts", "resolved_issues", "contributors", "tags");
            default -> List.of();
        };
        for (String field : arrays) if (!row.path(field).isArray()) throw new IOException("New " + domain + " needs " + field + " array");
        List<String> strings = switch (domain) {
            case "modules" -> List.of("description_html", "metadata_html");
            case "identities" -> List.of("bio_html", "role", "company", "location");
            case "guides" -> List.of("authors_html", "footer_html");
            case "releases" -> List.of("definition_title", "definition_html", "artifacts_group", "issues_comment", "latest_link", "latest_warning_html");
            default -> List.of();
        };
        for (String field : strings) if (!row.path(field).isTextual()) throw new IOException("New " + domain + " needs " + field + " text");
        if (domain.equals("modules") && !TYPES.contains(row.path("type").asText())) throw new IOException("Unknown component classification; configure a supported explicit type");
        if (domain.equals("modules")) for (String field : List.of("lead", "documentation", "activity")) if (!row.path(field).isObject()) throw new IOException("New component needs " + field);
        if (domain.equals("modules") && !row.path("directory").isObject()) throw new IOException("New component needs explicit directory classification");
        if (domain.equals("identities") && !row.path("directory_cards").isArray()) throw new IOException("New profile needs explicit directory cards, even when unlisted");
        if (domain.equals("releases")) {
            if (!row.path("published_order").isIntegralNumber() || !row.path("issues_count").isIntegralNumber()) throw new IOException("New announcement needs publication order and issue count");
            date(row.path("date").asText()); date(row.path("published_date").asText());
        }
        for (Map.Entry<String, JsonNode> entry : row.properties()) {
            if (entry.getKey().endsWith("_html") && entry.getValue().isTextual()) row.put(entry.getKey(), CatalogFragments.clean(entry.getValue().asText()));
        }
    }

    /** Resolves repository observations by exact canonical locator or explicit immutable upstream binding. */
    private static Map<String, String> repositories(ObjectNode catalog, ObjectNode batch, ObjectNode mappings) throws IOException {
        Map<String, ObjectNode> canonical = index(catalog, "repositories");
        Map<String, ObjectNode> bindings = mappingIndex(mappings, "repository_bindings", "repository");
        Map<String, String> ids = new HashMap<>();
        Map<String, String> numeric = new HashMap<>();
        for (ObjectNode row : canonical.values()) {
            String id = required(row, "id");
            ids.put(id, id);
            if (row.has("github_repository_id") && numeric.putIfAbsent(row.path("github_repository_id").asText(), id) != null) throw new IOException("Historical numeric repository identity collision");
        }
        for (JsonNode observation : rows(batch, "repositories")) {
            String locator = required(observation, "full_name");
            JsonNode binding = bindings.get(locator);
            ObjectNode target = binding == null ? null : canonical.get(required(binding, "id"));
            if (binding != null && binding.has("github_repository_id") && !binding.path("github_repository_id").asText().equals(observation.path("source_id").asText())) throw new IOException("Repository numeric binding disagrees: " + locator);
            for (ObjectNode row : canonical.values()) {
                if (!row.path("full_name").asText().equals(locator)) continue;
                if (target != null && target != row) throw new IOException("Ambiguous repository binding: " + locator);
                target = row;
            }
            if (target == null) throw new IOException("Unknown repository; add repository_bindings create mapping: " + locator);
            String id = required(target, "id");
            if (target.has("github_repository_id") && !target.path("github_repository_id").asText().equals(observation.path("source_id").asText())) throw new IOException("Repository source identity changed");
            String previous = numeric.putIfAbsent(observation.path("source_id").asText(), id);
            if (previous != null && !previous.equals(id)) throw new IOException("Numeric repository identity collision");
            target.set("github_repository_id", observation.path("source_id").deepCopy());
            for (String field : List.of("path", "owner", "full_name", "description", "clone_url", "html_url", "commits_url", "topics", "commit", "contributors", "last_commit", "unreleased_commits", "latest_version", "latest_tag")) {
                if (observation.has(field)) target.set(field, observation.get(field).deepCopy());
            }
            provenance(target, observation);
            ids.put(required(observation, "id"), id);
            ids.put(locator, id);
        }
        return ids;
    }

    /** Normalizes all ownership through C3a before updating safe current profile presentation values. */
    private static void normalize(ObjectNode catalog, ObjectNode batch, ObjectNode mappings, Map<String, String> repositoryIds) throws IOException {
        ArrayNode observations = JSON.arrayNode();
        for (JsonNode incoming : rows(batch, "identity_observations")) {
            ObjectNode row = incoming.deepCopy();
            if (row.path("repository_commits").isObject()) {
                ObjectNode counts = row.putObject("repository_commits");
                for (Map.Entry<String, JsonNode> entry : incoming.path("repository_commits").properties()) counts.set(repositoryIds.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue().deepCopy());
            }
            observations.add(row);
        }
        ObjectNode normalized = new IdentityNormalizer().normalize(catalog.path("identities"), observations, rowsNode(mappings, "identity_overrides"));
        for (JsonNode unmatched : normalized.path("unmatched")) if (unmatched.path("source").asText().equals("attribution")) throw new IOException("Unresolved identity attribution; configure identity_overrides or identity_profiles");
        catalog.set("identities", normalized.path("identities"));
        for (JsonNode observation : observations) {
            if (!observation.path("source").asText().equals("github")) continue;
            ObjectNode person = person(index(catalog, "identities"), observation, false);
            if (person != null) CatalogFragments.profile(person, observation);
        }
        Map<String, ObjectNode> people = index(catalog, "identities");
        for (JsonNode override : rows(mappings, "identity_overrides")) {
            ObjectNode person = person(people, override, false);
            if (person != null) CatalogFragments.profile(person, override);
        }
        for (ObjectNode person : index(catalog, "identities").values()) {
            if (!person.has("link")) {
                // New observed identities have no public profile or directory card until reviewed.
                if (!person.has("directory_cards")) person.putArray("directory_cards");
                continue;
            }
            if (!person.path("link").asText().matches("/community/[A-Za-z0-9_-]+/")) throw new IOException("Profile route requires explicit identity_profiles mapping");
        }
    }

    /** Selects only exact coordinates already published for this component or explicitly configured artifacts/paths. */
    private static List<JsonNode> select(List<JsonNode> models, ObjectNode module, JsonNode binding, Map<String, String> repositoryIds) throws IOException {
        Set<String> coordinates = new HashSet<>();
        for (JsonNode artifact : rows(binding, "artifacts")) coordinates.add(required(artifact, "groupId") + ":" + required(artifact, "artifactId"));
        if (coordinates.isEmpty()) {
            for (JsonNode block : module.path("artifact_blocks")) {
                if (block.path("kind").asText().equals("coordinates")) {
                    Element dependency = Jsoup.parse(block.path("text").asText(), "", org.jsoup.parser.Parser.xmlParser()).selectFirst("dependency");
                    if (dependency != null) coordinates.add(dependency.select("groupId").text() + ":" + dependency.select("artifactId").text());
                }
                for (JsonNode artifact : block.path("items")) {
                    String[] parts = artifact.path("coordinates").asText().split(":");
                    if (parts.length >= 4) coordinates.add(parts[0] + ":" + parts[1]);
                }
            }
        }
        List<JsonNode> selected = new ArrayList<>();
        Set<String> selectedCoordinates = new HashSet<>();
        for (JsonNode model : models) {
            String repositoryId = repositoryIds.getOrDefault(model.path("repository").asText(), model.path("repository").asText());
            if (!repositoryId.equals(module.path("repository_id").asText())) continue;
            if (binding.has("relative_path") && !model.path("relative_path").asText().equals(binding.path("relative_path").asText())) continue;
            String coordinate = model.path("groupId").asText() + ":" + model.path("artifactId").asText();
            if (coordinates.contains(coordinate)) {
                if (!selectedCoordinates.add(coordinate)) throw new IOException("Ambiguous artifact paths; add explicit components relative_path binding");
                selected.add(model.deepCopy());
            }
        }
        return selected;
    }

    /** Ports Universe generator metadata from current models, failing incomplete inputs instead of keeping stale selectable artifacts. */
    private static void universe(ObjectNode module, ObjectNode repository, List<JsonNode> models, Map<String, String> repositoryIds) throws IOException {
        if (!module.path("link").asText().equals("/modules/arquillian-universe-bom-bom/")) return;
        if (!module.path("type").asText().equals("Bom") || !repository.path("full_name").asText().equals("arquillian/arquillian-universe-bom")) throw new IOException("Universe generator requires its exact canonical BOM/repository binding");
        Map<String, JsonNode> paths = new HashMap<>();
        for (JsonNode model : models) {
            if (!module.path("repository_id").asText().equals(repositoryIds.getOrDefault(model.path("repository").asText(), model.path("repository").asText()))) continue;
            if (paths.putIfAbsent(required(model, "source_path"), model) != null) throw new IOException("Ambiguous current Universe POM path; reconcile artifact_modules");
        }
        String root = repository.path("relative_path").asText();
        JsonNode bom = paths.get(root + "arquillian-universe/pom.xml");
        if (bom == null || !bom.path("groupId").asText().equals("org.arquillian") || !bom.path("artifactId").asText().equals("arquillian-universe")) throw new IOException("Missing canonical Universe POM coordinates; reconcile current artifact_modules");
        if (!bom.path("dependency_management").isArray() || bom.path("dependency_management").isEmpty()) throw new IOException("Universe dependency_management is empty or missing; review before replacing generator inputs");
        ArrayNode extensions = JSON.arrayNode();
        Set<String> coordinates = new HashSet<>();
        for (JsonNode dependency : rows(bom, "dependency_management")) {
            String group = required(dependency, "groupId"); String artifact = required(dependency, "artifactId");
            if (!group.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*") || !artifact.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*") || !coordinates.add(group + ":" + artifact)) throw new IOException("Unresolved or ambiguous Universe dependency coordinates; reconcile dependency_management");
            JsonNode model = paths.get(root + artifact + "/pom.xml");
            if (model == null || !model.path("groupId").asText().equals(group) || !model.path("artifactId").asText().equals(artifact)) throw new IOException("Missing exact Universe artifact POM: " + group + ":" + artifact);
            String name = required(model, "name");
            if (name.contains("${")) throw new IOException("Unresolved Universe artifact name: " + artifact);
            extensions.addObject().put("name", name).put("groupId", group).put("artifactId", artifact);
        }
        module.set("extensions", extensions);
    }

    /** Updates history and existing announcements, deriving new complete rows only under explicit component publication policy. */
    private static void history(ObjectNode module, JsonNode tag, List<JsonNode> artifacts, ObjectNode catalog, Map<String, ObjectNode> people, ObjectNode mappings) throws IOException {
        String version = required(tag, "version");
        ObjectNode announcement = announcement(catalog, module, version, mappings);
        if (announcement == null) {
            for (JsonNode binding : rows(mappings, "components")) if (binding.path("id").equals(module.path("id")) && binding.has("announcement_policy")) {
                announcement = deriveAnnouncement(catalog, module, tag, people, binding.path("announcement_policy"));
            }
        }
        ArrayNode history = CatalogFragments.array(module, "release_history");
        ObjectNode row = null;
        for (JsonNode candidate : history) if (candidate.path("version").asText().equals(version)) {
            if (row != null) throw new IOException("Ambiguous historical component/version; configure explicit reconciliation");
            row = (ObjectNode) candidate;
        }
        if (row == null) row = history.addObject().put("legacy_id", tag.path("tag").asText()).put("version", version).put("compiled_against_html", "");
        String previous = row.toString();
        String releaseDate = date(required(tag, "date"));
        row.put("date", releaseDate).put("tag_url", CatalogFragments.safeLink(required(tag, "source_url")));
        ObjectNode releaser = person(people, tag.path("released_by"), true);
        row.put("released_by", releaser == null ? tag.path("released_by").path("name").asText() : releaser.path("name").asText());
        if (announcement != null) row.put("link", announcement.path("link").asText());
        String compiled = dependencies(artifacts, catalog, mappings);
        if (!compiled.isBlank()) row.put("compiled_against_html", compiled);
        if (!row.toString().equals(previous)) row.put("html", CatalogFragments.history(row));
        provenance(row, tag);
        if (announcement == null) return;
        announcement.put("module_id", module.path("id").asText());
        announcement.put("date", releaseDate).put("tag_url", row.path("tag_url").asText()).put("released_by", row.path("released_by").asText());
        CatalogFragments.detail(announcement, "details", "Component", CatalogFragments.anchor(module.path("link").asText(), module.path("name").asText()));
        CatalogFragments.detail(announcement, "details", "Version", CatalogFragments.text(version) + " " + CatalogFragments.anchor(row.path("tag_url").asText(), "view tag"));
        CatalogFragments.detail(announcement, "details", "Release date", CatalogFragments.text(releaseDate));
        CatalogFragments.detail(announcement, "details", "Released by", CatalogFragments.text(row.path("released_by").asText()));
        if (!compiled.isBlank()) CatalogFragments.detail(announcement, "details", "Compiled against", compiled);
        CatalogFragments.releaseArtifacts(announcement, artifacts);
        if (!artifacts.isEmpty()) announcement.put("artifacts_group", artifacts.getFirst().path("groupId").asText());
        contributors(announcement, tag.path("contributors"), people, true);
        provenance(announcement, tag);
    }

    /** Ports legacy generated-post defaults under explicit owner/date/slug/author policy while retaining authored rows unchanged. */
    private static ObjectNode deriveAnnouncement(ObjectNode catalog, ObjectNode module, JsonNode tag, Map<String, ObjectNode> people, JsonNode policy) throws IOException {
        if (!policy.isObject() || !TYPES.contains(module.path("type").asText())) throw new IOException("Announcement policy needs a classified canonical component");
        String released = date(required(tag, "date"));
        String since = required(policy, "since");
        try { LocalDate.parse(since); }
        catch (RuntimeException failure) { throw new IOException("Announcement policy needs an explicit ISO since date"); }
        ObjectNode repository = index(catalog, "repositories").get(required(module, "repository_id"));
        if (!policy.path("owners").isArray() || policy.path("owners").isEmpty()) throw new IOException("Announcement policy needs explicit repository owners");
        if (!contains(policy.path("owners"), required(repository, "owner")) || released.compareTo(since) < 0) return null;
        String prefix = required(policy, "slug_prefix");
        String version = required(tag, "version");
        if (!prefix.matches("[A-Za-z0-9_-]+") || !version.matches("[A-Za-z0-9_.-]+")) throw new IOException("Unsafe explicit announcement slug rule");
        if (!required(policy, "publication_date").equals("release-date") || !required(policy, "comment_id").equals("canonical-url")) throw new IOException("Unsupported announcement publication/comment identity policy");
        ObjectNode author;
        if (policy.has("author_id") && !policy.has("author")) author = people.get(required(policy, "author_id"));
        else if (!policy.has("author_id") && policy.path("author").asText().equals("tag-committer")) author = person(people, tag.path("released_by"), true);
        else throw new IOException("Announcement policy needs one explicit author_id or trusted tag-committer rule");
        if (author == null) throw new IOException("New announcement author is unmatched; configure trusted identity attribution");
        String authorLink = CatalogFragments.safeLink(required(author, "link"));
        String avatar = CatalogFragments.safeLink(required(author, "avatar_url"));
        String origin = CatalogFragments.safeLink(required(policy, "comment_origin")).replaceAll("/+$", "");
        URI originUri = URI.create(origin);
        if (!originUri.isAbsolute() || originUri.getQuery() != null || originUri.getFragment() != null || !originUri.getPath().isEmpty()) throw new IOException("Comment origin must be an explicit HTTP(S) origin");
        String slug = prefix + "-" + version.replace('.', '-');
        String link = "/blog/" + released.replace('-', '/') + "/" + slug + "/";
        String key = required(module, "id") + ":" + version;
        int order = 0;
        for (JsonNode row : catalog.path("releases")) order = Math.max(order, row.path("published_order").asInt());
        if (order == Integer.MAX_VALUE) throw new IOException("Announcement publication order is exhausted");
        ObjectNode row = JSON.objectNode().put("id", "derived:release:" + key).put("source", "derived").put("source_id", key)
                .put("source_url", origin + link).put("link", link).put("title", required(module, "name") + " " + version + " Released")
                .put("slug", slug).put("date", released).put("published_date", released).put("published_order", order + 1)
                .put("author", required(author, "name")).put("author_link", authorLink).put("author_avatar_url", avatar).put("author_avatar_title", "")
                .put("comment_id", origin + link).put("comment_url", origin + link + "#disqus_thread").put("disqus_url", origin + link)
                .put("component", required(module, "name")).put("module_link", required(module, "link")).put("version", version)
                .put("tag_url", required(tag, "source_url")).put("released_by", tag.path("released_by").path("name").asText(required(author, "name")))
                .put("definition_title", "What is " + module.path("name").asText() + "?").put("definition_html", module.path("description_html").asText())
                .put("artifacts_group", "").put("issues_count", 0).put("issues_comment", "").put("latest_link", link).put("latest_warning_html", "");
        for (String field : List.of("details", "artifacts", "resolved_issues", "contributors", "tags")) row.putArray(field);
        if (!policy.path("tags").isArray() || policy.path("tags").isEmpty()) throw new IOException("Announcement policy needs explicit legacy tags");
        for (JsonNode value : policy.path("tags")) {
            if (!value.isTextual() || value.asText().isBlank()) throw new IOException("Announcement policy tags must be strings");
            ((ArrayNode) row.path("tags")).addObject().put("title", value.asText()).put("href", "/blog/tags/" + DataHttpClient.encode(value.asText()) + "/");
        }
        if (List.of("Platform", "Extension").contains(module.path("type").asText()) && version.endsWith(".Final") && policy.path("tags").valueStream().noneMatch(value -> value.asText().equals("jbosscentral"))) {
            ((ArrayNode) row.path("tags")).addObject().put("title", "jbosscentral").put("href", "/blog/tags/jbosscentral/");
        }
        validateNew("releases", row);
        CatalogFragments.array(catalog, "releases").add(row);
        return row;
    }

    /** Finds announcements by canonical component route and exact version, never by display-name similarity. */
    private static ObjectNode announcement(ObjectNode catalog, ObjectNode module, String version, ObjectNode mappings) throws IOException {
        ObjectNode found = null;
        String explicit = null;
        for (JsonNode mapping : rows(mappings, "announcements")) if (mapping.path("module_id").asText().equals(module.path("id").asText()) && mapping.path("version").asText().equals(version)) {
            if (explicit != null) throw new IOException("Duplicate announcement component/version mapping");
            explicit = required(mapping, "id");
        }
        for (ObjectNode row : index(catalog, "releases").values()) {
            boolean matches = explicit == null ? row.path("module_link").asText().equals(module.path("link").asText()) && row.path("version").asText().equals(version) : row.path("id").asText().equals(explicit);
            if (!matches) continue;
            if (!row.path("version").asText().equals(version) || !row.path("module_link").asText().equals(module.path("link").asText())) throw new IOException("Announcement mapping rebinds component/version");
            if (found != null) throw new IOException("Ambiguous component announcement; configure announcements binding");
            found = row;
        }
        if (explicit != null && found == null) throw new IOException("Announcement mapping needs a complete create seed");
        return found;
    }

    /** Renders explicitly bound dependency coordinates rather than guessing legacy property aliases. */
    private static String dependencies(List<JsonNode> artifacts, ObjectNode catalog, ObjectNode mappings) throws IOException {
        Element list = new Element("ul").addClass("unstyled");
        Set<String> seen = new HashSet<>();
        for (JsonNode artifact : artifacts) for (JsonNode dependency : rows(artifact, "dependencies")) {
            for (JsonNode binding : rows(mappings, "dependency_bindings")) {
                if (!binding.path("groupId").equals(dependency.path("groupId")) || !binding.path("artifactId").equals(dependency.path("artifactId"))) continue;
                ObjectNode target = index(catalog, "modules").get(required(binding, "module_id"));
                if (target == null) throw new IOException("Unknown dependency component binding");
                String version = required(dependency, "version");
                if (version.contains("${")) throw new IOException("Unresolved compiled-against dependency version");
                if (!seen.add(target.path("id").asText() + ":" + version)) continue;
                list.appendElement("li").html(CatalogFragments.anchor(target.path("link").asText(), target.path("name").asText()) + " – " + CatalogFragments.text(version));
            }
        }
        return list.children().isEmpty() ? "" : list.outerHtml();
    }

    /** Binds exact release-note versions to canonical component announcements and retains raw notes as provenance. */
    private static void notes(ObjectNode catalog, ObjectNode batch) throws IOException {
        for (JsonNode binding : rows(batch, "release_notes")) {
            ObjectNode module = index(catalog, "modules").get(required(binding, "module_id"));
            if (module == null) throw new IOException("Release notes need an explicit canonical module_id");
            JsonNode observation = binding.path("observation");
            ObjectNode announcement = announcement(catalog, module, required(observation, "version"), JSON.objectNode());
            if (announcement != null) { CatalogFragments.notes(announcement, observation); provenance(announcement, observation); }
            else provenance(module, binding);
        }
    }

    /** Retains existing contributor arrays and adds only exact canonical profile matches. */
    private static void contributors(ObjectNode target, JsonNode observations, Map<String, ObjectNode> people, boolean release) throws IOException {
        ArrayNode contributors = CatalogFragments.array(target, "contributors");
        for (JsonNode observation : observations) {
            ObjectNode person = person(people, observation, observation.path("source").asText().equals("git-email"));
            if (person == null || !person.has("link")) continue;
            String href = required(person, "link");
            String field = release ? "href" : "link";
            boolean present = false;
            for (JsonNode previous : contributors) if (previous.path(field).asText().replaceAll("/+$", "").equals(href.replaceAll("/+$", ""))) present = true;
            if (!present) contributors.add(release ? JSON.objectNode().put("href", href).put("title", required(person, "name")) : personView(person));
        }
    }

    /** Resolves person references by exact accounts or previously trusted email ownership, never name/noreply hints. */
    private static ObjectNode person(Map<String, ObjectNode> people, JsonNode selector, boolean emailOnly) throws IOException {
        ObjectNode found = null;
        for (ObjectNode person : people.values()) {
            boolean matches = !emailOnly && (selector.path("identity_id").asText().equals(person.path("id").asText()) || selector.path("id").asText().equals(person.path("id").asText()));
            if (!emailOnly) for (String field : List.of("github_id", "jboss_username", "username")) {
                if (selector.hasNonNull(field) && person.path(field).asText().equalsIgnoreCase(selector.path(field).asText())) matches = true;
            }
            if (selector.hasNonNull("email")) {
                String email = selector.path("email").asText().toLowerCase(Locale.ROOT);
                if (person.path("email").asText().equalsIgnoreCase(email) || contains(person.path("emails"), email)) matches = true;
            }
            if (matches && found != null && found != person) throw new IOException("Ambiguous exact person reference");
            if (matches) found = person;
        }
        return found;
    }

    /** Produces the structured person view already consumed by generated module pages. */
    private static ObjectNode personView(ObjectNode person) throws IOException {
        return JSON.objectNode().put("name", required(person, "name")).put("link", required(person, "link")).put("avatar_url", person.path("avatar_url").asText());
    }

    /** Updates explicitly bound guide history and credits while preserving routes, chapters and menu ownership. */
    private static void guides(ObjectNode catalog, ObjectNode batch, ObjectNode mappings, Map<String, ObjectNode> people) throws IOException {
        Map<String, ObjectNode> bindings = mappingIndex(mappings, "guide_bindings", "source_path");
        Map<String, ObjectNode> guides = index(catalog, "guides");
        for (JsonNode observation : rows(batch, "guide_history")) {
            JsonNode binding = bindings.get(required(observation, "source_path"));
            if (binding == null) throw new IOException("Guide history needs guide_bindings source_path/id mapping: " + observation.path("source_path").asText());
            ObjectNode guide = guides.get(required(binding, "id"));
            if (guide == null) throw new IOException("Unknown guide binding needs complete create seed");
            List<ObjectNode> authors = new ArrayList<>();
            for (ObjectNode person : people.values()) if (contains(person.path("guide_authorships"), guide.path("id").asText())) authors.add(person);
            ObjectNode combined = observation.deepCopy();
            Map<String, JsonNode> changes = new TreeMap<>();
            for (JsonNode change : rows(guide, "changes")) changes.put(required(change, "commit"), change.deepCopy());
            for (JsonNode change : rows(observation, "changes")) changes.put(required(change, "commit"), change.deepCopy());
            ArrayNode history = combined.putArray("changes");
            changes.values().stream().sorted(Comparator.comparing((JsonNode change) -> change.path("date").asText()).reversed().thenComparing(change -> change.path("commit").asText())).forEach(history::add);
            CatalogFragments.guide(guide, combined, authors);
            guide.put("source_path", observation.path("source_path").asText()).put("revision", observation.path("revision").asText());
            for (String field : List.of("native_path", "history_paths")) if (observation.has(field)) guide.set(field, observation.path(field).deepCopy());
            if (binding.has("chapters")) {
                if (!binding.path("chapters").isArray()) throw new IOException("Guide chapters must be a reviewed array");
                guide.set("chapters", binding.path("chapters").deepCopy());
            }
            provenance(guide, observation);
        }
    }

    /** Keeps every historical event and merges validated current observations under their original source identity. */
    private static void mergeEvent(ObjectNode catalog, JsonNode observation) throws IOException {
        String id = required(observation, "id");
        ObjectNode existing = index(catalog, "events").get(id);
        if (existing == null) CatalogFragments.array(catalog, "events").add(observation.deepCopy());
        else {
            for (String field : List.of("source", "source_id")) if (!existing.path(field).equals(observation.path(field))) throw new IOException("Event source identity change requires review");
            existing.setAll((ObjectNode) observation.deepCopy());
        }
    }

    /** Applies only explicitly reviewed exact candidate envelopes without granting candidate account ownership. */
    private static void reviewed(ObjectNode catalog, ObjectNode batch, ObjectNode mappings) throws IOException {
        Map<String, ObjectNode> people = index(catalog, "identities");
        for (JsonNode candidate : rows(batch, "enrichment_candidates")) {
            boolean approved = false;
            for (JsonNode review : rows(mappings, "reviewed_enrichments")) if (review.equals(candidate)) approved = true;
            if (!approved) continue;
            ObjectNode person = people.get(required(candidate, "identity_id"));
            if (person == null || !(candidate.path("patch") instanceof ObjectNode patch)) throw new IOException("Reviewed enrichment needs exact canonical ID and patch");
            String source = required(candidate, "source");
            validateOptionalSource(person, candidate, patch, source);
            ObjectNode accepted = JSON.objectNode();
            for (Map.Entry<String, JsonNode> field : patch.properties()) {
                String name = field.getKey();
                if (!OPTIONAL_PRESENTATION.contains(name)) {
                    boolean protectedSourceField = switch (source) {
                        case "gravatar" -> GRAVATAR_ACCOUNT_FIELDS.contains(name);
                        case "jbosscommunity" -> Set.of("jboss_username", "jbosscommunity").contains(name);
                        case "confluence" -> Set.of("confluence", "email", "emails").contains(name);
                        default -> false;
                    };
                    if (!protectedSourceField) throw new IOException("Reviewed enrichment cannot change ownership or avatars");
                    continue;
                }
                JsonNode value = field.getValue();
                if (name.equals("urls")) {
                    if (!value.isArray()) throw new IOException("Reviewed profile URLs must be an array");
                    for (JsonNode url : value) CatalogFragments.safeLink(required(url, "value"));
                } else {
                    if (!value.isTextual()) throw new IOException("Reviewed profile value must be text: " + name);
                    if (name.equals("blog") || name.equals("homepage")) CatalogFragments.safeLink(value.asText());
                }
                if (person.hasNonNull(name) || name.equals("bio") && !person.path("bio_html").asText().isBlank()
                        || name.equals("blog") && hasBlogProfile(person)) continue;
                accepted.set(name, value.deepCopy());
            }
            person.setAll(accepted);
            CatalogFragments.profile(person, accepted);
            if (source.equals("jbosscommunity") || source.equals("confluence")) {
                String profileUrl = required(patch.path(source), "profile_url");
                ObjectNode account = person.withObject(source);
                if (!account.hasNonNull("profile_url")) account.put("profile_url", CatalogFragments.safeLink(profileUrl));
            }
            provenance(person, candidate);
        }
    }

    /** Verifies the collector's original account selector before recording its provenance or safe presentation. */
    private static void validateOptionalSource(ObjectNode person, JsonNode candidate, ObjectNode patch, String source) throws IOException {
        switch (source) {
            case "gravatar" -> {
                String hash = person.path("gravatar_id").asText("");
                if (hash.isBlank()) {
                    String email = person.path("email").asText("").toLowerCase(Locale.ROOT);
                    if (email.isBlank()) throw new IOException("Reviewed Gravatar needs an existing trusted email or hash");
                    try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(email.getBytes(StandardCharsets.UTF_8))); }
                    catch (NoSuchAlgorithmException failure) { throw new IOException("Gravatar hash algorithm unavailable", failure); }
                }
                JsonNode payload = candidate.path("source_payload");
                if (!hash.equalsIgnoreCase(required(payload, "hash")) || !candidate.path("source_id").equals(payload.path("id"))
                        || patch.has("gravatar_id") && !hash.equalsIgnoreCase(patch.path("gravatar_id").asText())) {
                    throw new IOException("Reviewed Gravatar selector disagrees with existing identity");
                }
            }
            case "jbosscommunity" -> {
                String trusted = person.path("jboss_username").asText(person.path("jbosscommunity").path("username").asText(""));
                String username = required(candidate, "source_id");
                if (trusted.isBlank() || !trusted.equalsIgnoreCase(username) || !username.equalsIgnoreCase(required(patch, "jboss_username"))
                        || !username.equalsIgnoreCase(required(patch.path("jbosscommunity"), "username"))) {
                    throw new IOException("Reviewed Community username disagrees with existing trusted account");
                }
                String profileUrl = required(patch.path("jbosscommunity"), "profile_url");
                if (!profileUrl.equals(candidate.path("source_url").asText())) throw new IOException("Reviewed Community profile URL disagrees with source");
                URI uri = URI.create(CatalogFragments.safeLink(profileUrl));
                if (!"https".equals(uri.getScheme()) || !"community.jboss.org".equalsIgnoreCase(uri.getHost())
                        || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                        || !uri.getPath().equals("/people/" + username)) throw new IOException("Reviewed Community profile URL is not canonical");
            }
            case "confluence" -> {
                String trusted = person.path("confluence").path("username").asText("");
                String username = required(candidate, "source_id");
                if (trusted.isBlank() || !trusted.equals(username) || !trusted.equals(required(patch.path("confluence"), "username"))) {
                    throw new IOException("Reviewed Confluence username needs an existing trusted account");
                }
                String profileUrl = required(patch.path("confluence"), "profile_url");
                if (!profileUrl.equals(candidate.path("source_url").asText())) throw new IOException("Reviewed Confluence profile URL disagrees with source");
                CatalogFragments.safeLink(profileUrl);
            }
            default -> throw new IOException("Unknown reviewed enrichment source: " + source);
        }
    }

    /** Detects an already published Blog fragment so optional data cannot replace its curated link. */
    private static boolean hasBlogProfile(ObjectNode person) {
        for (JsonNode profile : person.path("profiles")) {
            if (profile.path("label").asText().equals("Blog") && (!profile.path("html").asText().isBlank()
                    || profile.path("links").isArray() && !profile.path("links").isEmpty())) return true;
        }
        return false;
    }

    /** Updates derived announcement links only for components with refreshed release facts. */
    private static void latest(ObjectNode catalog) throws IOException {
        Map<String, List<ObjectNode>> groups = new HashMap<>();
        for (ObjectNode row : index(catalog, "releases").values()) groups.computeIfAbsent(row.path("module_link").asText(), ignored -> new ArrayList<>()).add(row);
        for (List<ObjectNode> group : groups.values()) {
            if (group.stream().noneMatch(row -> row.has("module_id"))) continue;
            group.sort(Comparator.comparing((ObjectNode row) -> row.path("date").asText()).thenComparing(row -> row.path("version").asText()));
            String link = group.getLast().path("link").asText();
            for (ObjectNode row : group) {
                if (!row.path("latest_link").asText().equals(link)) row.put("latest_link", link).put("latest_warning_html", "Since we wrote this post we didn't laze around. " + CatalogFragments.anchor(link, "Check our latest announcement") + ".");
            }
        }
    }

    /** Rejects directory metadata that would fail native rendering before publishing a refreshed snapshot. */
    private static void directories(ObjectNode catalog) throws IOException {
        Set<Integer> moduleRanks = new HashSet<>();
        for (JsonNode module : catalog.path("modules")) {
            JsonNode directory = module.path("directory");
            if (!directory.isObject() || !DIRECTORY_CATEGORIES.contains(required(directory, "category"))
                    || !directory.path("listed").isBoolean()) throw new IOException("Invalid module directory classification");
            if (!directory.path("listed").asBoolean()) continue;
            JsonNode order = directory.path("order");
            if (!order.isIntegralNumber() || !order.canConvertToInt() || order.asInt() < 0 || !moduleRanks.add(order.asInt())
                    || !directory.path("obsolete").isBoolean() || !directory.path("external").isBoolean())
                throw new IOException("Invalid or duplicate listed module directory rank");
            required(directory, "label");
        }
        Set<Integer> cardRanks = new HashSet<>();
        Set<String> modals = new HashSet<>();
        for (JsonNode person : catalog.path("identities")) {
            if (!person.path("directory_cards").isArray()) throw new IOException("Identity requires an explicit directory_cards array");
            for (JsonNode card : person.path("directory_cards")) {
                JsonNode order = card.path("order");
                String modal = card.has("modal_id") ? required(card, "modal_id") : person.path("github_id").asText();
                if (!card.isObject() || !order.isIntegralNumber() || !order.canConvertToInt() || order.asInt() < 0
                        || !cardRanks.add(order.asInt()) || !modal.matches("[A-Za-z0-9_-]+") || !modals.add(modal))
                    throw new IOException("Invalid or duplicate contributor card rank/modal");
                required(card, "avatar_url");
                required(card, "modal_avatar_url");
            }
        }
    }

    /** Retains exact distinct upstream evidence, making repeated assembly idempotent. */
    private static void provenance(ObjectNode row, JsonNode observation) throws IOException {
        ArrayNode evidence = CatalogFragments.array(row, "observations");
        for (JsonNode previous : evidence) if (previous.equals(observation)) return;
        evidence.add(observation.deepCopy());
    }

    /** Validates canonical row IDs and route collisions while retaining their established ordering. */
    private static Map<String, ObjectNode> index(ObjectNode catalog, String domain) throws IOException {
        Map<String, ObjectNode> indexed = new LinkedHashMap<>();
        Set<String> links = new HashSet<>();
        if (!catalog.path(domain).isArray()) throw new IOException("Catalog needs all six domain arrays: " + domain);
        for (JsonNode row : catalog.path(domain)) {
            if (!(row instanceof ObjectNode object) || indexed.putIfAbsent(required(row, "id"), object) != null) throw new IOException("Duplicate or malformed canonical row in " + domain);
            if (!domain.equals("repositories") && row.hasNonNull("link") && !links.add(CatalogFragments.safeLink(row.path("link").asText()))) throw new IOException("Canonical route collision in " + domain);
        }
        return indexed;
    }

    /** Indexes explicit mapping selectors and rejects duplicate classification decisions. */
    private static Map<String, ObjectNode> mappingIndex(ObjectNode mappings, String field, String key) throws IOException {
        Map<String, ObjectNode> indexed = new TreeMap<>();
        for (JsonNode row : rows(mappings, field)) if (indexed.putIfAbsent(required(row, key), (ObjectNode) row) != null) throw new IOException("Duplicate mapping selector: " + field);
        return indexed;
    }

    /** Requires object rows and rejects malformed observation containers before mutation. */
    private static List<JsonNode> rows(JsonNode object, String field) throws IOException {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode row : rowsNode(object, field)) {
            if (!row.isObject()) throw new IOException("Expected object row: " + field);
            rows.add(row);
        }
        return rows;
    }

    /** Treats omitted source arrays as absent observations, never as deletion authority. */
    private static ArrayNode rowsNode(JsonNode object, String field) throws IOException {
        if (!object.has(field)) return JSON.arrayNode();
        if (object.get(field) instanceof ArrayNode array) return array;
        throw new IOException("Expected array: " + field);
    }

    /** Requires an actual nonblank string rather than silently coercing configuration or canonical identity values. */
    static String required(JsonNode object, String field) throws IOException {
        if (!object.path(field).isTextual() || object.path(field).asText().isBlank()) throw new IOException("Missing text field: " + field);
        return object.path(field).asText();
    }

    /** Compares exact trusted collection values without inferring ownership from names. */
    private static boolean contains(JsonNode array, String value) {
        for (JsonNode entry : array) if (entry.isTextual() && entry.asText().equalsIgnoreCase(value)) return true;
        return false;
    }

    /** Separates release calendar dates from publication identity, accepting source instants without changing publication data. */
    private static String date(String value) throws IOException {
        try { return LocalDate.parse(value.substring(0, 10)).toString(); }
        catch (RuntimeException failure) { throw new IOException("Invalid release date", failure); }
    }
}
