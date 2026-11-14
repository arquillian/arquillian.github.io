package org.arquillian.site.refresh;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses strict explicit-refresh configuration and resolves only named environment credentials without fetching sources. */
public final class RefreshConfig {
    private final ObjectNode data;
    private final Path directory;
    private final Map<String, String> tokens;

    /** Retains private configuration copies so callers cannot alter a validated refresh after construction. */
    private RefreshConfig(ObjectNode data, Path directory, Map<String, String> tokens) {
        this.data = data.deepCopy(); this.directory = directory; this.tokens = Map.copyOf(tokens);
    }

    /** Loads JSON or YAML, rejecting unknown keys, raw tokens, unsafe service origins and missing named credentials. */
    public static RefreshConfig load(Path configuration, Map<String, String> environment) throws IOException {
        Path path = configuration.toAbsolutePath().normalize();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode parsed;
        try { parsed = mapper.readTree(Files.readString(path)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IOException("Malformed refresh configuration"); }
        if (!(parsed instanceof ObjectNode object)) throw new IOException("Refresh configuration must be an object");
        keys(object, Set.of("catalog", "cache", "site_repository", "manual_identities", "github_mapping", "mappings", "github", "jira", "guides", "lanyrd", "gravatar", "community", "confluence"));
        for (String field : List.of("catalog", "cache", "site_repository")) CatalogAssembler.required(object, field);
        Map<String, String> tokens = new HashMap<>();
        for (String service : List.of("github", "jira", "lanyrd", "gravatar", "community", "confluence")) {
            if (!object.has(service)) continue;
            JsonNode options = object.path(service);
            Set<String> allowed = switch (service) {
                case "github" -> Set.of("base_uri", "token_env", "organizations", "repositories", "users", "teams", "release_sources", "minimum_repositories");
                case "jira" -> Set.of("base_uri", "token_env", "release_sources", "component_sources");
                case "lanyrd" -> Set.of("enabled", "base_uri", "token_env", "query", "minimum");
                case "confluence" -> Set.of("enabled", "base_uri", "token_env", "profiles");
                default -> Set.of("enabled", "base_uri", "token_env", "identity_ids");
            };
            keys(options, allowed);
            boolean optional = !List.of("github", "jira").contains(service);
            if (options.has("enabled") && !options.path("enabled").isBoolean()) throw new IOException("Service enabled must be boolean");
            if (optional && !options.path("enabled").asBoolean()) {
                if (options.has("token_env")) throw new IOException("Disabled service must not request credentials");
                continue;
            }
            String token = null;
            if (options.has("token_env")) {
                String name = CatalogAssembler.required(options, "token_env");
                if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IOException("Invalid token environment-variable name");
                token = environment.get(name);
                if (token == null || token.isBlank()) throw new IOException("Missing named token environment variable: " + name);
                tokens.put(service, token);
            }
            try { new DataHttpClient(URI.create(CatalogAssembler.required(options, "base_uri")), token); }
            catch (IllegalArgumentException failure) { throw new IOException("Invalid service origin or token: " + service); }
            if (service.equals("github")) {
                strings(options, "organizations"); strings(options, "users");
                objects(options, "repositories", Set.of("repository", "origin", "tracking_ref", "minimum_modules", "contributors"), List.of("repository"));
                objects(options, "teams", Set.of("id", "team"), List.of("id", "team"));
                objects(options, "release_sources", Set.of("repository", "module_id", "prefix"), List.of("repository", "module_id"));
            } else if (service.equals("jira")) {
                objects(options, "release_sources", Set.of("project", "module_id", "prefix"), List.of("project", "module_id"));
                objects(options, "component_sources", Set.of("project", "components"), List.of("project"));
            } else if (service.equals("confluence")) {
                objects(options, "profiles", Set.of("identity_id", "username", "profile_url"), List.of("identity_id", "username", "profile_url"));
            } else if (!service.equals("lanyrd")) strings(options, "identity_ids");
            if (service.equals("lanyrd")) CatalogAssembler.required(options, "query");
        }
        if (object.has("guides")) {
            keys(object.path("guides"), Set.of("prefix", "limit", "minimum"));
            RepositoryCollector.path(CatalogAssembler.required(object.path("guides"), "prefix"));
            int limit = object.path("guides").path("limit").asInt(10);
            if (limit < 1 || limit > 1000) throw new IOException("Guide limit must be between 1 and 1000");
        }
        if (object.has("mappings")) validateMappings(object.path("mappings"));
        RefreshConfig config = new RefreshConfig(object, path.getParent(), tokens);
        if (config.cache().equals(config.siteRepository()) || config.cache().startsWith(config.siteRepository().resolve(".git"))) throw new IOException("Git cache must be dedicated and outside the source Git directory");
        return config;
    }

    /** Checks the public mapping seam without duplicating the domain assembler's renderability validation. */
    private static void validateMappings(JsonNode mappings) throws IOException {
        keys(mappings, Set.of("repository_bindings", "components", "announcements", "identity_overrides", "identity_profiles", "guide_bindings", "reviewed_enrichments", "dependency_bindings"));
        objects(mappings, "repository_bindings", Set.of("repository", "id", "github_repository_id", "create"), List.of("repository", "id"));
        objects(mappings, "components", Set.of("id", "repository_id", "relative_path", "artifacts", "announcement_policy", "create"), List.of("id"));
        for (JsonNode component : mappings.path("components")) {
            objects(component, "artifacts", Set.of("groupId", "artifactId"), List.of("groupId", "artifactId"));
            if (component.has("announcement_policy")) {
                JsonNode policy = component.path("announcement_policy");
                keys(policy, Set.of("owners", "since", "slug_prefix", "author", "author_id", "publication_date", "comment_origin", "comment_id", "tags"));
                strings(policy, "owners"); strings(policy, "tags");
                for (String field : List.of("since", "slug_prefix", "publication_date", "comment_origin", "comment_id")) CatalogAssembler.required(policy, field);
            }
        }
        objects(mappings, "announcements", Set.of("id", "module_id", "version", "create"), List.of("id", "module_id", "version"));
        objects(mappings, "identity_profiles", Set.of("id", "create"), List.of("id"));
        objects(mappings, "guide_bindings", Set.of("id", "source_path", "native_path", "chapters", "create"), List.of("id", "source_path"));
        objects(mappings, "dependency_bindings", Set.of("groupId", "artifactId", "module_id"), List.of("groupId", "artifactId", "module_id"));
        for (String field : List.of("identity_overrides", "reviewed_enrichments")) {
            if (mappings.has(field) && !mappings.path(field).isArray()) throw new IOException("Mapping field must be an array: " + field);
        }
    }

    /** Returns a private copy of source/mapping settings; it never includes resolved tokens. */
    ObjectNode data() { return data.deepCopy(); }

    /** Returns the authoritative six-domain snapshot path selected explicitly by the operator. */
    public Path catalog() throws IOException { return path("catalog", null); }

    /** Returns the dedicated bare Git cache root. */
    public Path cache() throws IOException { return path("cache", null); }

    /** Returns the current site's repository whose HEAD alone supplies guide history. */
    public Path siteRepository() throws IOException { return path("site_repository", null); }

    /** Resolves checked-in manual YAML paths relative to the configuration file, with current-site defaults. */
    Path manual(String field) throws IOException {
        return data.has(field) ? path(field, null) : siteRepository().resolve("_config/" + (field.equals("manual_identities") ? "identities.yml" : "github_mapping.yml"));
    }

    /** Creates one origin-protected collector client without resolving any additional environment values. */
    DataHttpClient client(String service) throws IOException {
        try { return new DataHttpClient(URI.create(CatalogAssembler.required(data.path(service), "base_uri")), tokens.get(service)); }
        catch (IllegalArgumentException failure) { throw new IOException("Invalid service origin: " + service); }
    }

    /** Resolves normalized local paths without changing a source checkout or creating directories. */
    private Path path(String field, String fallback) throws IOException {
        String value = data.has(field) ? CatalogAssembler.required(data, field) : fallback;
        if (value == null) throw new IOException("Missing path: " + field);
        return directory.resolve(value).toAbsolutePath().normalize();
    }

    /** Rejects misspelled configuration and raw-token fields rather than silently ignoring operator intent. */
    private static void keys(JsonNode object, Set<String> allowed) throws IOException {
        if (!object.isObject()) throw new IOException("Configuration section must be an object");
        for (Map.Entry<String, JsonNode> entry : object.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!allowed.contains(key)) throw new IOException("Unknown refresh configuration key: " + key);
            if ((key.equals("minimum") || key.startsWith("minimum_") || key.equals("limit"))
                    && (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0)) throw new IOException("Expected nonnegative integer option: " + key);
            if (List.of("contributors", "enabled").contains(key) && !value.isBoolean()) throw new IOException("Expected boolean option: " + key);
        }
    }

    /** Validates arrays of object options and required textual selectors. */
    private static void objects(JsonNode object, String field, Set<String> allowed, List<String> required) throws IOException {
        if (!object.has(field)) return;
        if (!object.path(field).isArray()) throw new IOException("Configuration field must be an array: " + field);
        for (JsonNode row : object.path(field)) {
            keys(row, allowed);
            for (String key : required) CatalogAssembler.required(row, key);
        }
    }

    /** Validates explicit string lists without coercing scalar configuration into unsupported requests. */
    private static void strings(JsonNode object, String field) throws IOException {
        if (!object.has(field)) return;
        if (!object.path(field).isArray()) throw new IOException("Configuration field must be an array: " + field);
        for (JsonNode row : object.path(field)) if (!row.isTextual() || row.asText().isBlank()) throw new IOException("Expected nonblank configuration string: " + field);
    }
}
