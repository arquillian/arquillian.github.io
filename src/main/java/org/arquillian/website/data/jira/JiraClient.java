package org.arquillian.website.data.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.arquillian.website.data.CachePolicy;
import org.arquillian.website.data.CachedHttpClient;

/** Reads only the supported JSON portion of the legacy JIRA provider contract. */
public final class JiraClient {

    private final CachedHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Creates a JIRA client over the shared HTTP/cache contract. */
    public JiraClient(CachedHttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    /** Fetches project versions without attempting to scrape obsolete release-note HTML. */
    public Project project(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode value = mapper.readTree(http.fetch(endpoint, policy).entry().body());
        List<Version> versions = new ArrayList<>();
        value.path("versions").forEach(version -> versions.add(new Version(text(version, "id"), text(version, "name"),
                version.path("released").asBoolean(false), text(version, "description"))));
        return new Project(text(value, "key"), text(value, "name"), List.copyOf(versions));
    }

    /** Fetches component leads from a supported JSON endpoint. */
    public List<Component> components(URI endpoint, CachePolicy policy) throws IOException, InterruptedException {
        JsonNode values = mapper.readTree(http.fetch(endpoint, policy).entry().body());
        List<Component> result = new ArrayList<>();
        values.forEach(value -> result.add(new Component(text(value, "id"), text(value, "name"),
                text(value.path("lead"), "displayName"), text(value.path("lead"), "name"))));
        return List.copyOf(result);
    }

    /** Reads an optional JSON text field as an empty string when absent. */
    private static String text(JsonNode value, String field) {
        JsonNode fieldValue = value.get(field);
        return fieldValue == null || fieldValue.isNull() ? "" : fieldValue.asText();
    }

    /** Describes a JIRA project and its versions. */
    public record Project(String key, String name, List<Version> versions) {
    }

    /** Describes a JIRA release version. */
    public record Version(String id, String name, boolean released, String description) {
    }

    /** Describes a JIRA component lead. */
    public record Component(String id, String name, String leadDisplayName, String leadUsername) {
    }
}
