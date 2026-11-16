package org.arquillian.website.data;

import java.util.Locale;
import java.util.Map;

/** Selects whether a provider may use cached responses or must fetch live data. */
public enum RefreshPolicy {
    NORMAL,
    FORCE,
    OFFLINE,
    REDUCED_PREVIEW;

    /** Resolves the refresh mode using the system-property-over-environment convention. */
    public static RefreshPolicy from(Map<String, String> properties, Map<String, String> environment) {
        String mode = properties.get("roq.provider-mode");
        if (mode == null || mode.isBlank()) {
            mode = environment.get("ROQ_PROVIDER_MODE");
        }
        if (mode != null && !mode.isBlank()) {
            switch (mode.toLowerCase(Locale.ROOT)) {
                case "offline" -> { return OFFLINE; }
                case "reduced", "reduced-preview", "preview" -> { return REDUCED_PREVIEW; }
                case "live" -> { return NORMAL; }
                default -> throw new IllegalArgumentException("Unsupported provider mode: " + mode);
            }
        }
        String propertyValue = properties.get("roq.refresh");
        String environmentValue = environment.get("ROQ_REFRESH");
        String value = propertyValue == null || propertyValue.isBlank()
                ? environmentValue == null || environmentValue.isBlank() ? "false" : environmentValue
                : propertyValue;
        return Boolean.parseBoolean(value.toLowerCase(Locale.ROOT)) ? FORCE : NORMAL;
    }

    /** Returns whether cache reads and conditional request headers must be bypassed. */
    public boolean bypassesCache() {
        return this == FORCE;
    }

    /** Returns whether the provider must avoid network calls and use cache data only. */
    public boolean cacheOnly() {
        return this == OFFLINE || this == REDUCED_PREVIEW;
    }
}
