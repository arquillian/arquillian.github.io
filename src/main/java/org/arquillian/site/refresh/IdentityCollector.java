package org.arquillian.site.refresh;

import static org.arquillian.site.refresh.DataHttpClient.encode;
import static org.arquillian.site.refresh.DataHttpClient.required;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Collects optional profile suggestions and canonical-bound patches without merging people or writing snapshots. */
public final class IdentityCollector {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Set<String> ACCOUNT_FIELDS = Set.of("twitter", "github", "facebook", "linkedin", "google",
            "flickr", "youtube", "vimeo", "stackoverflow", "wordpress", "tumblr", "blogger", "lanyrd");
    private final DataHttpClient client;

    /** Uses an explicitly configured origin and caller-supplied bearer token, never legacy credential files. */
    public IdentityCollector(DataHttpClient client) {
        this.client = Objects.requireNonNull(client);
    }

    /** Returns every name-search suggestion, retaining original usernames without asserting account ownership. */
    public ArrayNode confluenceCandidates(String query) throws IOException {
        String[] words = Objects.requireNonNull(query).strip().toLowerCase(Locale.ROOT).split("\\s+");
        String search = words.length > 1 ? words[0] + " " + words[words.length - 1] : words[0];
        URI uri = client.endpoint("rest/prototype/1/search/user?query=" + encode(search));
        JsonNode payload = client.json(uri);
        JsonNode users = payload.path("result");
        if (!payload.isObject() || !payload.path("totalSize").canConvertToInt() || !payload.path("totalSize").isIntegralNumber()
                || payload.path("totalSize").intValue() < 0 || !users.isArray()
                || users.size() > payload.path("totalSize").intValue()
                || users.isEmpty() != (payload.path("totalSize").intValue() == 0)) {
            throw new IOException("Malformed Confluence search response");
        }
        ArrayNode result = JSON.arrayNode();
        Set<String> seen = new HashSet<>();
        for (JsonNode user : users) {
            String username = required(user, "username");
            if (!user.path("username").isTextual() || !user.path("link").isArray() || !seen.add(username)) {
                throw new IOException("Malformed Confluence candidate");
            }
            URI profile = null;
            for (JsonNode link : user.path("link")) {
                URI candidate = resolve(uri, required(link, "href"));
                if (candidate.getPath() != null && candidate.getPath().endsWith("~" + username)) {
                    client.validateOrigin(candidate);
                    if (profile != null && !profile.equals(candidate)) throw new IOException("Ambiguous Confluence profile link");
                    profile = candidate;
                }
            }
            if (profile == null) throw new IOException("Missing Confluence profile link");
            ObjectNode row = JSON.objectNode().put("source", "confluence").put("source_id", username)
                    .put("source_url", uri.toString()).put("username", username).put("profile_url", profile.toString())
                    .put("total_size", payload.path("totalSize").intValue());
            row.set("source_payload", user.deepCopy());
            result.add(row);
        }
        return result;
    }

    /** Fetches only an explicitly selected account; retired session-cookie authentication is unsupported. */
    public ObjectNode confluenceProfile(String canonicalId, String trustedUsername, URI profile) throws IOException {
        if (trustedUsername == null || trustedUsername.isBlank() || profile == null
                || profile.getPath() == null || !profile.getPath().endsWith("~" + trustedUsername)
                || profile.getQuery() != null) throw new IOException("Selected Confluence account and profile must agree");
        client.validateOrigin(profile);
        String html = client.text(profile);
        Document document = Jsoup.parse(html);
        if (document.select("#user-profile, #profile, #email").isEmpty() || !document.select("input[type=password]").isEmpty()) {
            throw new IOException("Unsupported or unauthenticated Confluence profile; legacy session authentication is unavailable");
        }
        ObjectNode result = bound(canonicalId, "confluence", JSON.textNode(trustedUsername), profile, JSON.textNode(html));
        ObjectNode patch = (ObjectNode) result.path("patch");
        patch.putObject("confluence").put("username", trustedUsername).put("profile_url", profile.toString());
        Element email = document.getElementById("email");
        if (email != null && !email.text().isBlank()) {
            String decoded = email.text().replace(" dot ", ".").replace(" at ", "@").toLowerCase(Locale.ROOT);
            if (!decoded.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IOException("Malformed Confluence email");
            patch.put("email", decoded).putArray("emails").add(decoded);
        }
        return result;
    }

    /** Maps a trusted email/hash profile into fill-only candidate fields; absent public profiles produce no patch. */
    public ObjectNode gravatar(JsonNode identity, Map<String, String> lanyrdMappings) throws IOException {
        String canonicalId = canonicalId(identity);
        Objects.requireNonNull(lanyrdMappings);
        String hash = identity.path("gravatar_id").asText("");
        if (hash.isBlank()) {
            String email = identity.path("email").asText("").toLowerCase(Locale.ROOT);
            if (email.isBlank()) return JSON.objectNode();
            try {
                hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(email.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("JDK must provide MD5 for the Gravatar protocol", impossible);
            }
        }
        URI uri = client.endpoint(encode(hash) + ".json");
        JsonNode payload;
        try {
            payload = client.json(uri);
        } catch (DataHttpClient.ServiceException exception) {
            if (exception.statusCode() == 404) return JSON.objectNode();
            throw exception;
        }
        if (!payload.isObject()) throw new IOException("Malformed Gravatar response");
        if (payload.isEmpty()) return JSON.objectNode();
        JsonNode entries = payload.path("entry");
        if (!entries.isArray() || entries.size() > 1) throw new IOException("Malformed or ambiguous Gravatar entry");
        if (entries.isEmpty()) return JSON.objectNode();
        JsonNode entry = entries.get(0);
        String observedHash = required(entry, "hash");
        if (!observedHash.equalsIgnoreCase(hash)) throw new IOException("Gravatar hash changed; explicit review required");
        required(entry, "id");
        ObjectNode result = bound(canonicalId, "gravatar", entry.get("id"), uri, entry);
        ObjectNode patch = (ObjectNode) result.path("patch");
        fill(identity, patch, "gravatar_id", JSON.textNode(observedHash));
        ObjectNode gravatar = JSON.objectNode();
        for (String field : new String[] {"id", "hash", "profileUrl"}) {
            if (entry.hasNonNull(field)) gravatar.set(field.equals("profileUrl") ? "profile_url" : field, entry.get(field).deepCopy());
        }
        fill(identity, patch, "gravatar", gravatar);
        for (Map.Entry<String, String> mapping : Map.of("preferredUsername", "preferred_username", "displayName", "name_cloak",
                "aboutMe", "bio", "currentLocation", "location").entrySet()) {
            fill(identity, patch, mapping.getValue(), entry.get(mapping.getKey()));
        }
        if (entry.hasNonNull("name") && !entry.path("name").isArray()) {
            if (!entry.path("name").isObject()) throw new IOException("Malformed Gravatar name");
            fill(identity, patch, "names", entry.get("name"));
            fill(identity, patch, "name", entry.path("name").get("formatted"));
        }
        if (entry.hasNonNull("email") && !entry.path("email").asText().isBlank()) {
            String email = required(entry, "email").toLowerCase(Locale.ROOT);
            fill(identity, patch, "email", JSON.textNode(email));
            ArrayNode emails = array(identity, "emails").deepCopy();
            String historical = identity.path("email").asText("");
            if (!historical.isBlank() && emails.valueStream().noneMatch(JSON.textNode(historical)::equals)) emails.add(historical);
            if (emails.valueStream().noneMatch(JSON.textNode(email)::equals)) emails.add(email);
            patch.set("emails", emails);
        }
        for (JsonNode account : array(entry, "accounts")) {
            String service = required(account, "shortname");
            if (ACCOUNT_FIELDS.contains(service)) {
                String historical = identity.path(service).path("username").asText("");
                if (!historical.isBlank() && account.hasNonNull("username") && !historical.equalsIgnoreCase(account.path("username").asText())) {
                    throw new IOException("Profile account changed; explicit review required");
                }
                fill(identity, patch, service, account);
            }
        }
        String twitter = patch.path("twitter").path("username").asText(identity.path("twitter").path("username")
                .asText(identity.path("twitter_username").asText("")));
        if (!twitter.isBlank()) {
            ObjectNode account = JSON.objectNode().put("username", twitter).put("url", "https://twitter.com/" + encode(twitter));
            if (!patch.has("twitter")) fill(identity, patch, "twitter", account);
            if (identity.path("speaker").asBoolean() && !identity.hasNonNull("lanyrd") && !patch.has("lanyrd")) {
                String username = lanyrdMappings.getOrDefault(twitter, twitter);
                patch.putObject("lanyrd").put("username", username).put("profile_url", "https://lanyrd.com/profile/" + encode(username));
            }
        }
        ArrayNode urls = array(entry, "urls");
        if (!urls.isEmpty()) {
            ArrayNode combined = array(identity, "urls").deepCopy();
            for (JsonNode url : urls) if (combined.valueStream().noneMatch(url::equals)) combined.add(url.deepCopy());
            patch.set("urls", combined);
        }
        for (JsonNode url : urls) {
            String title = required(url, "title").toLowerCase(Locale.ROOT);
            String value = required(url, "value");
            if (title.contains("blog")) fill(identity, patch, "blog", JSON.textNode(value));
            if (title.contains("personal")) fill(identity, patch, "homepage", JSON.textNode(value));
        }
        return result;
    }

    /** Normalizes a selected JBoss account/link while preserving nested history and rejecting conflicting usernames. */
    public ObjectNode community(JsonNode identity) throws IOException {
        String canonicalId = canonicalId(identity);
        String username = identity.path("jboss_username").asText(identity.path("jbosscommunity").path("username").asText(""));
        String nestedUsername = identity.path("jbosscommunity").path("username").asText("");
        if (!username.isBlank() && !nestedUsername.isBlank() && !username.equalsIgnoreCase(nestedUsername)) {
            throw new IOException("Conflicting JBoss usernames require explicit review");
        }
        JsonNode raw = identity;
        if (username.isBlank()) {
            for (JsonNode url : array(identity, "urls")) {
                if (!url.path("title").asText("").toLowerCase(Locale.ROOT).contains("jboss community")) continue;
                URI link = resolve(URI.create("https://community.jboss.org/"), required(url, "value"));
                if (link.getUserInfo() != null || !"community.jboss.org".equalsIgnoreCase(link.getHost()) || link.getQuery() != null
                        || link.getFragment() != null || link.getPort() != -1 || !Set.of("http", "https").contains(link.getScheme())
                        || !link.getPath().matches("/people/[^/]+/?")) throw new IOException("Invalid JBoss Community profile link");
                username = link.getPath().replaceFirst("^/people/", "").replaceFirst("/$", "");
                raw = url;
                break;
            }
        }
        if (username.isBlank()) return JSON.objectNode();
        URI profile = URI.create("https://community.jboss.org/people/" + encode(username));
        ObjectNode result = bound(canonicalId, "jbosscommunity", JSON.textNode(username), profile, raw);
        ObjectNode account = identity.path("jbosscommunity") instanceof ObjectNode existing ? existing.deepCopy() : JSON.objectNode();
        account.put("username", username).put("profile_url", profile.toString());
        ((ObjectNode) result.path("patch")).put("jboss_username", username).set("jbosscommunity", account);
        return result;
    }

    /** Preserves published avatars until reviewed, then chooses a real Gravatar, GitHub image or caller default. */
    public ObjectNode avatar(JsonNode identity, URI preferred, URI absent, URI github, URI fallback, boolean reviewed) throws IOException {
        String canonicalId = canonicalId(identity);
        ObjectNode raw = JSON.objectNode();
        String selected = identity.path("avatar_url").asText("");
        if (selected.isBlank() || reviewed) {
            if (fallback == null) throw new IllegalArgumentException("An explicit default avatar is required");
            safeImageUrl(fallback);
            if (github != null) safeImageUrl(github);
            selected = github == null ? fallback.toString() : github.toString();
            if (preferred != null) {
                if (absent == null) throw new IllegalArgumentException("A default Gravatar comparison image is required");
                byte[] bytes = null;
                try {
                    bytes = client.bytes(preferred);
                } catch (DataHttpClient.ServiceException exception) {
                    if (exception.statusCode() != 404) throw exception;
                }
                if (bytes != null) {
                    BufferedImage image = image(bytes);
                    BufferedImage defaultImage = image(client.bytes(absent));
                    boolean same = image.getWidth() == defaultImage.getWidth() && image.getHeight() == defaultImage.getHeight()
                            && Arrays.equals(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()),
                                    defaultImage.getRGB(0, 0, defaultImage.getWidth(), defaultImage.getHeight(), null, 0, defaultImage.getWidth()));
                    if (!same) selected = preferred.toString();
                }
                raw.put("gravatar_url", preferred.toString()).put("comparison_url", absent.toString());
            }
        }
        raw.put("reviewed", reviewed).put("selected_url", selected);
        ObjectNode result = bound(canonicalId, "avatar", JSON.textNode(canonicalId), null, raw);
        ((ObjectNode) result.path("patch")).put("avatar_url", selected);
        return result;
    }

    /** Rejects dangerous newly proposed image URLs while leaving historical avatar selections intact. */
    private static void safeImageUrl(URI uri) throws IOException {
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())
                && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost()))) throw new IOException("Unsafe avatar URL");
    }

    /** Checks decoded dimensions before allocation to prevent small encoded images from expanding without bounds. */
    private static BufferedImage image(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported avatar image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 2048 || height > 2048 || (long) width * height > 4_000_000) {
                    throw new IOException("Avatar dimensions exceed the decoding limit");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Malformed avatar image");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Requires C3a's textual canonical selector rather than manufacturing one by coercing a source value. */
    private static String canonicalId(JsonNode identity) throws IOException {
        if (identity == null || !identity.isObject() || !identity.path("id").isTextual()) {
            throw new IOException("An explicit textual canonical identity ID is required");
        }
        return required(identity, "id");
    }

    /** Makes the selected canonical ID a separate envelope field, preserving original source IDs and payloads. */
    private static ObjectNode bound(String canonicalId, String source, JsonNode sourceId, URI uri, JsonNode payload) throws IOException {
        if (canonicalId == null || canonicalId.isBlank()) throw new IOException("An explicit canonical identity ID is required");
        ObjectNode result = JSON.objectNode().put("identity_id", canonicalId).put("source", source);
        result.set("source_id", sourceId.deepCopy());
        if (uri != null) result.put("source_url", uri.toString());
        result.set("source_payload", payload.deepCopy());
        result.putObject("patch");
        return result;
    }

    /** Omits absent optional arrays while rejecting malformed source structures instead of clearing historical data. */
    private static ArrayNode array(JsonNode object, String field) throws IOException {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) return JSON.arrayNode();
        if (!(value instanceof ArrayNode array)) throw new IOException("Malformed profile array: " + field);
        return array;
    }

    /** Fills missing scalar/object fields while retaining historical nested account and profile properties. */
    private static void fill(JsonNode identity, ObjectNode patch, String field, JsonNode value) {
        if (value == null || value.isNull() || value.isTextual() && value.asText().isBlank()) return;
        JsonNode existing = identity.get(field);
        if (existing instanceof ObjectNode object && value.isObject()) {
            ObjectNode combined = object.deepCopy();
            value.properties().forEach(entry -> combined.putIfAbsent(entry.getKey(), entry.getValue().deepCopy()));
            patch.set(field, combined);
            return;
        }
        if ((existing == null || existing.isNull() || existing.isTextual() && existing.asText().isBlank()) && !patch.has(field)) {
            patch.set(field, value.deepCopy());
        }
    }

    /** Sanitizes URI parsing errors without echoing untrusted source strings or embedded credentials. */
    private static URI resolve(URI base, String value) throws IOException {
        try {
            return base.resolve(value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed profile URI");
        }
    }
}
