package org.arquillian.site;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Derives the Universe generator's ordered form directly from the canonical offline catalog. */
@Named("siteGenerator")
@ApplicationScoped
public class SiteGenerator {
    @Inject
    SiteCatalog catalog;

    @Inject
    @Named("catalog")
    JsonObject data;

    /** Reads fresh raw extensions and the existing latest release without caching or mutating snapshot data. */
    public JsonObject model() {
        JsonObject universe = catalog.find("modules", "/modules/arquillian-universe-bom-bom/");
        Object history = universe.getValue("release_history");
        if (!(history instanceof JsonArray releases) || releases.isEmpty()) {
            throw new IllegalArgumentException("Universe generator requires nonempty release_history");
        }
        String version = required(object(releases.getValue(0), "latest Universe release"), "version");
        if (!(universe.getValue("extensions") instanceof JsonArray extensions)) {
            throw new IllegalArgumentException("Universe generator requires an extensions array");
        }
        Map<String, JsonArray> groups = new LinkedHashMap<>();
        Set<String> identifiers = new HashSet<>();
        for (Object value : extensions) {
            JsonObject extension = object(value, "Universe extension");
            String artifactId = coordinate(extension, "artifactId");
            String groupId = coordinate(extension, "groupId");
            String name = required(extension, "name");
            if (!identifiers.add(artifactId)) {
                throw new IllegalArgumentException("Duplicate Universe extension artifactId: " + artifactId);
            }
            String group = group(artifactId);
            if (group.equals("CORE")) continue;
            JsonObject control = new JsonObject().put("name", name).put("groupId", groupId).put("artifactId", artifactId);
            String url = moduleLink(artifactId);
            control.put("url", url == null ? "" : url);
            groups.computeIfAbsent(group, ignored -> new JsonArray()).add(control);
        }
        JsonArray rows = new JsonArray();
        groups.forEach((name, controls) -> rows.add(new JsonObject().put("name", name).put("extensions", controls)));
        return new JsonObject().put("version", version).put("groups", rows);
    }

    /** Matches the legacy ordered rules so mixed-purpose modules remain in their published categories. */
    private static String group(String artifactId) {
        if (artifactId.matches(".*(junit|testng|cukes).*")) return "Test Framework";
        if (artifactId.contains("core")) return "CORE";
        if (artifactId.contains("spring")) return "Spring";
        if (artifactId.matches(".*(reporter|recorder).*")) return "Reporting";
        if (artifactId.matches(".*(drone|warp|graphene|rest).*")) return "Front-end";
        if (artifactId.matches(".*(persistence|transaction|byteman).*")) return "Back-end";
        if (artifactId.matches(".*(chameleon|containerless).*")) return "Containers";
        if (artifactId.contains("cube")) return "System";
        if (artifactId.contains("governor")) return "Issue Tracking";
        if (artifactId.contains("jacoco")) return "Quality Tools";
        return "Other";
    }

    /** Resolves exact legacy repository identities and rejects ambiguous links instead of choosing an arbitrary module. */
    private String moduleLink(String artifactId) {
        String link = null;
        for (Object value : data.getJsonArray("modules")) {
            JsonObject module = object(value, "catalog module");
            String repositoryId = module.getString("repository_id");
            if (repositoryId == null) continue;
            JsonObject repository = catalog.find("repositories", repositoryId);
            if (!required(repository, "path").replace("extension-", "").replace("container-", "").equals(artifactId)) continue;
            if (link != null) {
                throw new IllegalArgumentException("Ambiguous generator module link: " + artifactId);
            }
            String route = required(module, "link");
            if (!route.matches("/modules/[A-Za-z0-9_-][A-Za-z0-9._-]*/")) {
                throw new IllegalArgumentException("Unsafe generator module link: " + route);
            }
            link = route + "index.html";
        }
        return link;
    }

    /** Rejects malformed snapshot rows with repairable context before Qute attempts to render them. */
    private static JsonObject object(Object value, String context) {
        if (!(value instanceof JsonObject row)) throw new IllegalArgumentException("Invalid " + context + " object");
        return row;
    }

    /** Requires raw textual metadata while retaining punctuation for escaped labels and version values. */
    private static String required(JsonObject row, String field) {
        if (!(row.getValue(field) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Missing or invalid Universe generator " + field);
        }
        return value;
    }

    /** Validates Maven identifiers before they become form IDs and generated dependency coordinates. */
    private static String coordinate(JsonObject row, String field) {
        String value = required(row, field);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException("Malformed Universe generator " + field + ": " + value);
        }
        return value;
    }
}
