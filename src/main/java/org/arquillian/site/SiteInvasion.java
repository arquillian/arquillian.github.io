package org.arquillian.site;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Builds invasion views from Roq's single native snapshot, with no clock, files or upstream access. */
@Named("siteInvasion")
@ApplicationScoped
public class SiteInvasion {
    @Inject
    @Named("catalog")
    JsonObject data;

    @Inject
    SiteDirectories directories;

    @Inject
    LegacyMarkup markup;

    /** Uses explicit canonical membership so advisors and other role-bearing profiles cannot silently join the team. */
    public List<JsonObject> team() {
        return identities("team_order", 60).stream().map(view -> {
            JsonObject identity = view.getJsonObject("identity");
            JsonArray links = new JsonArray();
            for (String label : List.of("GitHub", "Twitter")) {
                String href = profile(identity, label);
                if (href != null) links.add(new JsonObject().put("label", label.equals("GitHub") ? "Github" : label).put("href", href));
            }
            if (identity.getString("google_profile_url") != null) {
                links.add(new JsonObject().put("label", "Google+").put("href", safeUrl(identity.getString("google_profile_url"))));
            }
            String biography = directories.biography(identity.getString("bio_html", ""));
            return view.put("github_url", profile(identity, "GitHub")).put("links", links)
                    .put("bio_html", biography).put("has_bio", !biography.isBlank());
        }).toList();
    }

    /** Selects only published roadie membership and validates its canonical Lanyrd account links. */
    public List<JsonObject> roadies() {
        return identities("roadie_order", 48).stream().map(view -> view.put("lanyrd_url",
                safeUrl(view.getJsonObject("identity").getString("lanyrd_profile_url")))).toList();
    }

    /** Counts published contributor membership occurrences through the shared directory view. */
    public int contributorCount() {
        return directories.contributors().size();
    }

    /** Consumes refresh-time upcoming decisions and retains session precision without consulting a moving clock. */
    public List<JsonObject> events() {
        return rows("events").stream().filter(row -> Boolean.TRUE.equals(row.getBoolean("upcoming")))
                .map(this::event).toList();
    }

    /** Extracts editable AsciiDoc regions around dynamic lists, removing only their explicit scaffolding wrappers. */
    public String prose(String html, String region) {
        if (!Set.of("introduction", "rally", "roadies").contains(region)) throw new IllegalArgumentException("Unknown invasion prose region: " + region);
        Document document = Jsoup.parseBodyFragment(markup.render(html));
        document.outputSettings().prettyPrint(false);
        Element block = document.selectFirst("div.openblock." + region + " > div.content");
        if (block == null) throw new IllegalArgumentException("Missing authored invasion prose region: " + region);
        for (Element count : block.select("strong[data-upcoming-count]")) count.removeAttr("data-upcoming-count").text(Integer.toString(events().size()));
        return block.html();
    }

    /** Preserves the catalog's event order and creates escaped-value-only presentation fields on disposable copies. */
    private JsonObject event(JsonObject row) {
        String id = required(row, "id");
        JsonObject view = row.copy().put("detail_url", safeUrl(required(row, "detail_url")))
                .put("title", required(row, "title")).put("event", row.getString("event", ""))
                .put("event_location", row.getString("event_location", ""))
                .put("description", row.getString("description", ""));
        if (row.getString("event_url") != null) view.put("event_url", safeUrl(row.getString("event_url")));
        boolean sessionDate = row.getString("start_datetime") != null || row.getString("start_date") != null;
        String field = List.of("start_datetime", "start_date", "conference_start_datetime", "conference_start_date").stream()
                .filter(name -> row.getString(name) != null).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing session or conference date: " + id));
        try {
            ZoneId zone = row.getString("timezone") == null ? ZoneOffset.UTC : ZoneId.of(row.getString("timezone"));
            ZonedDateTime timed = field.endsWith("_datetime") ? OffsetDateTime.parse(row.getString(field)).atZoneSameInstant(zone) : null;
            LocalDate date = timed == null ? LocalDate.parse(row.getString(field)) : timed.toLocalDate();
            String label = date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy", Locale.US));
            if (field.equals("start_datetime")) {
                label += " - " + timed.format(DateTimeFormatter.ofPattern("hh:mma", Locale.US));
                if (row.getString("end_datetime") != null) {
                    ZonedDateTime end = OffsetDateTime.parse(row.getString("end_datetime")).atZoneSameInstant(zone);
                    if (!end.isAfter(timed)) throw new IllegalArgumentException("Invalid event end: " + id);
                    label += " to " + end.format(DateTimeFormatter.ofPattern("hh:mma", Locale.US));
                }
                if (row.getString("timezone") != null) label += " " + row.getString("timezone");
            } else if (field.equals("start_date") && row.getString("end_date") != null) {
                LocalDate end = LocalDate.parse(row.getString("end_date"));
                if (!end.isAfter(date)) throw new IllegalArgumentException("Invalid event end: " + id);
            }
            view.put("month", date.format(DateTimeFormatter.ofPattern("MMM", Locale.US)))
                    .put("day", sessionDate ? date.format(DateTimeFormatter.ofPattern("dd", Locale.US)) : "")
                    .put("date_label", label).put("session_date", sessionDate);
        } catch (DateTimeException failure) {
            throw new IllegalArgumentException("Invalid event date/time: " + id, failure);
        }
        JsonArray speakers = new JsonArray();
        List<String> names = new ArrayList<>();
        for (Object value : row.getJsonArray("speakers", new JsonArray())) {
            JsonObject speaker = (JsonObject) value;
            String username = required(speaker, "username");
            if (!username.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Unsafe Lanyrd speaker account: " + id);
            String name = required(speaker, "name");
            names.add(name);
            speakers.add(new JsonObject().put("name", name).put("href", "http://lanyrd.com/profile/" + username + "/"));
        }
        String speakerLabel = names.size() < 2 ? String.join("", names)
                : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
        return view.put("speakers", speakers).put("speaker_label", speakerLabel);
    }

    /** Orders curated membership and derives requested avatar sizes from the existing protected canonical avatar URL. */
    private List<JsonObject> identities(String rankField, int size) {
        Set<Integer> ranks = new HashSet<>();
        return rows("identities").stream().filter(identity -> identity.containsKey(rankField))
                .peek(identity -> {
                    Integer rank = identity.getInteger(rankField);
                    if (rank == null || rank < 0 || !ranks.add(rank)) throw new IllegalArgumentException("Invalid or duplicate " + rankField + ": " + identity.getString("id"));
                    if (!required(identity, "github_id").matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Unsafe team modal ID");
                }).sorted(Comparator.comparingInt(identity -> identity.getInteger(rankField)))
                .map(identity -> new JsonObject().put("identity", identity)
                        .put("avatar_url", safeUrl(required(identity, "avatar_url")).replaceFirst("([?&]s=)[0-9]+", "$1" + size)))
                .toList();
    }

    /** Reads current canonical social fields rather than freezing team-only link copies. */
    private static String profile(JsonObject identity, String label) {
        return identity.getJsonArray("profiles").stream().map(JsonObject.class::cast)
                .filter(profile -> label.equals(profile.getString("label")))
                .flatMap(profile -> profile.getJsonArray("links").stream()).map(JsonObject.class::cast)
                .map(link -> safeUrl(link.getString("href"))).findFirst().orElse(null);
    }

    /** Reads the required native catalog domain without maintaining a second authoritative snapshot. */
    private List<JsonObject> rows(String domain) {
        JsonArray rows = data.getJsonArray(domain);
        if (rows == null) throw new IllegalArgumentException("Missing catalog domain: " + domain);
        return rows.stream().map(JsonObject.class::cast).toList();
    }

    /** Rejects incomplete visible metadata so broken refresh mappings cannot create blank titles or identities. */
    private static String required(JsonObject row, String field) {
        String value = row.getString(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing invasion " + field + ": " + row.getString("id"));
        return value;
    }

    /** Limits browser-bound snapshot links to absolute web URLs and rejects control characters before rendering. */
    private static String safeUrl(String value) {
        if (value == null || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Unsafe invasion URL");
        URI uri = URI.create(value);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Unsafe invasion URL: " + value);
        }
        return value;
    }
}
