package org.arquillian.website.roq;

import java.util.List;

/** Defines the typed page-facing records shared by generated Roq routes and Qute. */
public final class GeneratedModels {

    private GeneratedModels() {
    }

    /** Groups all generated page families loaded for one site build. */
    public record SiteData(List<ModulePage> modules, List<ReleasePage> releases, List<IdentityPage> identities) {
        /** Creates an immutable generated-data aggregate. */
        public SiteData {
            modules = List.copyOf(modules);
            releases = List.copyOf(releases);
            identities = List.copyOf(identities);
        }

        /** Returns an empty generated-data aggregate. */
        public static SiteData empty() {
            return new SiteData(List.of(), List.of(), List.of());
        }
    }

    /** Describes a generated module page. */
    public record ModulePage(String slug, String title, String description, String owner, String url,
            List<Contributor> contributors, List<String> artifacts, List<String> dependencies) {
        /** Creates an immutable module page model. */
        public ModulePage {
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        }
    }

    /** Describes a generated release page and its calendar entry. */
    public record ReleasePage(String slug, String title, String version, String component, String date, String url,
            boolean latest, List<ReleaseNote> releaseNotes, List<Contributor> contributors) {
        /** Creates an immutable release page model. */
        public ReleasePage {
            releaseNotes = releaseNotes == null ? List.of() : List.copyOf(releaseNotes);
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
        }
    }

    /** Describes a generated community identity page. */
    public record IdentityPage(String githubId, String name, String role, String avatarUrl, String url,
            String company, String location, String bio, boolean core, boolean contributor, boolean speaker,
            boolean translator, List<String> repositories) {
        /** Creates an immutable identity page model. */
        public IdentityPage {
            repositories = repositories == null ? List.of() : List.copyOf(repositories);
        }
    }

    /** Describes a release-note issue rendered on a release page. */
    public record ReleaseNote(int number, String title, String category, String url) {
    }

    /** Describes a contributor link shared by module and release pages. */
    public record Contributor(String name, String email, int commits, String url) {
    }
}
