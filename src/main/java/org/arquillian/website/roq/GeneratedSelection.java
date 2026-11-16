package org.arquillian.website.roq;

import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.arquillian.website.roq.GeneratedModels.IdentityPage;
import org.arquillian.website.roq.GeneratedModels.ModulePage;
import org.arquillian.website.roq.GeneratedModels.ReleasePage;
import org.arquillian.website.roq.GeneratedModels.SiteData;

/** Selects generated routes for Roq's HTTP-based static export. */
public final class GeneratedSelection {

    private final GeneratedDataExtension data;

    /** Creates a selection producer backed by the normalized generated-data loader. */
    @Inject
    public GeneratedSelection(GeneratedDataExtension data) {
        this.data = data;
    }

    /** Produces the concrete generated URLs that Roq must request during batch generation. */
    @Produces
    @Singleton
    public RoqSelection pages() {
        try {
            SiteData generated = data.load();
            List<SelectedPath> paths = new ArrayList<>();
            paths.add(select("/modules/"));
            paths.add(selectPretty("/community/contributors"));
            generated.modules().stream().map(ModulePage::url).map(GeneratedSelection::select)
                    .forEach(paths::add);
            generated.releases().stream().map(ReleasePage::url).map(GeneratedSelection::select)
                    .forEach(paths::add);
            generated.identities().stream().map(IdentityPage::url).map(GeneratedSelection::selectPretty)
                    .forEach(paths::add);
            return new RoqSelection(List.copyOf(paths));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to select generated Roq routes", exception);
        }
    }

    /** Creates a static-export selection for one HTML route. */
    private static SelectedPath select(String path) {
        String outputPath = path.startsWith("/") ? path.substring(1) : path;
        if (outputPath.endsWith("/")) {
            outputPath += "index.html";
        } else {
            outputPath += ".html";
        }
        return SelectedPath.builder().path(path).outputPath(outputPath).build();
    }

    /** Selects a production pretty URL while writing its index file under the Roq output tree. */
    private static SelectedPath selectPretty(String path) {
        String outputPath = path.startsWith("/") ? path.substring(1) : path;
        return SelectedPath.builder().path(path).outputPath(outputPath + "/index.html").build();
    }
}
