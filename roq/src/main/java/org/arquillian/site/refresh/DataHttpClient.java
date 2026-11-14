package org.arquillian.site.refresh;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs bounded explicit-refresh requests without sending credentials to another origin. */
public final class DataHttpClient {
    private static final Pattern LINK = Pattern.compile("(?:^|,)\\s*<([^>]*)>\\s*([^,]*)");
    private static final Pattern REL = Pattern.compile(";\\s*rel\\s*=\\s*\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final URI base;
    private final String token;
    private final Duration timeout;
    private final HttpClient client;

    /** Uses a thirty-second timeout for an explicitly configured service and optional bearer token. */
    public DataHttpClient(URI baseUri, String token) {
        this(baseUri, token, Duration.ofSeconds(30));
    }

    /** Restricts insecure transport to loopback and rejects unbounded or invalid request settings. */
    public DataHttpClient(URI baseUri, String token, Duration timeout) {
        if (baseUri == null || baseUri.getHost() == null || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null || baseUri.getFragment() != null
                || !("https".equalsIgnoreCase(baseUri.getScheme()) || ("http".equalsIgnoreCase(baseUri.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(baseUri.getHost())))) {
            throw new IllegalArgumentException("Service base must use HTTPS or loopback HTTP without credentials");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("Request timeout must be positive and at most two minutes");
        }
        if (token != null && !token.isBlank() && token.strip().chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("Invalid bearer token");
        }
        this.base = URI.create(baseUri.toString().replaceAll("/+$", "") + "/");
        this.token = token == null || token.isBlank() ? null : token.strip();
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Resolves a service-relative path while retaining a configured deployment context path. */
    public URI endpoint(String relative) {
        URI path = URI.create(relative.replaceFirst("^/", ""));
        if (path.isAbsolute() || path.getRawAuthority() != null) {
            throw new IllegalArgumentException("Endpoint must be relative to its configured service");
        }
        return base.resolve(path);
    }

    /** Fetches one strictly parsed JSON value, rejecting empty or trailing content. */
    public JsonNode json(URI uri) throws IOException {
        return parse(text(uri));
    }

    /** Fetches at most two MiB of UTF-8 text from the configured origin and accepts successful statuses. */
    public String text(URI uri) throws IOException {
        return new String(request(uri, ignored -> new BoundedBytes(), "application/json").body(), StandardCharsets.UTF_8);
    }

    /** Fetches at most two MiB of image data using the same timed, authenticated origin restrictions as JSON. */
    public byte[] bytes(URI uri) throws IOException {
        return request(uri, ignored -> new BoundedBytes(), "image/*").body();
    }

    /** Distinguishes an absent optional profile from an unavailable source without parsing error messages. */
    public static final class ServiceException extends IOException {
        private final int status;

        /** Retains only the status so response bodies, request URLs and credentials cannot leak through errors. */
        private ServiceException(int status) {
            super("Service returned HTTP " + status);
            this.status = status;
        }

        /** Returns the original status for explicit optional-source policies such as Gravatar's profile 404. */
        public int statusCode() { return status; }
    }

    /** Collects bounded array pages through Link headers, refusing cycles, foreign origins and runaway pagination. */
    public ArrayNode pages(URI firstPage) throws IOException {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        Set<URI> visited = new HashSet<>();
        URI next = firstPage;
        while (next != null) {
            if (visited.size() >= 1000 || !visited.add(next.normalize())) {
                throw new IOException("Repeated or excessive pagination");
            }
            HttpResponse<byte[]> response = request(next, ignored -> new BoundedBytes(), "application/json");
            JsonNode page = parse(new String(response.body(), StandardCharsets.UTF_8));
            if (!page.isArray()) {
                throw new IOException("Expected a JSON array page");
            }
            result.addAll((ArrayNode) page);
            next = nextPage(next, String.join(",", response.headers().allValues("Link")));
        }
        return result;
    }

    /** Encodes one path or query segment, including bracketed GitHub bot names. */
    static String encode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A nonblank service identifier is required");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Requires an original nonblank scalar field instead of manufacturing missing source identifiers. */
    static String required(JsonNode value, String field) throws IOException {
        JsonNode content = value.get(field);
        if (content == null || !(content.isTextual() || content.isIntegralNumber()) || content.asText().isBlank()) {
            throw new IOException("Missing or invalid source field: " + field);
        }
        return content.asText();
    }

    /** Parses untrusted JSON without exposing response bodies in error messages. */
    private JsonNode parse(String body) throws IOException {
        try {
            JsonNode value = mapper.readTree(body);
            if (value == null || value.isNull()) {
                throw new IOException("Empty JSON response");
            }
            return value;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IOException("Malformed JSON response");
        }
    }

    /** Validates discovered source links before either returning candidates or transmitting authenticated requests. */
    void validateOrigin(URI uri) throws IOException {
        if (uri == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !base.getScheme().equalsIgnoreCase(uri.getScheme()) || !base.getHost().equalsIgnoreCase(uri.getHost())
                || effectivePort(base) != effectivePort(uri)) {
            throw new IOException("Request target is outside the configured service origin");
        }
    }

    /** Executes one timed request with redirects disabled, restoring interruption on cancellation. */
    private <T> HttpResponse<T> request(URI uri, HttpResponse.BodyHandler<T> handler, String accept) throws IOException {
        validateOrigin(uri);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", accept);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<T> response = client.send(request.GET().build(), handler);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ServiceException(response.statusCode());
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Refresh request interrupted", exception);
        }
    }

    /** Cancels oversized bodies before forwarding their buffers to the JDK's byte-array accumulator. */
    private static final class BoundedBytes implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long count;

        /** Exposes the JDK completion result without adding a second request or blocking body reader. */
        @Override
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        /** Retains cancellation control while preserving the JDK accumulator's demand handling. */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        /** Enforces the response byte limit before a buffer can increase the retained body size. */
        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) count += buffer.remaining();
            if (count > 2 * 1024 * 1024) {
                subscription.cancel();
                delegate.onError(new IOException("Response exceeds two MiB"));
            } else {
                delegate.onNext(buffers);
            }
        }

        /** Preserves transport failures instead of producing an empty optional image. */
        @Override
        public void onError(Throwable failure) { delegate.onError(failure); }

        /** Completes the bounded accumulation only after all response buffers arrive. */
        @Override
        public void onComplete() { delegate.onComplete(); }
    }

    /** Resolves the unique next relation while rejecting malformed pagination headers. */
    private URI nextPage(URI current, String header) throws IOException {
        Matcher links = LINK.matcher(header);
        URI next = null;
        boolean matched = false;
        while (links.find()) {
            matched = true;
            Matcher relation = REL.matcher(links.group(2));
            if (relation.find() && Set.of(relation.group(1).strip().split("\\s+")).contains("next")) {
                if (next != null) {
                    throw new IOException("Multiple next-page relations");
                }
                try {
                    next = current.resolve(links.group(1));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid pagination URI");
                }
            }
        }
        if (!header.isBlank() && !matched) {
            throw new IOException("Malformed pagination header");
        }
        return next;
    }

    /** Compares implicit and explicit default ports as the same origin. */
    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }
}
