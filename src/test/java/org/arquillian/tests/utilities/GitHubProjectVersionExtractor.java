package org.arquillian.tests.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.arquillian.drone.webdriver.utils.HttpClient;
import org.jboss.arquillian.drone.webdriver.utils.Validate;

import static org.jboss.arquillian.drone.webdriver.binary.downloading.source.GitHubSource.AUTHORIZATION_HEADER_KEY;

public class GitHubProjectVersionExtractor {

    private static final Logger logger = Logger.getLogger(GitHubProjectVersionExtractor.class.getName());
    private static final String OAUTH_AUTHORIZATION_HEADER_VALUE_PREFIX = "Bearer ";

    private String TAGS_URL = "/tags";
    private String TAG_NAME = "name";
    private String project;

    public GitHubProjectVersionExtractor(String project) {
        this.project = String.format("https://api.github.com/repos/arquillian/%s", project);
    }

    public String getLatestReleaseFromGitHub() {
        try {
            final HttpClient.Response response = sentGetRequestWithPagination(project + TAGS_URL, 1, getAuthorizationHeader());
            JsonArray releaseTags = new Gson().fromJson(response.getPayload(), JsonElement.class).getAsJsonArray();
            if (releaseTags.size() == 0) {
                return null;
            }
            return releaseTags.get(0).getAsJsonObject().get(TAG_NAME).getAsString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch latest release from GitHub.", e);
        }
    }

    private HttpClient.Response sentGetRequestWithPagination(String url, int pageNumber, Map<String, String> headers)
        throws Exception {
        final URIBuilder uriBuilder = new URIBuilder(url);
        if (pageNumber != 1) {
            uriBuilder.setParameter("page", String.valueOf(pageNumber));
        }
        return new HttpClient().get(uriBuilder.build().toString(), headers);
    }

    private Map<String, String> getAuthorizationHeader() throws IOException {
        Map<String, String> headers = new HashMap<>();
        String token = new String(Files.readAllBytes(Paths.get(".github-auth"))).trim();
        if (Validate.nonEmpty(token)) {
            headers.put(AUTHORIZATION_HEADER_KEY, OAUTH_AUTHORIZATION_HEADER_VALUE_PREFIX + token);
        } else {
            logger.warning("Missing GitHub authentication configuration. Making an unauthenticated request to the GitHub API.");
        }
        return headers;
    }
}

