package org.arquillian.website.roq;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import jakarta.enterprise.context.ApplicationScoped;
import org.arquillian.website.roq.GeneratedModels.IdentityPage;
import org.arquillian.website.roq.GeneratedModels.ModulePage;
import org.arquillian.website.roq.GeneratedModels.ReleasePage;
import org.arquillian.website.roq.GeneratedModels.SiteData;

/** Loads normalized generated page data from ignored staging with curated fallback records. */
@ApplicationScoped
public final class GeneratedDataExtension {

    private static final Path DEFAULT_STAGING = Path.of("target/roq-data");
    private static final Path DEFAULT_FALLBACK = Path.of("data/curated/generated-pages.json");
    private static final String DATA_FILE = "generated-pages.json";

    private final ObjectMapper mapper;
    private final Path stagingDirectory;
    private final Path fallbackFile;

    /** Creates the build-time generated-data loader using Roq's ignored output directory. */
    public GeneratedDataExtension() {
        this(DEFAULT_STAGING, DEFAULT_FALLBACK);
    }

    /** Creates a loader with explicit paths for deterministic unit and lifecycle tests. */
    public GeneratedDataExtension(Path stagingDirectory, Path fallbackFile) {
        this.mapper = new ObjectMapper();
        this.stagingDirectory = Objects.requireNonNull(stagingDirectory, "stagingDirectory");
        this.fallbackFile = Objects.requireNonNull(fallbackFile, "fallbackFile");
    }

    /** Loads staged provider output first, then the committed curated fallback. */
    public SiteData load() throws IOException {
        Path stagedFile = stagingDirectory.resolve(DATA_FILE).normalize();
        if (Files.exists(stagedFile)) {
            return mapper.readValue(stagedFile.toFile(), SiteData.class);
        }
        if (Files.exists(fallbackFile)) {
            return mapper.readValue(fallbackFile.toFile(), SiteData.class);
        }
        return SiteData.empty();
    }

    /** Writes normalized provider output below the ignored staging directory only. */
    public Path stage(SiteData data) throws IOException {
        Objects.requireNonNull(data, "data");
        Path root = stagingDirectory.toAbsolutePath().normalize();
        Path trackedData = Path.of("data").toAbsolutePath().normalize();
        if (root.startsWith(trackedData)) {
            throw new IllegalArgumentException("generated provider data must not be written below data/");
        }
        Files.createDirectories(root);
        Path output = root.resolve(DATA_FILE);
        mapper.writeValue(output.toFile(), data);
        return output;
    }

    /** Creates a small typed fixture for lifecycle tests without contacting providers. */
    static SiteData fixture() {
        return new SiteData(
                java.util.List.of(new ModulePage("fixture-module", "Fixture Module", "Fixture description", "arquillian",
                        "/modules/fixture-module/", java.util.List.of(), java.util.List.of(), java.util.List.of())),
                java.util.List.of(new ReleasePage("fixture-release", "Fixture Release", "1.0.0.Final", "Fixture", "2026-01-01",
                        "/blog/2026/01/01/fixture-release/", true, java.util.List.of(), java.util.List.of())),
                java.util.List.of(new IdentityPage("fixture-user", "Fixture User", "Contributor", "",
                        "/community/fixture-user/", "", "", "", false, true, false, false, java.util.List.of())));
    }
}
