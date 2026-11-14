package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies optional source collection with local fixtures so builds need no live services or secrets. */
class EnrichmentCollectorsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, byte[]> bodies = new HashMap<>();
    private final Map<String, Integer> statuses = new HashMap<>();
    private final Map<String, String> auth = new HashMap<>();
    private HttpServer server;
    private DataHttpClient client;
    private IdentityCollector identities;

    /** Starts one isolated origin and records requests to verify that bearer tokens stay inside it. */
    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestURI().toString();
            auth.put(key, exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = bodies.getOrDefault(key, new byte[0]);
            exchange.sendResponseHeaders(statuses.getOrDefault(key, bodies.containsKey(key) ? 200 : 404), body.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(body);
            }
        });
        server.start();
        client = new DataHttpClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "fixture-token");
        identities = new IdentityCollector(client);
    }

    /** Releases the local fixture listener after every scenario. */
    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** A display-name query returns all suggestions without asserting canonical ownership. */
    @Test
    void confluenceSearchRetainsAmbiguousCandidatesAndRawData() throws Exception {
        put("/rest/prototype/1/search/user?query=alice%20smith", """
                {"totalSize":2,"result":[{"username":"alice","link":[{"href":"/display/~alice"}]},
                  {"username":"asmith","link":[{"href":"/display/~asmith"}]}]}
                """);
        ArrayNode result = identities.confluenceCandidates("Alice Middle Smith");
        assertEquals(2, result.size());
        assertFalse(result.get(0).has("identity_id"));
        assertEquals("alice", result.get(0).path("source_id").asText());
        assertEquals("/display/~alice", result.get(0).path("source_payload").path("link").get(0).path("href").asText());
        put("/rest/prototype/1/search/user?query=empty", "{\"totalSize\":0,\"result\":[]}");
        assertTrue(identities.confluenceCandidates("empty").isEmpty());
        put("/rest/prototype/1/search/user?query=broken", "{\"totalSize\":1,\"result\":[]}");
        assertThrows(IOException.class, () -> identities.confluenceCandidates("broken"));
        put("/rest/prototype/1/search/user?query=foreign", "{\"totalSize\":1,\"result\":[{\"username\":\"alice\",\"link\":[{\"href\":\"https://example.org/~alice\"}]}]}");
        assertThrows(IOException.class, () -> identities.confluenceCandidates("foreign"));
        put("/rest/prototype/1/search/user?query=malformed", "{\"totalSize\":1,\"result\":[{\"username\":17,\"link\":[{\"href\":\"/display/~17\"}]}]}");
        assertThrows(IOException.class, () -> identities.confluenceCandidates("malformed"));
    }

    /** Only an explicitly selected account produces an email patch; login pages remain source failures. */
    @Test
    void confluenceProfileRequiresSelectedAccountAndDecodesEmail() throws Exception {
        put("/display/~alice", "<div id='user-profile'><span id='email'>Alice at Example dot ORG</span></div>");
        ObjectNode result = identities.confluenceProfile("published:alice", "alice", client.endpoint("display/~alice"));
        assertEquals("published:alice", result.path("identity_id").asText());
        assertEquals("alice@example.org", result.path("patch").path("email").asText());
        assertTrue(result.path("source_payload").asText().contains(" at "));
        assertThrows(IOException.class, () -> identities.confluenceProfile("published:alice", "bob", client.endpoint("display/~alice")));
        put("/display/~alice", "<form><input type=password></form>");
        assertThrows(IOException.class, () -> identities.confluenceProfile("published:alice", "alice", client.endpoint("display/~alice")));
    }

    /** Gravatar maps legacy profile fields while preserving selected IDs and retaining raw account data. */
    @Test
    void gravatarProducesSparseBoundPatchAndLanyrdMapping() throws Exception {
        ObjectNode identity = (ObjectNode) mapper.readTree("{\"id\":\"published:alice\",\"gravatar_id\":\"abc\",\"speaker\":true,\"twitter_username\":\"alice\"}");
        put("/abc.json", """
                {"entry":[{"id":"17","hash":"abc","profileUrl":"https://gravatar.com/alice",
                 "preferredUsername":"alice","displayName":"Alice","aboutMe":"Bio","currentLocation":"Earth",
                 "name":{"formatted":"Alice Smith"},"email":"ALICE@EXAMPLE.ORG",
                 "accounts":[{"shortname":"twitter","username":"alice","url":"https://twitter.com/alice"}],
                 "urls":[{"title":"Personal blog","value":"https://example.org/blog"}]}]}
                """);
        ObjectNode result = identities.gravatar(identity, Map.of("alice", "alice_smith"));
        assertEquals("published:alice", result.path("identity_id").asText());
        JsonNode patch = result.path("patch");
        assertEquals("Earth", patch.path("location").asText());
        assertEquals("alice@example.org", patch.path("emails").get(0).asText());
        assertEquals("alice_smith", patch.path("lanyrd").path("username").asText());
        assertEquals("https://example.org/blog", patch.path("blog").asText());
        assertEquals("17", result.path("source_id").asText());
        assertTrue(result.path("source_payload").path("accounts").isArray());
        assertFalse(identity.has("location"));
        identity.put("name", "Published name").put("location", "Historical location");
        JsonNode sparse = identities.gravatar(identity, Map.of()).path("patch");
        assertFalse(sparse.has("name"));
        assertFalse(sparse.has("location"));
        ObjectNode emailOnly = mapper.createObjectNode().put("id", "published:email").put("email", "MyEmailAddress@example.com");
        put("/0bc83cb571cd1c50ba6f3e8a78ef1346.json", "{}");
        assertTrue(identities.gravatar(emailOnly, Map.of()).isEmpty());
        assertTrue(auth.containsKey("/0bc83cb571cd1c50ba6f3e8a78ef1346.json"));
        put("/abc.json", "{}");
        assertTrue(identities.gravatar(identity, Map.of()).isEmpty());
        put("/abc.json", "{\"entry\":[]}");
        assertTrue(identities.gravatar(identity, Map.of()).isEmpty());
        statuses.put("/abc.json", 404);
        assertTrue(identities.gravatar(identity, Map.of()).isEmpty());
        statuses.put("/abc.json", 503);
        assertThrows(IOException.class, () -> identities.gravatar(identity, Map.of()));
        statuses.remove("/abc.json");
        put("/abc.json", "{\"entry\":{}}");
        assertThrows(IOException.class, () -> identities.gravatar(identity, Map.of()));
    }

    /** Historical community links are normalized only from selected identity data, with unsafe hosts rejected. */
    @Test
    void communityNormalizesTrustedAccountAndRetainsLinkSource() throws Exception {
        ObjectNode identity = (ObjectNode) mapper.readTree("{\"id\":\"published:alice\",\"urls\":[{\"title\":\"JBoss Community\",\"value\":\"http://community.jboss.org/people/alice/\"}]}");
        ObjectNode result = identities.community(identity);
        assertEquals("alice", result.path("patch").path("jboss_username").asText());
        assertEquals("https://community.jboss.org/people/alice", result.path("patch").path("jbosscommunity").path("profile_url").asText());
        assertEquals("published:alice", result.path("identity_id").asText());
        ((ObjectNode) identity.path("urls").get(0)).put("value", "https://example.org/people/alice");
        assertThrows(IOException.class, () -> identities.community(identity));
        identity.put("jboss_username", "alice").putObject("jbosscommunity").put("username", "bob");
        assertThrows(IOException.class, () -> identities.community(identity));
    }

    /** Avatar comparison uses decoded pixels; an existing published choice needs an explicit reviewed refresh. */
    @Test
    void avatarsPreservePublishedChoiceAndComparePixelsBeforeFallback() throws Exception {
        byte[] png = image(2, 2, "png");
        bodies.put("/avatar", png);
        bodies.put("/absent", image(2, 2, "bmp"));
        ObjectNode identity = mapper.createObjectNode().put("id", "published:alice").put("avatar_url", "https://old.example/avatar");
        URI github = URI.create("https://avatars.githubusercontent.com/u/7?s=48");
        URI fallback = URI.create("https://community.jboss.org/people/sbs-default-avatar/avatar/48.png");
        assertEquals("https://old.example/avatar", identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, false).path("patch").path("avatar_url").asText());
        assertFalse(auth.containsKey("/avatar"));
        assertEquals(github.toString(), identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, true).path("patch").path("avatar_url").asText());
        assertEquals(fallback.toString(), identities.avatar(identity, null, null, null, fallback, true).path("patch").path("avatar_url").asText());
        bodies.put("/absent", image(1, 1, "png"));
        assertEquals(client.endpoint("avatar").toString(), identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, true).path("patch").path("avatar_url").asText());
        statuses.put("/avatar", 404);
        assertEquals(github.toString(), identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, true).path("patch").path("avatar_url").asText());
        statuses.put("/avatar", 503);
        assertThrows(IOException.class, () -> identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, true));
        statuses.remove("/avatar");
        bodies.put("/avatar", image(2049, 1, "png"));
        assertThrows(IOException.class, () -> identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, true));
        put("/avatar", "not an image");
        assertThrows(IOException.class, () -> identities.avatar(identity, client.endpoint("avatar"), client.endpoint("absent"), github, fallback, true));
    }

    /** Binary requests share token/status/origin rules and stop oversized data before image decoding. */
    @Test
    void binaryRequestsAreBoundedAndOriginProtected() throws Exception {
        bodies.put("/blob", new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, client.bytes(client.endpoint("blob")));
        assertEquals("Bearer fixture-token", auth.get("/blob"));
        assertThrows(IOException.class, () -> client.bytes(URI.create("https://example.org/private")));
        statuses.put("/blob", 302);
        assertThrows(IOException.class, () -> client.bytes(client.endpoint("blob")));
        statuses.remove("/blob");
        bodies.put("/large", new byte[2 * 1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> client.bytes(client.endpoint("large")));
        DataHttpClient.ServiceException missing = assertThrows(DataHttpClient.ServiceException.class, () -> client.bytes(client.endpoint("missing")));
        assertEquals(404, missing.statusCode());
    }

    /** Search follows real pagination for current and next years and retains event dates, speakers and provenance. */
    @Test
    void lanyrdCollectsMultiplePagesAndYearsWithDeterministicTimes() throws Exception {
        put("/search/?type=session&q=arquillian&year=2026", search("one", "<div class=pagination><a href='?type=session&q=arquillian&year=2026&page=2'>2</a></div>"));
        put("/search/?type=session&q=arquillian&year=2026&page=2", search("two", ""));
        put("/search/?type=session&q=arquillian&year=2027", search("three", ""));
        put("/2026/conf/one/", detail("2026-11-14T10:00:00", "2026-11-14T11:00:00", "America/Chicago"));
        put("/2026/conf/two/", detail("2026-11-15", "2026-11-16", "America/Chicago"));
        put("/2026/conf/three/", detail("2027-01-01T10:00:00+01:00", "2027-01-01T11:00:00+01:00", "Europe/Paris"));
        ArrayNode result = lanyrd().sessions("arquillian");
        assertEquals(3, result.size());
        assertEquals("2026-11-14T16:00:00Z", result.get(0).path("start_datetime").asText());
        assertEquals("Alice Smith", result.get(0).path("speakers").get(0).path("name").asText());
        assertEquals("alice", result.get(0).path("speakers").get(0).path("username").asText());
        assertTrue(result.get(1).path("date_only").asBoolean());
        assertEquals("2026-11-15", result.get(1).path("start_date").asText());
        assertEquals("2027-01-01T09:00:00Z", result.get(2).path("start_datetime").asText());
        assertEquals("lanyrd:session:one@arquillian.org", result.get(0).path("uid").asText());
        assertTrue(result.get(0).path("source_payload").path("detail_html").asText().contains("dtstart"));
        assertEquals("Paris, France", result.get(0).path("event_location").asText());
        put("/2026/conf/one/", detail("2026-11-12", "2026-11-13", "America/Chicago"));
        assertEquals(2, lanyrd().sessions("arquillian").size());
    }

    /** Empty recognized pages succeed; unavailable services, login pages, unsafe links and bad times fail closed. */
    @Test
    void lanyrdRejectsUnsafeAndMalformedOrRetiredSources() throws Exception {
        String first = "/search/?type=session&q=x&year=2026";
        put(first, "<ul class=search-results></ul><p class=no-results>No sessions found.</p>");
        put("/search/?type=session&q=x&year=2027", "<ul class=search-results></ul><p class=no-results>No sessions found.</p>");
        assertTrue(lanyrd().sessions("x").isEmpty());
        put(first, "<form><input type=password></form>");
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
        put(first, search("one", "<div class=pagination><a href='https://example.org/search/?page=2'>2</a></div>"));
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
        put(first, search("one", ""));
        for (String start : new String[] {"broken", "2026-11-14T10:00:00", "2026-03-08T02:30:00", "2026-11-01T01:30:00"}) {
            put("/2026/conf/one/", detail(start, "2026-11-14T09:00:00", "America/Chicago"));
            assertThrows(IOException.class, () -> lanyrd().sessions("x"));
        }
        statuses.put(first, 503);
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
    }

    /** New profile observations union historical email/URL lists and fill existing nested accounts without clearing fields. */
    @Test
    void gravatarPreservesHistoricalListsAndEnrichesAccountFields() throws Exception {
        ObjectNode identity = (ObjectNode) mapper.readTree("{\"id\":\"published:alice\",\"gravatar_id\":\"abc\",\"email\":\"old@example.org\",\"emails\":[\"older@example.org\"],\"urls\":[{\"title\":\"Old\",\"value\":\"https://old.example/\"}],\"twitter\":{\"username\":\"alice\",\"historical\":true}}");
        put("/abc.json", "{\"entry\":[{\"id\":17,\"hash\":\"abc\",\"email\":\"new@example.org\",\"urls\":[{\"title\":\"Blog\",\"value\":\"https://new.example/\"}],\"accounts\":[{\"shortname\":\"twitter\",\"username\":\"alice\",\"url\":\"https://twitter.com/alice\"}]}]}");
        JsonNode patch = identities.gravatar(identity, Map.of()).path("patch");
        assertEquals(3, patch.path("emails").size());
        assertTrue(patch.path("emails").toString().contains("older@example.org"));
        assertEquals(2, patch.path("urls").size());
        assertTrue(patch.path("twitter").path("historical").asBoolean());
        assertEquals("https://twitter.com/alice", patch.path("twitter").path("url").asText());
        assertEquals(1, identity.path("urls").size());
    }

    /** Identical legacy slugs from different source URLs require review instead of silently dropping an event. */
    @Test
    void lanyrdRejectsSourceIdCollision() throws Exception {
        String page = search("one", "");
        put("/search/?type=session&q=x&year=2026", page);
        put("/search/?type=session&q=x&year=2027", page.replace("/2026/conf/one/", "/2027/other/one/"));
        put("/2026/conf/one/", detail("2026-11-14", "2026-11-15", "UTC"));
        put("/2027/other/one/", detail("2027-01-01", "2027-01-02", "UTC"));
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
    }

    /** Conference-only dates and the later detail-page speaker layout survive without fabricated session times. */
    @Test
    void lanyrdPreservesConferenceOnlyDateAndDetailSpeakers() throws Exception {
        put("/search/?type=session&q=x&year=2026", search("one", ""));
        put("/search/?type=session&q=x&year=2027", "<ul class=search-results></ul><p class=no-results>No sessions found.</p>");
        put("/2026/conf/one/", "<div class=session-meta></div><div class=conference><p class=date><abbr class=dtstart title='2026-11-14'></abbr></p></div><ul class=user-list><li><a href='/profile/bob/'><span class=user-name>Bob</span></a></li><li><a href='/profile/carol/'><span class=user-name>Carol</span></a></li></ul>");
        JsonNode event = lanyrd().sessions("x").get(0);
        assertEquals("2026-11-14", event.path("conference_start_date").asText());
        assertFalse(event.has("start_datetime"));
        assertEquals(3, event.path("speakers").size());
        assertEquals("carol", event.path("speakers").get(2).path("username").asText());
    }

    /** Canonical IDs retain C3a's textual contract instead of coercing arbitrary upstream values into person selectors. */
    @Test
    void rejectsMalformedCanonicalSelectors() throws Exception {
        ObjectNode malformed = mapper.createObjectNode().put("id", 17);
        assertThrows(IOException.class, () -> identities.gravatar(malformed, Map.of()));
        assertThrows(IOException.class, () -> identities.community(malformed));
        assertThrows(IOException.class, () -> identities.avatar(malformed, null, null, null, null, false));
    }

    /** Unsupported session-like tags must fail rather than silently disappearing from the parsed result list. */
    @Test
    void lanyrdRejectsUnsupportedSessionMarkup() throws Exception {
        String html = "<div class='s-session'><h3>Session</h3></div>";
        put("/search/?type=session&q=x&year=2026", html);
        put("/search/?type=session&q=x&year=2027", html);
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
    }

    /** A familiar result wrapper cannot turn a service error page into a successful empty collection. */
    @Test
    void lanyrdRejectsErrorInsideSearchResults() throws Exception {
        String html = "<div class='search-results'><div class='error'>Service unavailable</div></div>";
        put("/search/?type=session&q=x&year=2026", html);
        put("/search/?type=session&q=x&year=2027", html);
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
    }

    /** Empty results require an explicit valid zero-results message, with no unknown rows or contradictory errors. */
    @Test
    void lanyrdRequiresPositiveEmptyResultEvidence() throws Exception {
        for (String html : new String[] {"<ul class=search-results></ul>",
                "<div class=no-results>Service unavailable</div>",
                "<ul class=search-results><li>Unsupported result</li></ul><p class=no-results>No sessions found.</p>"}) {
            put("/search/?type=session&q=x&year=2026", html);
            put("/search/?type=session&q=x&year=2027", html);
            assertThrows(IOException.class, () -> lanyrd().sessions("x"));
        }
        put("/search/?type=session&q=x&year=2026", "<p class=no-results>No matching sessions found.</p>");
        put("/search/?type=session&q=x&year=2027", "<div id=search-results><p class=no-results>No results.</p></div>");
        assertTrue(lanyrd().sessions("x").isEmpty());
    }

    /** A parseable session cannot hide additional failed or unsupported results in the same response. */
    @Test
    void lanyrdRejectsMixedValidAndErrorResultContent() throws Exception {
        put("/search/?type=session&q=x&year=2026", search("one", "").replace("</ul>", "<li class=error>Service unavailable</li></ul>"));
        put("/search/?type=session&q=x&year=2027", "<p class=no-results>No sessions found.</p>");
        put("/2026/conf/one/", detail("2026-11-14", "2026-11-15", "UTC"));
        assertThrows(IOException.class, () -> lanyrd().sessions("x"));
    }

    /** Fixes the collection clock so year selection and future filtering never depend on the test date. */
    private LanyrdCollector lanyrd() {
        return new LanyrdCollector(client, Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC));
    }

    /** Supplies the original legacy search classes and relative links without external fixture files. */
    private String search(String slug, String pagination) {
        return "<ul class=search-results><li class=s-session><h3><a href='/2026/conf/" + slug + "/'>Session " + slug
                + "</a></h3><p class=meta><strong>Event</strong><a href='/conf/'>Conference</a></p>"
                + "<p class=meta><strong>Speakers</strong><a href='/profile/alice/'>Alice Smith</a></p></li></ul>" + pagination;
    }

    /** Supplies detail metadata in the selectors used by the historical Lanyrd extension. */
    private String detail(String start, String end, String zone) {
        return "<div class=session-meta><abbr class=dtstart title='" + start + "'></abbr><abbr class=dtend title='" + end
                + "'></abbr></div><abbr class=timezone title='" + zone + "'></abbr>"
                + "<div class=description>Session description</div><p class=location>France / Paris</p>";
    }

    /** Installs UTF-8 JSON or HTML at a fixture URL. */
    private void put(String path, String body) {
        bodies.put(path, body.getBytes(StandardCharsets.UTF_8));
    }

    /** Produces equivalent pixels in different encodings to detect an accidental encoded-byte comparison. */
    private byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }
}
