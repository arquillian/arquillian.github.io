package org.arquillian.site;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;

/** Groups canonical offline module and identity rows for the three native directory pages without I/O. */
@Named("siteDirectories")
@ApplicationScoped
public class SiteDirectories {
    private static final List<Category> CATEGORIES = List.of(
            new Category("bom", "Boms", "star"), new Category("platform", "Platforms", "home"),
            new Category("tool", "Tools", "move"), new Category("daemon", "Daemons", "cog"),
            new Category("container_adapter", "Container Adapters", "retweet"),
            new Category("extension", "Extensions", "road"),
            new Category("test_runner", "Test Runners (other than JUnit and TestNG)", "play-circle"),
            new Category("tool_plugin", "Tool Plugins", "fire"), new Category("shrinkwrap", "Shrinkwraps", "folder-open"),
            new Category("tck", "Tcks", "tint"), new Category("test_reports", "Test Reports", "th-list"));

    @Inject
    @Named("catalog")
    JsonObject data;

    /** Returns published module groups, retaining their category order and separate obsolete section. */
    public List<JsonObject> moduleGroups(boolean obsolete) {
        return groups(obsolete, false);
    }

    /** Selects current modules with documentation while preserving legacy category and vendor-divider behavior. */
    public List<JsonObject> documentationGroups() {
        return groups(false, true);
    }

    /** Expands historical card occurrences by published ordinal and reads current canonical profile fields. */
    public List<JsonObject> contributors() {
        List<JsonObject> result = new ArrayList<>();
        Set<Integer> ranks = new HashSet<>();
        Set<String> modals = new HashSet<>();
        for (JsonObject identity : rows("identities")) {
            JsonArray cards = identity.getJsonArray("directory_cards");
            if (cards == null) throw new IllegalArgumentException("Identity requires curated directory membership: " + identity.getString("id"));
            for (Object value : cards) {
                JsonObject card = (JsonObject) value;
                String id = identity.getString("id");
                Integer rank = card.getInteger("order");
                String modal = card.getString("modal_id", identity.getString("github_id"));
                if (rank == null || rank < 0 || !ranks.add(rank) || modal == null || !modal.matches("[A-Za-z0-9_-]+")
                        || !modals.add(modal)) throw new IllegalArgumentException("Invalid or duplicate directory card order/modal: " + id);
                JsonArray links = new JsonArray();
                for (String label : List.of("Blog", "Twitter", "GitHub")) {
                    String href = identity.getJsonArray("profiles").stream().map(JsonObject.class::cast)
                            .filter(profile -> label.equals(profile.getString("label")))
                            .flatMap(profile -> profile.getJsonArray("links").stream()).map(JsonObject.class::cast)
                            .map(link -> link.getString("href")).findFirst().orElse(null);
                    links.add(new JsonObject().put("title", label).put("href", href));
                }
                String biography = biography(identity.getString("bio_html", ""));
                result.add(new JsonObject().put("identity", identity).put("order", rank).put("modal_id", modal)
                        .put("name", card.getString("name_override", identity.getString("name")))
                        .put("avatar_url", required(card, "avatar_url", id))
                        .put("modal_avatar_url", required(card, "modal_avatar_url", id))
                        .put("bio_html", biography).put("has_bio", !biography.isBlank()).put("links", links));
            }
        }
        return result.stream().sorted(Comparator.comparingInt(row -> row.getInteger("order"))).toList();
    }

    /** Normalizes canonical plain biographies for modals while preserving existing paragraph and rich HTML semantics. */
    public String biography(String biography) {
        if (!biography.isBlank() && Jsoup.parseBodyFragment(biography).body().children().isEmpty()) {
            biography = biography.strip().replace("\r\n", "\n").replace('\r', '\n');
            return "<p>" + biography.replaceAll("\n[\\t ]*\n", "</p>\n<p>") + "</p>";
        }
        return biography;
    }

    /** Builds disposable view rows while rejecting missing classification and keeping source rows unmodified. */
    private List<JsonObject> groups(boolean obsolete, boolean documentation) {
        List<JsonObject> modules = rows("modules");
        Set<Integer> orders = new HashSet<>();
        for (JsonObject module : modules) {
            JsonObject directory = module.getJsonObject("directory");
            String id = module.getString("id");
            if (directory == null || CATEGORIES.stream().noneMatch(category -> category.id().equals(directory.getString("category")))
                    || directory.getBoolean("listed") == null) {
                throw new IllegalArgumentException("Module requires curated directory classification: " + id);
            }
            if (directory.getBoolean("listed")) {
                Integer order = directory.getInteger("order");
                if (order == null || order < 0 || !orders.add(order) || directory.getBoolean("obsolete") == null
                        || directory.getBoolean("external") == null) throw new IllegalArgumentException("Invalid module directory metadata: " + id);
                required(directory, "label", id);
            }
        }
        List<JsonObject> result = new ArrayList<>();
        for (Category category : CATEGORIES) {
            List<JsonObject> selected = modules.stream().filter(module -> {
                JsonObject directory = module.getJsonObject("directory");
                return directory.getBoolean("listed") && category.id().equals(directory.getString("category"))
                        && directory.getBoolean("obsolete") == obsolete;
            }).sorted(Comparator.comparingInt(module -> module.getJsonObject("directory").getInteger("order"))).toList();
            if (selected.isEmpty() || documentation && selected.stream().noneMatch(module -> module.containsKey("documentation"))) continue;
            JsonArray items = new JsonArray();
            String previousVendor = null;
            for (JsonObject module : selected) {
                JsonObject directory = module.getJsonObject("directory");
                String vendor = module.getString("family");
                JsonArray history = module.getJsonArray("release_history");
                JsonObject docs = module.getJsonObject("documentation");
                items.add(new JsonObject().put("label", directory.getString("label"))
                        .put("href", documentation ? docs == null ? null : docs.getString("href") : module.getString("link"))
                        .put("visible", !documentation || docs != null)
                        .put("divider", previousVendor != null && vendor != null && !Objects.equals(previousVendor, vendor))
                        .put("version", history.isEmpty() ? null : history.getJsonObject(0).getString("version"))
                        .put("external", directory.getBoolean("external")));
                previousVendor = vendor;
            }
            result.add(new JsonObject().put("title", (obsolete ? "Obsolete " : "") + category.title())
                    .put("icon", "icon-" + category.icon()).put("items", items));
        }
        return List.copyOf(result);
    }

    /** Reads required native data domains without opening files or creating another catalog snapshot. */
    private List<JsonObject> rows(String domain) {
        JsonArray rows = data.getJsonArray(domain);
        if (rows == null) throw new IllegalArgumentException("Missing catalog domain: " + domain);
        return rows.stream().map(JsonObject.class::cast).toList();
    }

    /** Explains incomplete curated presentation metadata rather than producing blank links or avatars. */
    private static String required(JsonObject row, String field, String id) {
        String value = row.getString(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing directory " + field + ": " + id);
        return value;
    }

    /** Keeps the authored category title and Bootstrap icon beside their explicit curated key. */
    private record Category(String id, String title, String icon) {
    }
}
