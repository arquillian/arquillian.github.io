package org.arquillian.website.data.model;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

/** Provides curated site metadata to Qute through Roq's typed data mapping. */
@DataMapping("site-config")
public record SiteData(String name, String description) {
}
