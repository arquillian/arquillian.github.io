package org.arquillian.website.roq;

import java.util.List;

/** Defines the typed page-facing records shared by generated Roq routes and Qute. */
public final class GeneratedModels {

    private GeneratedModels() {
    }

    /** Groups all generated page families loaded for one site build. */
    public record SiteData(List<ModulePage> modules, List<ReleasePage> releases, List<IdentityPage> identities,
            List<NewsItem> news) {
        /** Creates generated page data without optional homepage news records. */
        public SiteData(List<ModulePage> modules, List<ReleasePage> releases, List<IdentityPage> identities) {
            this(modules, releases, identities, List.of());
        }

        /** Creates an immutable generated-data aggregate. */
        public SiteData {
            modules = List.copyOf(modules);
            releases = List.copyOf(releases);
            identities = List.copyOf(identities);
            news = news == null ? List.of() : List.copyOf(news);
        }

        /** Returns an empty generated-data aggregate. */
        public static SiteData empty() {
            return new SiteData(List.of(), List.of(), List.of(), List.of());
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

    /** Describes one ordered news entry, including optional release details for full blog rendering. */
    public record NewsItem(String title, String url, String author, String authorUrl, String avatarUrl,
            String published, String summary, String intro, String descriptionHeading, String description,
            String component, String componentUrl, String version, String versionUrl, String releaseDate,
            String artifactGroup, String artifactPath, String releaseNoteCategory,
            List<NewsDependency> compiledAgainst, List<String> artifacts, List<ReleaseNote> releaseNotes,
            List<NewsContributor> contributors, List<String> tags,
            String previousUrl, String previousTitle, int postNumber, int postTotal) {
        /** Creates an immutable news entry while allowing older curated records to remain summary-only. */
        public NewsItem {
            compiledAgainst = compiledAgainst == null ? List.of() : List.copyOf(compiledAgainst);
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            releaseNotes = releaseNotes == null ? List.of() : List.copyOf(releaseNotes);
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /** Describes a release-note issue rendered on a release page. */
    public record ReleaseNote(int number, String title, String category, String url) {
    }

    /** Describes one named dependency and its production module link. */
    public record NewsDependency(String name, String version, String url) {
    }

    /** Describes a contributor link shared by module and release pages. */
    public record Contributor(String name, String email, int commits, String url) {
    }

    /** Describes a contributor link embedded in a generated blog entry. */
    public record NewsContributor(String name, String url) {
    }
}
