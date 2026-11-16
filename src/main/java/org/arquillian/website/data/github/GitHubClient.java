package org.arquillian.website.data.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.arquillian.website.data.CachePolicy;
import org.arquillian.website.data.CachedHttpClient;

/** Reads the GitHub REST resources needed by generated Arquillian pages. */
public final class GitHubClient {

    private final CachedHttpClient http;
    private final ObjectMapper mapper;
    private final Map<String, String> headers;

    /** Creates a GitHub client over the shared HTTP/cache contract. */
    public GitHubClient(CachedHttpClient http) {
        this(http, "");
    }

    /** Creates a GitHub client with an optional bearer token for higher API limits. */
    public GitHubClient(CachedHttpClient http, String token) {
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = new ObjectMapper();
        this.headers = token == null || token.isBlank() ? Map.of("Accept", "application/vnd.github+json")
                : Map.of("Accept", "application/vnd.github+json", "Authorization", "Bearer " + token);
    }

    /** Fetches and maps a GitHub repository collection response. */
    public List<Repository> repositories(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        return parseRepositories(http.fetch(endpoint, policy, headers).entry().body());
    }

    /** Fetches ordered repository pages with distinct cache keys and combines them deterministically. */
    public List<Repository> repositories(List<URI> endpoints, CachePolicy pagePolicy)
            throws IOException, InterruptedException {
        List<Repository> result = new ArrayList<>();
        for (int index = 0; index < endpoints.size(); index++) {
            CachePolicy policy = new CachePolicy(pagePolicy.key() + "-" + index, pagePolicy.maxAge(),
                    pagePolicy.conditionalRequests(), pagePolicy.allowStaleFallback());
            result.addAll(repositories(endpoints.get(index), policy));
        }
        return List.copyOf(result);
    }

    /** Fetches and maps a GitHub contributor collection response. */
    public List<Contributor> contributors(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode values = mapper.readTree(http.fetch(endpoint, policy, headers).entry().body());
        List<Contributor> result = new ArrayList<>();
        values.forEach(value -> result.add(new Contributor(text(value, "login"), integer(value, "contributions"),
                text(value, "html_url"))));
        return List.copyOf(result);
    }

    /** Fetches and maps one GitHub user response. */
    public User user(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode value = mapper.readTree(http.fetch(endpoint, policy, headers).entry().body());
        return new User(text(value, "login"), text(value, "name"), text(value, "company"), text(value, "location"),
                text(value, "bio"), text(value, "blog"), text(value, "html_url"), text(value, "avatar_url"));
    }

    /** Fetches and maps closed GitHub milestones used as release identifiers. */
    public List<Milestone> milestones(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode values = mapper.readTree(http.fetch(endpoint, policy, headers).entry().body());
        List<Milestone> result = new ArrayList<>();
        values.forEach(value -> result.add(new Milestone(integer(value, "number"), text(value, "title"),
                text(value, "description"), text(value, "html_url"))));
        return List.copyOf(result);
    }

    /** Fetches GitHub milestone data under the release-facing name used by page generators. */
    public List<Release> releases(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode values = mapper.readTree(http.fetch(endpoint, policy, headers).entry().body());
        List<Release> result = new ArrayList<>();
        values.forEach(value -> result.add(new Release(integer(value, "number"), text(value, "title"),
                text(value, "description"), text(value, "html_url"))));
        return List.copyOf(result);
    }

    /** Fetches and maps closed GitHub issues associated with a milestone. */
    public List<Issue> issues(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode values = mapper.readTree(http.fetch(endpoint, policy, headers).entry().body());
        List<Issue> result = new ArrayList<>();
        values.forEach(value -> result.add(new Issue(integer(value, "number"), text(value, "title"), text(value, "html_url"),
                text(value.path("user"), "login"), labels(value.path("labels")))));
        return List.copyOf(result);
    }

    /** Fetches and maps GitHub team members. */
    public List<TeamMember> teamMembers(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode values = mapper.readTree(http.fetch(endpoint, policy, headers).entry().body());
        List<TeamMember> result = new ArrayList<>();
        values.forEach(value -> result.add(new TeamMember(text(value, "login"), text(value, "html_url"))));
        return List.copyOf(result);
    }

    /** Normalizes legacy GitHub git protocol URLs to HTTPS clone URLs. */
    public static URI normalizeCloneUrl(URI cloneUrl) {
        Objects.requireNonNull(cloneUrl, "cloneUrl");
        if (!"git".equalsIgnoreCase(cloneUrl.getScheme()) || !"github.com".equalsIgnoreCase(cloneUrl.getHost())) {
            return cloneUrl;
        }
        return URI.create("https://github.com/" + cloneUrl.getPath().replaceFirst("^/", ""));
    }

    /** Maps one repository JSON array into typed repository records. */
    private List<Repository> parseRepositories(String body) throws IOException {
        JsonNode values = mapper.readTree(body);
        List<Repository> result = new ArrayList<>();
        values.forEach(value -> result.add(new Repository(text(value, "name"), text(value, "description"),
                text(value.path("owner"), "login"), text(value, "html_url"), text(value, "git_url"),
                text(value, "clone_url"), text(value, "commits_url"))));
        return List.copyOf(result);
    }

    /** Reads an optional text field without exposing Jackson nodes to page models. */
    private static String text(JsonNode value, String field) {
        JsonNode fieldValue = value.get(field);
        return fieldValue == null || fieldValue.isNull() ? "" : fieldValue.asText();
    }

    /** Reads an optional integer field without exposing Jackson nodes to page models. */
    private static int integer(JsonNode value, String field) {
        JsonNode fieldValue = value.get(field);
        return fieldValue == null || !fieldValue.canConvertToInt() ? 0 : fieldValue.intValue();
    }

    /** Maps GitHub issue label names without exposing provider JSON nodes. */
    private static List<String> labels(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(text(value, "name")));
        return List.copyOf(result);
    }

    /** Describes a repository returned by GitHub. */
    public record Repository(String name, String description, String owner, String htmlUrl, String gitUrl,
            String cloneUrl, String commitsUrl) {
    }

    /** Describes a GitHub contributor. */
    public record Contributor(String login, int contributions, String htmlUrl) {
    }

    /** Describes the profile fields used by identity enrichment. */
    public record User(String login, String name, String company, String location, String bio, String blog,
            String htmlUrl, String avatarUrl) {
    }

    /** Describes a GitHub milestone used as a release source. */
    public record Milestone(int number, String title, String description, String htmlUrl) {
    }

    /** Describes a release identifier sourced from a closed GitHub milestone. */
    public record Release(int number, String title, String description, String htmlUrl) {
    }

    /** Describes a closed GitHub issue. */
    public record Issue(int number, String title, String htmlUrl, String author, List<String> labels) {
    }

    /** Describes a GitHub team member. */
    public record TeamMember(String login, String htmlUrl) {
    }
}
