package org.arquillian.site;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Captures an explicitly requested production reference without affecting rendering. */
public final class ProductionBaseline {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?([^)'\"\\s]+)['\"]?\\s*\\)");
    private static final Map<URI, HttpResponse<byte[]>> ASSETS = new LinkedHashMap<>();

    /** Captures page and inline CSS asset evidence through the same path used by offline fixture tests. */
    static Map<String, Object> capturePage(URI url, Path output) throws Exception {
        HttpResponse<byte[]> response = fetch(url);
        if (!sameOrigin(url, response.uri())) {
            throw new IllegalArgumentException("Page redirected outside its origin: " + response.uri());
        }
        String htmlPath = "pages/" + hash(url.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16) + "/page.html";
        save(output.resolve(htmlPath), response.body());
        Document document = Jsoup.parse(new ByteArrayInputStream(response.body()), null, response.uri().toString());
        Set<String> fragments = new LinkedHashSet<>();
        for (Element element : document.select("[id], a[name]")) {
            fragments.add(element.hasAttr("id") ? element.id() : element.attr("name"));
        }
        List<Map<String, String>> links = new ArrayList<>();
        for (Element element : document.select("a[href]")) {
            URI link = resolve(response.uri(), element.absUrl("href"));
            if (link != null && sameOrigin(response.uri(), link)) {
                links.add(Map.of("page", withoutFragment(link).toString(),
                        "fragment", link.getFragment() == null ? "" : link.getFragment()));
            }
        }
        Set<URI> references = new LinkedHashSet<>();
        for (Element element : document.select("img[src], script[src], source[src], link[rel~=stylesheet|icon][href]")) {
            URI asset = resolve(response.uri(), element.absUrl(element.hasAttr("src") ? "src" : "href"));
            if (asset != null) references.add(withoutFragment(asset));
        }
        for (Element element : document.select("[style]")) {
            references.addAll(cssReferences(URI.create(element.baseUri()), element.attr("style")));
        }
        for (Element element : document.select("style")) {
            references.addAll(cssReferences(URI.create(element.baseUri()), element.data()));
        }
        List<Map<String, Object>> assets = new ArrayList<>();
        Set<URI> seen = new LinkedHashSet<>();
        for (URI reference : references) collectAsset(reference, output, assets, seen);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("requestedUrl", url.toString());
        page.put("finalUrl", response.uri().toString());
        page.put("status", response.statusCode());
        page.put("contentType", response.headers().firstValue("content-type").orElse(""));
        page.put("htmlPath", htmlPath);
        page.put("sha256", hash(response.body()));
        page.put("fragments", fragments);
        page.put("links", links);
        page.put("assets", assets);
        return page;
    }

    /** Retrieves explicit asset references and CSS dependencies, retaining unavailable-service evidence. */
    private static void collectAsset(URI url, Path output, List<Map<String, Object>> assets, Set<URI> seen)
            throws Exception {
        if (!seen.add(url)) return;
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("url", url.toString());
        List<String> references = new ArrayList<>();
        try {
            HttpResponse<byte[]> response = ASSETS.get(url);
            if (response == null) {
                response = fetch(url);
                ASSETS.put(url, response);
            }
            asset.put("finalUrl", response.uri().toString());
            asset.put("status", response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String checksum = hash(response.body());
                String path = "assets/" + checksum;
                save(output.resolve(path), response.body());
                asset.put("sha256", checksum);
                asset.put("path", path);
                if (response.headers().firstValue("content-type").orElse("").contains("text/css")) {
                    references.addAll(cssReferences(response.uri(), new String(response.body(), StandardCharsets.UTF_8))
                            .stream().map(URI::toString).toList());
                }
            } else {
                asset.put("error", "HTTP " + response.statusCode());
            }
        } catch (java.io.IOException exception) {
            asset.put("error", exception.toString());
        }
        asset.put("references", references);
        assets.add(asset);
        for (String nested : references) collectAsset(URI.create(nested), output, assets, seen);
    }

    /** Resolves inline and external CSS URLs consistently while ignoring non-HTTP references. */
    private static Set<URI> cssReferences(URI base, String css) {
        Set<URI> references = new LinkedHashSet<>();
        Matcher matcher = CSS_URL.matcher(css);
        while (matcher.find()) {
            URI reference = resolve(base, matcher.group(1));
            if (reference != null) references.add(withoutFragment(reference));
        }
        return references;
    }

    /** Performs a bounded HTTP read; interruption propagates instead of becoming a missing asset. */
    private static HttpResponse<byte[]> fetch(URI url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "ArquillianMigrationBaseline/1.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Ignores non-HTTP schemes and malformed references so they cannot become network requests. */
    private static URI resolve(URI base, String reference) {
        try {
            URI resolved = base.resolve(reference);
            return ("http".equalsIgnoreCase(resolved.getScheme()) || "https".equalsIgnoreCase(resolved.getScheme()))
                    && resolved.getHost() != null ? resolved : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Compares effective origins, including the scheme's implicit default port. */
    static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost()) && port(first) == port(second);
    }

    /** Makes explicit and implicit default ports equivalent for origin checks. */
    private static int port(URI url) {
        return url.getPort() >= 0 ? url.getPort() : "https".equalsIgnoreCase(url.getScheme()) ? 443 : 80;
    }

    /** Retains a URI's raw path and query while recording its fragment separately. */
    private static URI withoutFragment(URI url) {
        return URI.create(url.toString().split("#", 2)[0]);
    }

    /** Names artifacts by their received bytes so identical assets share a checksum. */
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Creates artifact parents without changing any content source. */
    private static void save(Path path, byte[] bytes) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    /** Runs only when explicitly requested, keeping remote reads out of ordinary builds and tests. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("capture")) {
            throw new IllegalArgumentException("Use ProductionBaseline capture explicitly");
        }
        Path output = Path.of("target/baseline");
        List<String> urls;
        try (java.io.InputStream input = ProductionBaseline.class.getResourceAsStream("/baseline-pages.json")) {
            urls = JSON.readValue(input, JSON.getTypeFactory().constructCollectionType(List.class, String.class));
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("startedAt", Instant.now().toString());
        try (Repository repository = new FileRepositoryBuilder().findGitDir().build()) {
            manifest.put("sourceRevision", repository.resolve("HEAD").name());
            manifest.put("sourceWorktreeDirty", !org.eclipse.jgit.api.Git.wrap(repository).status().call().isClean());
        }
        manifest.put("playwrightVersion", "1.65.0");
        List<Map<String, Object>> pages = new ArrayList<>();
        manifest.put("pages", pages);
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            manifest.put("browserVersion", browser.version());
            for (String url : urls) {
                Map<String, Object> page = capturePage(URI.create(url), output);
                pages.add(page);
                if (url.endsWith("/")) {
                    page.put("screenshots", List.of(screenshot(browser, url, output, page, 1440, 1000),
                            screenshot(browser, url, output, page, 390, 844)));
                }
                if (url.equals(urls.getFirst()) || url.contains("/guides/getting_started/")) {
                    page.put("breakpointChecks", boundaryChecks(browser, url));
                }
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("manifest.json").toFile(), manifest);
                System.out.println("Captured " + url);
            }
            manifest.put("completedAt", Instant.now().toString());
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("manifest.json").toFile(), manifest);
        }
    }

    /** Records responsive navigation and tab behavior at both sides of the legacy breakpoints. */
    private static List<Map<String, Object>> boundaryChecks(Browser browser, String url) {
        List<Map<String, Object>> checks = new ArrayList<>();
        try (Page page = browser.newPage()) {
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            for (int width : List.of(766, 767, 768, 978, 979, 980)) {
                page.setViewportSize(width, 1000);
                Map<String, Object> check = new LinkedHashMap<>();
                check.put("width", width);
                check.put("horizontalOverflow", page.evaluate("document.documentElement.scrollWidth > innerWidth"));
                boolean toggleVisible = page.locator(".btn-navbar").count() > 0 && page.locator(".btn-navbar").isVisible();
                check.put("navigationToggleVisible", toggleVisible);
                if (toggleVisible) {
                    page.locator(".btn-navbar").click();
                    check.put("navigationExpanded", page.locator(".nav-collapse").getAttribute("class"));
                    page.locator(".btn-navbar").click();
                }
                if (page.locator("a[data-toggle=tab]").count() > 0) {
                    page.locator("a[data-toggle=tab]").first().click();
                    check.put("activeTab", page.locator(".tab-pane.active").first().getAttribute("id"));
                }
                checks.add(check);
            }
        } catch (PlaywrightException exception) {
            checks.add(Map.of("error", exception.getMessage()));
        }
        return checks;
    }

    /** Waits for fonts and images and records network failures beside each fixed-viewport screenshot. */
    private static Map<String, Object> screenshot(Browser browser, String url, Path output,
            Map<String, Object> capture, int width, int height) {
        List<Map<String, String>> failures = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewport", Map.of("width", width, "height", height));
        result.put("capturedAt", Instant.now().toString());
        result.put("failures", failures);
        String path = capture.get("htmlPath").toString().replace("page.html", width + "x" + height + ".png");
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height))) {
            Page page = context.newPage();
            page.setDefaultTimeout(25_000);
            page.onRequestFailed(request -> failures.add(Map.of("url", request.url(), "error", request.failure())));
            page.onResponse(response -> {
                if (response.status() >= 400) failures.add(Map.of("url", response.url(), "error", "HTTP " + response.status()));
            });
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            result.put("finalUrl", page.url());
            try {
                page.waitForFunction("document.fonts.status === 'loaded' && Array.from(document.images).every(i => i.complete)");
            } catch (PlaywrightException exception) {
                result.put("assetWaitError", exception.getMessage());
            }
            result.put("brokenImages", page.evaluate("Array.from(document.images).filter(i => !i.naturalWidth).map(i => i.src)"));
            page.screenshot(new Page.ScreenshotOptions().setPath(output.resolve(path)).setFullPage(true));
            result.put("path", path);
        } catch (PlaywrightException exception) {
            result.put("error", exception.getMessage());
        }
        return result;
    }
}
