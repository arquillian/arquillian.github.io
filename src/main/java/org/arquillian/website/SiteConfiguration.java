package org.arquillian.website;

import java.nio.file.Path;
import java.util.Map;

/** Provides the site-wide settings shared by Roq generators and provider clients. */
public final class SiteConfiguration {

    private static final String DEFAULT_SITE_URL = "https://arquillian.org";
    private static final Path DEFAULT_CACHE_DIRECTORY = Path.of(".roq-cache");
    private static final String DEFAULT_PROFILE = "development";

    private final String siteUrl;
    private final Path cacheDirectory;
    private final boolean refresh;
    private final String buildProfile;

    private SiteConfiguration(String siteUrl, Path cacheDirectory, boolean refresh, String buildProfile) {
        this.siteUrl = siteUrl;
        this.cacheDirectory = cacheDirectory;
        this.refresh = refresh;
        this.buildProfile = buildProfile;
    }

    /** Loads configuration from the current JVM properties and environment variables. */
    public static SiteConfiguration load() {
        return from((Map) System.getProperties(), System.getenv());
    }

    /** Creates configuration with system-style property values taking precedence over environment values. */
    public static SiteConfiguration from(Map<String, String> properties, Map<String, String> environment) {
        return new SiteConfiguration(
                value(properties, environment, "roq.site-url", "ROQ_SITE_URL", DEFAULT_SITE_URL),
                Path.of(value(properties, environment, "roq.cache-directory", "ROQ_CACHE_DIRECTORY",
                        DEFAULT_CACHE_DIRECTORY.toString())),
                Boolean.parseBoolean(value(properties, environment, "roq.refresh", "ROQ_REFRESH", "false")),
                value(properties, environment, "roq.profile", "ROQ_PROFILE", DEFAULT_PROFILE));
    }

    /** Returns the canonical site URL used when building absolute links. */
    public String siteUrl() {
        return siteUrl;
    }

    /** Returns the root directory used for provider response caches. */
    public Path cacheDirectory() {
        return cacheDirectory;
    }

    /** Returns whether providers must bypass usable cached responses. */
    public boolean refresh() {
        return refresh;
    }

    /** Returns the named Roq build profile. */
    public String buildProfile() {
        return buildProfile;
    }

    /** Resolves one setting while preserving the system-property-over-environment precedence rule. */
    private static String value(Map<String, String> properties, Map<String, String> environment,
            String propertyName, String environmentName, String defaultValue) {
        String propertyValue = properties.get(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String environmentValue = environment.get(environmentName);
        return environmentValue == null || environmentValue.isBlank() ? defaultValue : environmentValue;
    }
}
