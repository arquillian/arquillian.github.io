package org.arquillian.tests.utilities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.arquillian.drone.webdriver.utils.HttpClient;

public class GitHubProjectVersionExtractor {

    private String TAGS_URL = "/tags";
    private String TAG_NAME = "name";
    private String project;

    public GitHubProjectVersionExtractor(String project) {
        this.project = String.format("https://api.github.com/repos/arquillian/%s", project);
    }

    public String getLatestReleaseFromGitHub() {
        try {
            final HttpClient.Response response = sentGetRequestWithPagination(project + TAGS_URL, 1);
            JsonArray releaseTags = new Gson().fromJson(response.getPayload(), JsonElement.class).getAsJsonArray();
            if (releaseTags.size() == 0) {
                return null;
            }
            return releaseTags.get(0).getAsJsonObject().get(TAG_NAME).getAsString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch latest release from GitHub.", e);
        }
    }

    private HttpClient.Response sentGetRequestWithPagination(String url, int pageNumber) throws Exception {
        final URIBuilder uriBuilder = new URIBuilder(url);
        if (pageNumber != 1) {
            uriBuilder.setParameter("page", String.valueOf(pageNumber));
        }
        return new HttpClient().get(uriBuilder.build().toString());
    }
}
