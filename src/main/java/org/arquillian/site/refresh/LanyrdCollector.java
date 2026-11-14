package org.arquillian.site.refresh;

import static org.arquillian.site.refresh.DataHttpClient.encode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Collects optional historical Lanyrd HTML only on explicit invocation, preserving failures for the refresh coordinator. */
public final class LanyrdCollector {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final DataHttpClient client;
    private final Clock clock;

    /** Shares an explicitly trusted origin and clock without opening connections or registering startup callbacks. */
    public LanyrdCollector(DataHttpClient client, Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Searches current and next year, follows validated pagination and returns only future session/conference dates. */
    public ArrayNode sessions(String query) throws IOException {
        encode(query);
        ArrayNode result = JSON.arrayNode();
        Map<String, String> sessionIds = new HashMap<>();
        int year = LocalDate.now(clock).getYear();
        for (int searchYear : new int[] {year, year + 1}) {
            URI first = client.endpoint("search/?type=session&q=" + encode(query) + "&year=" + searchYear);
            ArrayDeque<URI> pending = new ArrayDeque<>();
            Set<URI> visited = new HashSet<>();
            pending.add(first);
            while (!pending.isEmpty()) {
                URI page = pending.removeFirst();
                if (!visited.add(page)) continue;
                if (visited.size() > 1000) throw new IOException("Excessive Lanyrd pagination");
                String html = client.text(page);
                Document document = Jsoup.parse(html);
                List<Element> summaries = searchResults(document);
                for (Element link : document.select("[class*=pagination] a[href]")) {
                    URI next = resolve(page, link.attr("href"));
                    pagination(first, next);
                    if (!visited.contains(next) && !pending.contains(next)) pending.addLast(next);
                }
                for (Element summary : summaries) {
                    ObjectNode row = session(summary, page, html);
                    String prior = sessionIds.putIfAbsent(row.path("id").asText(), row.path("source_url").asText());
                    if (prior != null && !prior.equals(row.path("source_url").asText())) {
                        throw new IOException("Lanyrd session ID collision requires explicit review");
                    }
                    if (future(row) && prior == null) result.add(row);
                }
            }
        }
        return result;
    }

    /** Requires parseable list rows or explicit zero-result evidence so source errors cannot masquerade as empty refreshes. */
    private static List<Element> searchResults(Document document) throws IOException {
        List<Element> rows = document.select("li.s-session");
        List<Element> empty = document.select(".no-results");
        if (!document.select(".s-session:not(li), input[type=password], .error, [role=alert]").isEmpty()
                || rows.isEmpty() == empty.isEmpty()) throw new IOException("Unrecognized or contradictory Lanyrd search results");
        for (Element message : empty) {
            if (!message.text().matches("(?i)(?:no (?:matching )?(?:sessions|results)(?: found)?|0 (?:sessions|results))\\.?")) {
                throw new IOException("Missing explicit Lanyrd zero-result evidence");
            }
        }
        for (Element row : rows) {
            if (row.parent() == null || !Set.of("ul", "ol").contains(row.parent().normalName())) {
                throw new IOException("Unsupported Lanyrd session result structure");
            }
        }
        ArrayDeque<Element> containers = new ArrayDeque<>(document.select(".search-results, #search-results, .sessions"));
        while (!containers.isEmpty()) {
            Element container = containers.removeFirst();
            if (!container.ownText().isBlank()) throw new IOException("Unparsed Lanyrd result content");
            for (Element child : container.children()) {
                if (child.is("li.s-session, .no-results, [class*=pagination]")) continue;
                if (Set.of("ul", "ol").contains(child.normalName()) || child.is(".search-results, #search-results, .sessions")) {
                    containers.addLast(child);
                } else {
                    throw new IOException("Unsupported Lanyrd result content");
                }
            }
        }
        return rows;
    }

    /** Extracts one detail page using legacy selectors while keeping the complete source HTML for review. */
    private ObjectNode session(Element summary, URI page, String searchHtml) throws IOException {
        Element title = summary.selectFirst("h3 a[href]");
        if (title == null || title.text().isBlank()) throw new IOException("Missing Lanyrd session link/title");
        URI detail = resolve(page, title.attr("href"));
        client.validateOrigin(detail);
        if (detail.getQuery() != null || detail.getPath() == null) throw new IOException("Invalid Lanyrd session URL");
        String[] segments = detail.getPath().split("/");
        if (segments.length < 2 || !segments[segments.length - 1].matches("[A-Za-z0-9_-]+")) throw new IOException("Invalid Lanyrd session slug");
        String slug = segments[segments.length - 1];
        String html = client.text(detail);
        Document document = Jsoup.parse(html);
        Element meta = document.selectFirst(".session-meta");
        Element conference = document.selectFirst("[class*=conference]");
        if (meta == null || !document.select("input[type=password]").isEmpty()) throw new IOException("Unrecognized Lanyrd session detail");
        String timezone = textAttribute(document.selectFirst("abbr.timezone"), "title");
        ObjectNode row = JSON.objectNode().put("id", "lanyrd:session:" + slug).put("source", "lanyrd")
                .put("source_id", slug).put("source_url", detail.toString()).put("detail_url", detail.toString())
                .put("slug", slug).put("uid", "lanyrd:session:" + slug + "@arquillian.org")
                .put("title", title.text()).put("updated", clock.instant().toString());
        if (!timezone.isBlank()) row.put("timezone", timezone);
        Moment start = moment(meta.selectFirst("abbr.dtstart"), timezone);
        Moment end = moment(meta.selectFirst("abbr.dtend"), timezone);
        Moment conferenceStart = moment(conference == null ? null : conference.selectFirst("p.date abbr.dtstart"), timezone);
        if (start == null && conferenceStart == null) throw new IOException("Missing Lanyrd session/conference date");
        if (end != null && (start == null || start.dateOnly() != end.dateOnly() || !end.instant().isAfter(start.instant()))) {
            throw new IOException("Invalid Lanyrd session end time");
        }
        if (start != null) {
            row.put("date_only", start.dateOnly());
            row.put(start.dateOnly() ? "start_date" : "start_datetime", start.value());
        }
        if (end != null) row.put(end.dateOnly() ? "end_date" : "end_datetime", end.value());
        if (conferenceStart != null) row.put(conferenceStart.dateOnly() ? "conference_start_date" : "conference_start_datetime", conferenceStart.value());
        Element description = document.selectFirst("[class*=description]");
        if (description != null) row.put("description", description.text());
        Element location = document.selectFirst("p.location");
        if (location != null) {
            List<String> parts = java.util.Arrays.stream(location.text().split("/")).map(String::strip).toList();
            row.put("event_location", String.join(", ", parts.reversed()));
        }
        Map<String, String> speakers = new LinkedHashMap<>();
        for (Element metadata : summary.select("p[class*=meta]")) {
            Element label = metadata.selectFirst("strong");
            if (label == null) continue;
            if (label.text().equals("Event")) {
                Element link = metadata.selectFirst("a[href]");
                if (link == null || link.text().isBlank()) throw new IOException("Missing Lanyrd conference link");
                URI event = resolve(page, link.attr("href"));
                safeLink(event);
                row.put("event", link.text()).put("event_url", event.toString());
            } else if (label.text().equals("Speakers")) {
                for (Element link : metadata.select("a[href]")) speaker(speakers, link, page, link.text());
            }
        }
        for (Element user : document.select("ul[class*=user-list] li")) {
            Element link = user.selectFirst("a[href]");
            Element name = user.selectFirst("span[class*=user-name]");
            if (link == null) throw new IOException("Malformed Lanyrd speaker");
            speaker(speakers, link, detail, name == null ? link.text() : name.text());
        }
        ArrayNode names = row.putArray("speaker_names");
        ArrayNode accounts = row.putArray("speakers");
        speakers.forEach((username, name) -> {
            names.add(name);
            accounts.addObject().put("username", username).put("name", name);
        });
        row.putObject("source_payload").put("search_url", page.toString()).put("search_html", searchHtml).put("detail_html", html);
        return row;
    }

    /** Keeps sessions or conference-only listings that are upcoming in the event's date or instant semantics. */
    private boolean future(JsonNode row) throws IOException {
        ZoneId zone = row.has("timezone") ? ZoneId.of(row.path("timezone").asText()) : ZoneOffset.UTC;
        for (String field : new String[] {"start_datetime", "conference_start_datetime", "start_date", "conference_start_date"}) {
            if (!row.has(field)) continue;
            if (field.endsWith("_date")) {
                if (!LocalDate.parse(row.path(field).asText()).isBefore(LocalDate.now(clock.withZone(zone)))) return true;
            } else if (!Instant.parse(row.path(field).asText()).isBefore(clock.instant())) {
                return true;
            }
        }
        return false;
    }

    /** Parses dates without inventing times and rejects nonexistent or ambiguous local times without an explicit offset. */
    private static Moment moment(Element element, String timezone) throws IOException {
        if (element == null) return null;
        String value = element.attr("title").strip();
        try {
            ZoneId zone = timezone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timezone);
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(value);
                return new Moment(date.toString(), date.atStartOfDay(zone).toInstant(), true);
            }
            String normalized = value.replace(' ', 'T');
            if (normalized.matches(".*(?:Z|[+-]\\d{2}:\\d{2})$")) {
                OffsetDateTime date = OffsetDateTime.parse(normalized);
                if (!timezone.isBlank() && !zone.getRules().getOffset(date.toInstant()).equals(date.getOffset())) {
                    throw new DateTimeException("Offset and timezone disagree");
                }
                return new Moment(date.toInstant().toString(), date.toInstant(), false);
            }
            if (timezone.isBlank()) throw new DateTimeException("Local time needs a timezone");
            LocalDateTime date = LocalDateTime.parse(normalized);
            List<ZoneOffset> offsets = zone.getRules().getValidOffsets(date);
            if (offsets.size() != 1) throw new DateTimeException("Ambiguous or nonexistent local time");
            Instant instant = date.toInstant(offsets.getFirst());
            return new Moment(instant.toString(), instant, false);
        } catch (DateTimeException exception) {
            throw new IOException("Malformed or ambiguous Lanyrd date/time");
        }
    }

    /** Retains temporal precision and comparison semantics without silently changing a date-only listing into a timed event. */
    private record Moment(String value, Instant instant, boolean dateOnly) { }

    /** Validates each discovered page against the same search and origin, rejecting changed years and arbitrary URLs. */
    private void pagination(URI first, URI next) throws IOException {
        client.validateOrigin(next);
        Map<String, String> expected = query(first);
        Map<String, String> actual = query(next);
        if (!Objects.equals(first.getPath(), next.getPath()) || !actual.keySet().stream().allMatch(Set.of("type", "q", "year", "page")::contains)
                || !expected.entrySet().stream().allMatch(entry -> entry.getValue().equals(actual.get(entry.getKey())))
                || actual.containsKey("page") && !actual.get("page").matches("[1-9][0-9]{0,2}|1000")) {
            throw new IOException("Unsafe Lanyrd pagination target");
        }
    }

    /** Decodes query parameters once and rejects duplicates that could disguise a different search target. */
    private static Map<String, String> query(URI uri) throws IOException {
        Map<String, String> result = new HashMap<>();
        if (uri.getRawQuery() == null) return result;
        try {
            for (String pair : uri.getRawQuery().split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2 || result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8)) != null) throw new IOException("Malformed pagination query");
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed pagination query");
        }
        return result;
    }

    /** Preserves source usernames as speaker observations without selecting or merging canonical people. */
    private void speaker(Map<String, String> speakers, Element link, URI base, String name) throws IOException {
        URI uri = resolve(base, link.attr("href"));
        client.validateOrigin(uri);
        if (uri.getPath() == null || !uri.getPath().matches("/profile/[^/]+/?") || uri.getQuery() != null || name.isBlank()) {
            throw new IOException("Malformed Lanyrd speaker");
        }
        String username = uri.getPath().replaceFirst("^/profile/", "").replaceFirst("/$", "");
        speakers.putIfAbsent(username, name.strip());
    }

    /** Allows published external conference links while rejecting credentials and executable schemes. */
    private static void safeLink(URI uri) throws IOException {
        if (uri.getHost() == null || uri.getUserInfo() != null || !Set.of("https", "http").contains(uri.getScheme())) {
            throw new IOException("Unsafe Lanyrd conference link");
        }
    }

    /** Reads optional attributes consistently so missing HTML nodes retain their original absence. */
    private static String textAttribute(Element element, String attribute) {
        return element == null ? "" : element.attr(attribute).strip();
    }

    /** Resolves untrusted links without putting their contents into exceptions. */
    private static URI resolve(URI base, String link) throws IOException {
        try {
            return base.resolve(link).normalize();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed Lanyrd link");
        }
    }
}
