package org.arquillian.site;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Serializes public ancillary formats directly from native metadata and the shared rendered article body. */
@Named("siteExports")
@ApplicationScoped
public class SiteExports {
    /** Keeps the published RiCal identity and the byte-for-byte empty legacy calendar. */
    private static final String CALENDAR_HEADER = "BEGIN:VCALENDAR\nPRODID;X-RICAL-TZSOURCE=TZINFO:-//com.denhaven2/NONSGML ri_cal gem//EN\nCALSCALE:GREGORIAN\nVERSION:2.0\n";
    /** Emits RFC 5545 UTC timestamps independently of the machine locale or timezone. */
    private static final DateTimeFormatter CALENDAR_TIME = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    /** Reads authoritative project configuration through Roq's native YAML data bean. */
    @Inject
    @Named("siteData")
    JsonObject data;
    /** Reads only checked-in event snapshots, including refresh-time eligibility flags. */
    @Inject
    @Named("catalog")
    JsonObject catalog;
    /** Reuses publication ordering and the actual article fragment instead of copying post data. */
    @Inject
    SiteBlog blog;

    /** Returns a well-formed Atom feed retaining historical public IDs, date offsets, categories and content. */
    public String atom() {
        JsonObject project = data.getJsonObject("project");
        String base = required(project, "legacyBaseUrl");
        List<JsonObject> posts = blog.latest(50);
        StringWriter output = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("feed");
            xml.writeDefaultNamespace("http://www.w3.org/2005/Atom");
            xml.writeAttribute("xml", "http://www.w3.org/XML/1998/namespace", "lang", "en-US");
            text(xml, "id", base + "/");
            text(xml, "title", required(project, "title"));
            xml.writeStartElement("author");
            text(xml, "name", required(project, "title"));
            xml.writeEndElement();
            text(xml, "updated", posts.isEmpty() ? "1970-01-01T00:00:00+00:00" : updated(posts.getFirst()));
            link(xml, "self", "application/atom+xml", base + "/blog/atom.xml");
            link(xml, "alternate", "text/html", base + "/blog");
            for (JsonObject post : posts) {
                xml.writeStartElement("entry");
                String path = required(post, "link");
                text(xml, "id", base + path);
                text(xml, "title", required(post, "title"));
                text(xml, "updated", updated(post));
                link(xml, "alternate", "text/html", base + path);
                for (Object value : post.getJsonArray("tags", new JsonArray())) {
                    category(xml, required((JsonObject) value, "title"), base + "/blog/tags");
                }
                category(xml, "arquillian", base + "/blog");
                String body = blog.body(path);
                text(xml, "summary", summary(body));
                xml.writeStartElement("content");
                xml.writeAttribute("type", "html");
                xml.writeCharacters(validXml(qualified(body, base)));
                xml.writeEndElement();
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
            return output.toString();
        } catch (XMLStreamException failure) {
            throw new IllegalStateException("Cannot serialize Atom feed", failure);
        }
    }

    /** Ports the legacy literal-space budget, separating adjacent blocks and decoding entities once for safe XML text. */
    static String summary(String body) {
        String text = body.replaceAll("(?i)</(p|h[1-6]|div|section|ul|ol|li|table|tr|td|th|dl|dt|dd|blockquote|pre)>(?=<)", "</$1>\n")
                .replaceAll("<[^>]+>", "").replace("&nbsp;", " ");
        String[] words = text.split(" ");
        return org.jsoup.parser.Parser.unescapeEntities(String.join(" ", Arrays.copyOf(words, Math.min(100, words.length))), false) + "......";
    }

    /** Qualifies root asset/link paths while preserving existing absolute URLs and the article's literal text. */
    private static String qualified(String body, String base) {
        Document document = Jsoup.parseBodyFragment(body);
        document.outputSettings().prettyPrint(false);
        for (Element element : document.select("[href],[src]")) {
            for (String attribute : List.of("href", "src")) {
                String value = element.attr(attribute);
                if (value.startsWith("/") && !value.startsWith("//")) element.attr(attribute, base + value);
            }
        }
        return document.body().html();
    }

    /** Applies the legacy one-day feed offset to editable publication metadata without changing the public path. */
    private static String updated(JsonObject post) {
        return LocalDate.parse(required(post, "date")).plusDays(1) + "T00:00:00+00:00";
    }

    /** Writes text using the XML serializer so markup and ampersands are escaped exactly once. */
    private static void text(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(validXml(value));
        xml.writeEndElement();
    }

    /** Rejects disallowed XML characters instead of emitting a feed that an XML parser cannot read. */
    private static String validXml(String value) {
        if (value.codePoints().anyMatch(point -> point != 9 && point != 10 && point != 13
                && (point < 0x20 || point >= 0xD800 && point <= 0xDFFF || point > 0xFFFD && point < 0x10000))) {
            throw new IllegalArgumentException("Invalid XML character in Atom feed");
        }
        return value;
    }

    /** Writes an Atom link with deterministic attributes and no manual XML interpolation. */
    private static void link(XMLStreamWriter xml, String relation, String type, String href) throws XMLStreamException {
        xml.writeEmptyElement("link");
        xml.writeAttribute("rel", relation);
        xml.writeAttribute("type", type);
        xml.writeAttribute("href", validXml(href));
    }

    /** Retains category order and repeated occurrences together with their historical scheme. */
    private static void category(XMLStreamWriter xml, String term, String scheme) throws XMLStreamException {
        xml.writeEmptyElement("category");
        xml.writeAttribute("term", validXml(term));
        xml.writeAttribute("scheme", validXml(scheme));
    }

    /** Returns the exact empty legacy calendar or deterministic RFC 5545 events selected by stored upcoming flags. */
    public String calendar() {
        List<String> lines = new ArrayList<>(CALENDAR_HEADER.lines().toList());
        for (Object value : catalog.getJsonArray("events", new JsonArray())) {
            JsonObject event = (JsonObject) value;
            if (!event.getBoolean("upcoming", false) || event.getString("start_datetime") == null && event.getString("start_date") == null) continue;
            lines.add("BEGIN:VEVENT");
            lines.add("UID:" + escape(event.getString("uid", required(event, "id"))));
            String updated = timestamp(required(event, "updated"));
            lines.add("DTSTAMP:" + updated);
            dates(lines, event);
            lines.add("SUMMARY:" + escape(required(event, "title")));
            lines.add("DESCRIPTION:" + escape(event.getString("description", "")));
            lines.add("CATEGORIES:SESSION");
            lines.add("URL:" + uri(required(event, "detail_url")));
            lines.add("LOCATION:" + escape(event.getString("event", "")));
            lines.add("SEQUENCE:0");
            lines.add("CLASS:PUBLIC");
            lines.add("CREATED:" + updated);
            lines.add("LAST-MODIFIED:" + updated);
            if (event.getString("event_url") != null) lines.add("ORGANIZER;CN=" + parameter(event.getString("event", "")) + ":" + uri(event.getString("event_url")));
            for (Object speaker : event.getJsonArray("speakers", new JsonArray())) {
                JsonObject person = (JsonObject) speaker;
                String name = required(person, "name");
                lines.add("ATTENDEE;CN=" + parameter(name) + ":urn:arquillian:speaker:"
                        + URLEncoder.encode(person.getString("username", name), StandardCharsets.UTF_8));
            }
            lines.add("END:VEVENT");
        }
        lines.add("END:VCALENDAR");
        return lines.size() == 5 ? CALENDAR_HEADER + "END:VCALENDAR\n" : String.join("\r\n", lines.stream().map(SiteExports::fold).toList()) + "\r\n";
    }

    /** Keeps date-only events distinct and converts inclusive source end days to exclusive calendar ends. */
    private static void dates(List<String> lines, JsonObject event) {
        try {
            if (event.getString("start_date") != null) {
                if (event.getString("start_datetime") != null || event.getString("end_datetime") != null) throw new IllegalArgumentException("Mixed calendar date/time fields");
                LocalDate start = LocalDate.parse(required(event, "start_date"));
                LocalDate end = LocalDate.parse(event.getString("end_date", start.toString()));
                if (end.isBefore(start)) throw new IllegalArgumentException("Calendar end precedes start");
                lines.add("DTSTART;VALUE=DATE:" + DateTimeFormatter.BASIC_ISO_DATE.format(start));
                lines.add("DTEND;VALUE=DATE:" + DateTimeFormatter.BASIC_ISO_DATE.format(end.plusDays(1)));
            } else {
                if (event.getString("end_date") != null) throw new IllegalArgumentException("Mixed calendar date/time fields");
                Instant start = OffsetDateTime.parse(required(event, "start_datetime")).toInstant();
                lines.add("DTSTART:" + CALENDAR_TIME.format(start));
                if (event.getString("end_datetime") != null) {
                    Instant end = OffsetDateTime.parse(event.getString("end_datetime")).toInstant();
                    if (!end.isAfter(start)) throw new IllegalArgumentException("Calendar end must follow start");
                    lines.add("DTEND:" + CALENDAR_TIME.format(end));
                }
            }
        } catch (DateTimeException failure) {
            throw new IllegalArgumentException("Invalid calendar date/time for " + event.getString("id"), failure);
        }
    }

    /** Formats a stored update instant without consulting the rendering clock. */
    private static String timestamp(String value) {
        try {
            return CALENDAR_TIME.format(OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeException failure) {
            throw new IllegalArgumentException("Invalid calendar updated timestamp", failure);
        }
    }

    /** Rejects forbidden controls, then escapes RFC 5545 TEXT without losing valid tabs or line breaks. */
    private static String escape(String value) {
        return validCalendarText(value).replace("\\", "\\\\").replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,");
    }

    /** Rejects forbidden controls, then encodes quoted parameter text using RFC 6868. */
    private static String parameter(String value) {
        return "\"" + validCalendarText(value).replace("^", "^^").replace("\r\n", "\n").replace('\r', '\n').replace("\n", "^n").replace("\"", "^'") + "\"";
    }

    /** Keeps C0 controls and DEL out of calendar lines while allowing TAB and escaped CR/LF text. */
    private static String validCalendarText(String value) {
        if (value.codePoints().anyMatch(point -> point < 0x20 && point != '\t' && point != '\n' && point != '\r' || point == 0x7f)) {
            throw new IllegalArgumentException("Invalid calendar text control character");
        }
        return value;
    }

    /** Rejects relative, malformed or control-bearing calendar URIs instead of silently publishing injection. */
    private static String uri(String value) {
        if (value.codePoints().anyMatch(Character::isISOControl) || !URI.create(value).isAbsolute()) throw new IllegalArgumentException("Invalid calendar URI");
        return value;
    }

    /** Folds at 75 UTF-8 octets including the continuation space without splitting a Unicode code point. */
    private static String fold(String value) {
        StringBuilder result = new StringBuilder();
        int octets = 0;
        for (int index = 0; index < value.length();) {
            int point = value.codePointAt(index);
            String character = Character.toString(point);
            int width = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + width > 75) {
                result.append("\r\n ");
                octets = 1;
            }
            result.append(character);
            octets += width;
            index += Character.charCount(point);
        }
        return result.toString();
    }

    /** Renders only the twelve public legacy API keys in their published order, deriving project links once. */
    public String properties() {
        JsonObject project = data.getJsonObject("project");
        JsonObject api = project.getJsonObject("legacyApi");
        String base = required(project, "legacyBaseUrl");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("nodePath", required(api, "nodePath"));
        values.put("projectName", required(project, "title"));
        values.put("homePage", base);
        values.put("license", required(project, "license"));
        values.put("description", required(project, "description"));
        values.put("docsLink", base + "/guides");
        for (String key : List.of("knowledgeBaseLink", "userForumLink", "devForumLink")) values.put(key, required(api, key));
        values.put("blogLink", base + "/blog");
        for (String key : List.of("githubLink", "issueTrackerLink")) values.put(key, required(api, key));
        return String.join("\n", values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList()) + "\n";
    }

    /** Fails clearly when a required snapshot/configuration string is absent or blank. */
    private static String required(JsonObject value, String key) {
        String text = value.getString(key);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing export field: " + key);
        return text;
    }
}
