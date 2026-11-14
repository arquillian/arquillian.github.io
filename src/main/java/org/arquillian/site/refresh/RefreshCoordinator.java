package org.arquillian.site.refresh;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/** Explicitly stages all requested collection, normalization and renderability checks before one atomic catalog publication. */
public final class RefreshCoordinator {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final RefreshConfig config;
    private final Clock clock;

    /** Retains validated settings and an injected refresh clock without collecting data or starting a framework. */
    public RefreshCoordinator(RefreshConfig config, Clock clock) {
        this.config = java.util.Objects.requireNonNull(config); this.clock = java.util.Objects.requireNonNull(clock);
    }

    /** Collects and validates exactly the same candidate for preview and publication; dry runs never write the catalog. */
    public ObjectNode refresh(boolean dryRun) throws IOException {
        SnapshotStore store = new SnapshotStore(config.catalog());
        ObjectNode existing = store.read();
        ObjectNode settings = config.data();
        ObjectNode mappings = settings.path("mappings") instanceof ObjectNode value ? value.deepCopy() : JSON.objectNode();
        ArrayNode overrides = CatalogFragments.array(mappings, "identity_overrides");
        overrides.addAll(manual(config.manual("manual_identities")));
        overrides.addAll(manual(config.manual("github_mapping")));
        ObjectNode batch = JSON.objectNode();
        for (String name : List.of("repositories", "artifact_modules", "tag_releases", "release_notes", "identity_observations", "guide_history", "events", "enrichment_candidates", "source_completeness")) batch.putArray(name);
        if (settings.has("github")) github(batch, settings.path("github"));
        if (settings.has("jira")) jira(batch, settings.path("jira"));
        if (settings.has("guides")) guides(batch, settings.path("guides"), mappings);
        optional(batch, settings, existing);
        ObjectNode candidate = new CatalogAssembler().assemble(existing, batch, mappings);
        eventState(candidate);
        ObjectNode preview = store.preview(candidate);
        return dryRun ? preview : store.merge(preview);
    }

    /** Reads only authoritative manual YAML arrays, never credential files or a second snapshot store. */
    private static ArrayNode manual(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        JsonNode value = mapper.readTree(Files.readString(path));
        if (!(value instanceof ArrayNode array)) throw new IOException("Manual identity configuration must be an array");
        return array;
    }

    /** Collects organization/explicit repository metadata, Git facts, profiles, roles and exact component notes. */
    private void github(ObjectNode batch, JsonNode options) throws IOException {
        GitHubCollector collector = new GitHubCollector(config.client("github"));
        Map<String, ObjectNode> repositories = new LinkedHashMap<>();
        Map<String, JsonNode> configured = new HashMap<>();
        for (JsonNode row : options.path("repositories")) {
            if (configured.putIfAbsent(CatalogAssembler.required(row, "repository"), row) != null) throw new IOException("Duplicate repository refresh request");
        }
        for (JsonNode organization : options.path("organizations")) {
            ArrayNode rows = collector.repositories(organization.asText());
            complete(batch, "github:organization:" + organization.asText(), rows.size(), 1);
            for (JsonNode row : rows) putRepository(repositories, (ObjectNode) row);
        }
        for (String name : configured.keySet().stream().sorted().toList()) putRepository(repositories, collector.repository(name));
        complete(batch, "github:repositories", repositories.size(), options.path("minimum_repositories").asInt(configured.size()));
        RepositoryCollector gitCollector = new RepositoryCollector();
        for (ObjectNode repository : repositories.values()) {
            String locator = CatalogAssembler.required(repository, "full_name");
            JsonNode source = configured.getOrDefault(locator, JSON.objectNode());
            String originText = source.has("origin") ? CatalogAssembler.required(source, "origin") : CatalogAssembler.required(repository, "clone_url");
            URI origin;
            try { origin = URI.create(originText); }
            catch (IllegalArgumentException failure) { throw new IOException("Invalid Git repository origin"); }
            String numeric = repository.path("source_id").asText();
            if (!numeric.matches("[1-9][0-9]*")) throw new IOException("GitHub repository needs a numeric source ID");
            try (Git git = gitCollector.open(origin, config.cache().resolve("github-" + numeric + ".git"))) {
                String revision = fetchedRevision(git.getRepository(), origin, source.path("tracking_ref").asText());
                ObjectNode collected = gitCollector.collect(git.getRepository(), repository, revision);
                append(batch, "repositories", collected.path("repositories"));
                append(batch, "artifact_modules", collected.path("modules"));
                append(batch, "tag_releases", collected.path("releases"));
                append(batch, "identity_observations", collected.path("identities"));
                complete(batch, "git:" + numeric, collected.path("modules").size(), source.path("minimum_modules").asInt(1));
            } catch (IllegalArgumentException failure) { throw new IOException("Invalid Git source configuration"); }
            if (!source.has("contributors") || source.path("contributors").asBoolean()) {
                ArrayNode profiles = collector.contributors(locator);
                for (JsonNode profile : profiles) ((ObjectNode) profile).put("repository", repository.path("id").asText());
                append(batch, "identity_observations", profiles);
                complete(batch, "github:contributors:" + numeric, profiles.size(), 0);
            }
        }
        for (JsonNode login : options.path("users")) {
            ((ArrayNode) batch.path("identity_observations")).add(collector.user(login.asText()));
            complete(batch, "github:user:" + login.asText(), 1, 1);
        }
        for (JsonNode team : options.path("teams")) {
            ArrayNode members = collector.teamMembers(CatalogAssembler.required(team, "id"));
            for (JsonNode member : members) {
                ((ArrayNode) batch.path("identity_observations")).add(member);
                attribution(batch, "team:" + team.path("id").asText() + ":" + member.path("source_id").asText(), "team", "github_id", member.path("github_id").asText(), "team", CatalogAssembler.required(team, "team"));
            }
            complete(batch, "github:team:" + team.path("id").asText(), members.size(), 0);
        }
        for (JsonNode source : options.path("release_sources")) {
            ArrayNode notes = collector.releases(CatalogAssembler.required(source, "repository"), source.has("prefix") ? CatalogAssembler.required(source, "prefix") : null);
            bindNotes(batch, notes, CatalogAssembler.required(source, "module_id"));
            complete(batch, "github:notes:" + source.path("repository").asText(), notes.size(), 0);
        }
    }

    /** Rejects conflicting numeric identities even when multiple explicit organization requests overlap. */
    private static void putRepository(Map<String, ObjectNode> repositories, ObjectNode row) throws IOException {
        String name = CatalogAssembler.required(row, "full_name");
        ObjectNode previous = repositories.putIfAbsent(name, row);
        if (previous != null && !previous.path("source_id").equals(row.path("source_id"))) throw new IOException("Repository locator claims conflicting numeric IDs");
    }

    /** Resolves freshly advertised remote HEAD against fetched tracking refs, never stale bare-cache HEAD. */
    private static String fetchedRevision(Repository repository, URI origin, String tracking) throws IOException {
        try {
            Collection<Ref> advertised = Git.lsRemoteRepository().setRemote(origin.toString()).setTimeout(60).call();
            Ref remoteHead = advertised.stream().filter(ref -> ref.getName().equals("HEAD")).findFirst().orElse(null);
            String branch = null;
            if (!tracking.isBlank()) {
                if (!tracking.matches("refs/remotes/origin/[A-Za-z0-9_./-]+") || tracking.contains("..") || tracking.endsWith("/HEAD")) throw new IOException("tracking_ref must name a fetched origin branch");
                branch = "refs/heads/" + tracking.substring("refs/remotes/origin/".length());
            } else if (remoteHead != null && remoteHead.isSymbolic()) branch = remoteHead.getTarget().getName();
            else if (remoteHead != null) {
                for (Ref ref : advertised) if (ref.getName().startsWith("refs/heads/") && remoteHead.getObjectId().equals(ref.getObjectId())) {
                    if (branch != null) throw new IOException("Remote default branch is ambiguous; configure tracking_ref");
                    branch = ref.getName();
                }
            }
            if (branch == null || !branch.startsWith("refs/heads/")) throw new IOException("Remote default branch is unavailable; configure tracking_ref");
            String selected = branch;
            Ref remote = advertised.stream().filter(ref -> ref.getName().equals(selected)).findFirst().orElse(null);
            String fetched = "refs/remotes/origin/" + branch.substring("refs/heads/".length());
            Ref local = repository.exactRef(fetched);
            if (remote == null || local == null || !remote.getObjectId().equals(local.getObjectId())) throw new IOException("Remote branch changed after fetch; rerun refresh");
            return local.getObjectId().name();
        } catch (GitAPIException failure) { throw new IOException("Unable to advertise the remote default branch", failure); }
    }

    /** Collects only explicitly bound Jira projects and converts component leads into exact attribution observations. */
    private void jira(ObjectNode batch, JsonNode options) throws IOException {
        JiraCollector collector = new JiraCollector(config.client("jira"));
        for (JsonNode source : options.path("release_sources")) {
            ArrayNode notes = collector.releases(CatalogAssembler.required(source, "project"), source.has("prefix") ? CatalogAssembler.required(source, "prefix") : null);
            bindNotes(batch, notes, CatalogAssembler.required(source, "module_id"));
            complete(batch, "jira:notes:" + source.path("project").asText(), notes.size(), 0);
        }
        for (JsonNode source : options.path("component_sources")) {
            ArrayNode leads = collector.componentLeads(CatalogAssembler.required(source, "project"));
            if (!source.path("components").isObject()) throw new IOException("Jira component_sources needs exact components mapping");
            for (JsonNode lead : leads) {
                String key = CatalogAssembler.required(lead, "component");
                String component = CatalogAssembler.required(source.path("components"), key);
                attribution(batch, lead.path("id").asText(), "component-lead", "jboss_username", CatalogAssembler.required(lead, "jboss_username"), "component", component);
            }
            complete(batch, "jira:leads:" + source.path("project").asText(), leads.size(), 0);
        }
    }

    /** Unions explicit historical/native guide paths at current HEAD, retaining deleted-path history and exact author credits. */
    private void guides(ObjectNode batch, JsonNode options, ObjectNode mappings) throws IOException {
        try (Repository repository = new FileRepositoryBuilder().findGitDir(config.siteRepository().toFile()).build()) {
            int limit = options.path("limit").asInt(10);
            String revision = RepositoryCollector.commit(repository, "HEAD").name();
            Set<String> prefixes = new TreeSet<>(); prefixes.add(CatalogAssembler.required(options, "prefix"));
            Set<String> boundPaths = new HashSet<>();
            for (JsonNode binding : mappings.path("guide_bindings")) {
                boundPaths.add(RepositoryCollector.path(CatalogAssembler.required(binding, "source_path")));
                if (binding.has("native_path")) {
                    String nativePath = RepositoryCollector.path(CatalogAssembler.required(binding, "native_path"));
                    boundPaths.add(nativePath);
                    Path parent = Path.of(nativePath).getParent(); prefixes.add(parent == null ? "" : parent.toString());
                }
            }
            Map<String, JsonNode> sources = new TreeMap<>();
            for (String prefix : prefixes) for (JsonNode source : new GuideHistoryCollector().collect(repository, revision, prefix, limit)) {
                String path = CatalogAssembler.required(source, "source_path");
                if (!boundPaths.contains(path)) throw new IOException("Guide history needs an explicit old/native source_path binding: " + path);
                sources.put(path, source);
            }
            int count = 0;
            for (JsonNode binding : mappings.path("guide_bindings")) {
                String originalPath = RepositoryCollector.path(CatalogAssembler.required(binding, "source_path"));
                String nativePath = binding.has("native_path") ? RepositoryCollector.path(CatalogAssembler.required(binding, "native_path")) : originalPath;
                JsonNode metadata = sources.containsKey(nativePath) ? sources.get(nativePath) : sources.get(originalPath);
                if (metadata == null) throw new IOException("Neither historical nor native guide path exists at current HEAD");
                ObjectNode guide = metadata.deepCopy();
                guide.put("id", "git:guide:" + originalPath).put("source", "site-git").put("source_id", originalPath).put("source_path", originalPath).put("revision", revision);
                if (binding.has("native_path")) guide.put("native_path", nativePath);
                ArrayNode paths = guide.putArray("history_paths");
                ArrayNode provenance = guide.putArray("source_observations");
                Map<String, JsonNode> changes = new TreeMap<>();
                for (String path : new java.util.LinkedHashSet<>(List.of(originalPath, nativePath))) {
                    paths.add(path);
                    if (sources.containsKey(path)) { provenance.add(sources.get(path)); guideCredits(batch, sources.get(path), CatalogAssembler.required(binding, "id")); }
                    for (JsonNode change : RepositoryCollector.history(repository, revision, null, path, limit)) changes.put(CatalogAssembler.required(change, "commit"), change);
                }
                ArrayNode history = guide.putArray("changes");
                changes.values().stream().sorted(Comparator.comparing((JsonNode change) -> change.path("date").asText()).reversed().thenComparing(change -> change.path("commit").asText())).forEach(history::add);
                ((ArrayNode) batch.path("guide_history")).add(guide);
                count++;
            }
            complete(batch, "site:HEAD:guides", count, options.path("minimum").asInt(1));
        }
    }

    /** Converts legacy singular and native plural account credits into the same exact canonical attribution contract. */
    private static void guideCredits(ObjectNode batch, JsonNode guide, String canonical) throws IOException {
        for (String key : List.of("author", "authors", "translator", "translators")) {
            if (!guide.hasNonNull(key)) continue;
            JsonNode credit = guide.path(key);
            List<JsonNode> accounts = credit.isArray() ? credit.valueStream().toList() : List.of(credit);
            for (JsonNode value : accounts) {
                if (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_-]+")) throw new IOException("Guide author needs an exact account attribution");
                String account = value.asText();
                boolean translator = key.startsWith("translator");
                ObjectNode attribution = attribution(batch, guide.path("source_path").asText() + ":" + key + ":" + account, translator ? "guide-translator" : "guide-author", "github_id", account, "guide", canonical);
                if (translator) attribution.put("language", guide.path("language").asText());
            }
        }
    }

    /** Collects retired services only when enabled; all enabled-source failures remain fatal before publication. */
    private void optional(ObjectNode batch, ObjectNode options, ObjectNode existing) throws IOException {
        if (options.path("lanyrd").path("enabled").asBoolean()) {
            ArrayNode events = new LanyrdCollector(config.client("lanyrd"), clock).sessions(CatalogAssembler.required(options.path("lanyrd"), "query"));
            append(batch, "events", events);
            complete(batch, "lanyrd", events.size(), options.path("lanyrd").path("minimum").asInt());
        }
        for (String service : List.of("gravatar", "community")) {
            if (!options.path(service).path("enabled").asBoolean()) continue;
            IdentityCollector collector = new IdentityCollector(config.client(service));
            int count = 0;
            for (JsonNode id : options.path(service).path("identity_ids")) {
                JsonNode person = identity(existing, id.asText());
                ObjectNode candidate = service.equals("gravatar") ? collector.gravatar(person, Map.of()) : collector.community(person);
                if (!candidate.isEmpty()) { ((ArrayNode) batch.path("enrichment_candidates")).add(candidate); count++; }
            }
            complete(batch, service, count, 0);
        }
        if (options.path("confluence").path("enabled").asBoolean()) {
            IdentityCollector collector = new IdentityCollector(config.client("confluence"));
            for (JsonNode selected : options.path("confluence").path("profiles")) {
                String id = CatalogAssembler.required(selected, "identity_id"); identity(existing, id);
                ((ArrayNode) batch.path("enrichment_candidates")).add(collector.confluenceProfile(id, CatalogAssembler.required(selected, "username"), URI.create(CatalogAssembler.required(selected, "profile_url"))));
            }
            complete(batch, "confluence", options.path("confluence").path("profiles").size(), 0);
        }
    }

    /** Requires exact canonical optional-profile selection rather than a search suggestion or display name. */
    private static JsonNode identity(ObjectNode existing, String id) throws IOException {
        for (JsonNode row : existing.path("identities")) if (row.path("id").asText().equals(id)) return row;
        throw new IOException("Optional enrichment requires an existing canonical identity_id");
    }

    /** Stores event flags from the session start when present, falling back to conference dates without render-time clocks. */
    private void eventState(ObjectNode candidate) throws IOException {
        for (JsonNode value : candidate.path("events")) {
            ObjectNode event = (ObjectNode) value;
            try {
                ZoneId zone = event.has("timezone") ? ZoneId.of(event.path("timezone").asText()) : ZoneOffset.UTC;
                String startDate = event.path("start_date").asText("");
                String startTime = event.path("start_datetime").asText("");
                if (startDate.isBlank() && startTime.isBlank()) {
                    startDate = event.path("conference_start_date").asText("");
                    startTime = event.path("conference_start_datetime").asText("");
                }
                boolean upcoming;
                boolean active;
                if (startTime.isBlank()) {
                    LocalDate today = LocalDate.now(clock.withZone(zone));
                    LocalDate start = LocalDate.parse(startDate);
                    LocalDate end = LocalDate.parse(event.path("end_date").asText(startDate));
                    upcoming = !start.isBefore(today); active = !end.isBefore(today);
                } else {
                    Instant start = Instant.parse(startTime);
                    Instant end = Instant.parse(event.path("end_datetime").asText(startTime));
                    upcoming = !start.isBefore(clock.instant()); active = !end.isBefore(clock.instant());
                }
                event.put("upcoming", upcoming).put("active", active);
            } catch (DateTimeException failure) { throw new IOException("Invalid historical event date; review event before refresh"); }
        }
    }

    /** Creates exact attribution rows accepted by the existing normalizer. */
    private static ObjectNode attribution(ObjectNode batch, String key, String kind, String selector, String account, String field, String value) {
        return ((ArrayNode) batch.path("identity_observations")).addObject().put("id", "attribution:" + key).put("source", "attribution").put("source_id", key)
                .put("kind", kind).put(selector, account).put(field, value);
    }

    /** Keeps raw note observations distinct from renderable announcement rows. */
    private static void bindNotes(ObjectNode batch, ArrayNode notes, String module) {
        for (JsonNode note : notes) ((ArrayNode) batch.path("release_notes")).addObject().put("module_id", module).set("observation", note);
    }

    /** Records successful full-source collection only after the collector has returned every page/ref. */
    private static void complete(ObjectNode batch, String id, int count, int minimum) throws IOException {
        if (minimum < 0 || count < minimum) throw new IOException("Requested source is unexpectedly empty or incomplete: " + id);
        ((ArrayNode) batch.path("source_completeness")).addObject().put("id", id).put("count", count).put("minimum", minimum).put("complete", true);
    }

    /** Copies collector-owned arrays into the in-memory observation batch before any publication step. */
    private static void append(ObjectNode batch, String field, JsonNode rows) throws IOException {
        if (!(rows instanceof ArrayNode array)) throw new IOException("Collector returned a malformed source array");
        ((ArrayNode) batch.path(field)).addAll(array.deepCopy());
    }
}
