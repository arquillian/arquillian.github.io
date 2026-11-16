package org.arquillian.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Verifies the build configuration contract used by later Roq data generators. */
class SiteConfigurationTest {

    /** Confirms that a clean build uses safe defaults and does not force provider refreshes. */
    @Test
    void usesDefaultsWithoutOverrides() {
        SiteConfiguration configuration = SiteConfiguration.from(Map.of(), Map.of());

        assertEquals("https://arquillian.org", configuration.siteUrl());
        assertEquals(Path.of(".roq-cache"), configuration.cacheDirectory());
        assertEquals("development", configuration.buildProfile());
        assertFalse(configuration.refresh());
    }

    /** Confirms that explicit system properties override environment defaults for refreshes. */
    @Test
    void systemPropertiesOverrideEnvironment() {
        SiteConfiguration configuration = SiteConfiguration.from(
                Map.of("roq.refresh", "false", "roq.site-url", "https://example.test"),
                Map.of("ROQ_REFRESH", "true", "ROQ_PROFILE", "production"));

        assertFalse(configuration.refresh());
        assertEquals("https://example.test", configuration.siteUrl());
        assertEquals("production", configuration.buildProfile());
    }

    /** Confirms that the documented environment switch can force a refresh. */
    @Test
    void environmentCanForceRefresh() {
        SiteConfiguration configuration = SiteConfiguration.from(Map.of(), Map.of("ROQ_REFRESH", "true"));

        assertTrue(configuration.refresh());
    }
}
