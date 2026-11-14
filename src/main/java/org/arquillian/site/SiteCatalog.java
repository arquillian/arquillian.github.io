package org.arquillian.site;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Provides domain lookups over Roq's native offline catalog without loading a second copy. */
@Named("siteCatalog")
@ApplicationScoped
public class SiteCatalog {

    @Inject
    @Named("catalog")
    JsonObject catalog;

    /** Finds a historical row by its stable catalog ID or public path and explains missing data. */
    public JsonObject find(String domain, String key) {
        Objects.requireNonNull(key, "Catalog lookup key must not be null");
        return rows(domain)
                .filter(row -> key.equals(row.getString("id")) || key.equals(row.getString("link")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing catalog row: " + domain + "/" + key));
    }

    /** Orders announcements by publication date and captured same-day rank, with stable ID fallback. */
    public List<JsonObject> latestReleases(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Release limit must not be negative: " + limit);
        }
        return rows("releases")
                .sorted(Comparator.comparing((JsonObject row) -> row.getString("published_date", row.getString("date"))).reversed()
                        .thenComparingInt(row -> row.getInteger("published_order", Integer.MAX_VALUE))
                        .thenComparing(row -> row.getString("id")))
                .limit(limit)
                .toList();
    }

    /** Returns canonical guide menu rows in the published group order without listing translations twice. */
    public List<JsonObject> guidesInGroup(int group) {
        return rows("guides")
                .filter(row -> Objects.equals(row.getInteger("menu_group"), group))
                .sorted(Comparator.comparingInt((JsonObject row) -> row.getInteger("menu_order"))
                        .thenComparing(row -> row.getString("id")))
                .toList();
    }

    /** Reads a required native data domain and fails clearly when a template names an unknown one. */
    private Stream<JsonObject> rows(String domain) {
        JsonArray rows = catalog.getJsonArray(domain);
        if (rows == null) {
            throw new IllegalArgumentException("Unknown catalog domain: " + domain);
        }
        return IntStream.range(0, rows.size()).mapToObj(rows::getJsonObject);
    }
}
