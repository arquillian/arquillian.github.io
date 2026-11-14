# Arquillian Roq site

Work in this directory with Eclipse Temurin **25.0.5+8**, pinned in
`.java-version`. The Maven Wrapper pins Maven **3.10.0** and verifies the
distribution's SHA-256 before its first installation. On Windows, substitute
`mvnw.cmd` for `./mvnw`. Set `JAVA_HOME` or put this JDK on `PATH`.

```sh
./mvnw --version
./mvnw -B -ntp compile test-compile
./mvnw -B -ntp test
```

The first build downloads Maven and Maven dependencies into the normal local
cache. Snapshot-only rendering requires no external content-service credentials,
content API requests, repository fetches, or separately installed Ruby, Pandoc,
Python, or Node. The AsciiDoc renderer is an
embedded Maven dependency. [Web Bundler 3.0](https://docs.quarkiverse.io/quarkus-web-bundler/dev/index.html)
uses Java bindings for Deno/esbuild and Maven web dependencies; it needs no
manually installed Node runtime.

For local development:

```sh
./mvnw quarkus:dev
```

Open <http://127.0.0.1:8182/>. Stop with Ctrl+C. Shared assets are staged by
Maven; restart the command after changing their sources.

To export and preview the static artifact:

```sh
./mvnw -B -ntp -DskipTests -Dquarkus.roq.generator.batch=true package quarkus:run
jwebserver -b 127.0.0.1 -p 8183 -d target/roq
```

Run the tests above before exporting. The export command exits after writing
`target/roq/`; open <http://127.0.0.1:8183/> to inspect those files. `jwebserver`
is included in the JDK. It provides an HTTP preview; browser behavior that
depends on HTTPS and the hosting provider's custom 404 handling still needs the
corresponding deployment review.

Edit `content/` for authored AsciiDoc/HTML pages and blog posts, `templates/` for
Qute layouts and partials, `public/stylesheets/screen.css` for the migrated CSS,
and `data/catalog.json` for the validated six-domain snapshot. The
`public/stylesheets/dashboard.css` file preserves the separate production dashboard
style; its capture details are in `ASSET_ORIGINS.md`. The root `images/`, `javascripts/`,
`favicon.ico`, `CNAME`, `.nojekyll`, `animated-ike.html`, and
`code/dashboard/index.html` remain the shared asset sources. Maven stages
`public/` and those shared assets under ignored
`site-public/`; it derives ignored `data/generated.json`,
`data/blogreleases.json`, and `data/blogarchives.json` from the catalog and
native blog posts. Never edit those staging/index files or `target/` as source.

## Build-time data refresh

The production build refreshes data before deriving pages. Create an
operator-owned YAML file outside the checkout and a copy of `data/catalog.json`
beside it. This complete, narrow example selects one existing repository; replace
the absolute site path with this checkout's root. Relative paths resolve against
the YAML file.

If the default `data/refresh.yml` is absent, the build supplies an in-memory
production fallback using `data/catalog.json`, `.cache/refresh`, the root
repository, the configured GitHub teams and release sources, the five Jira
release projects, and guide history. It requires `SITE_GITHUB_TOKEN`; it never
reads `.github-auth`. A
present configuration file always takes precedence. Missing custom paths still
fail rather than silently falling back.

```yaml
catalog: ./catalog.copy.json
cache: ./.refresh-cache
site_repository: /absolute/path/to/arquillian.github.io
github:
  base_uri: https://api.github.com/
  token_env: SITE_GITHUB_TOKEN
  repositories:
    - repository: arquillian/arquillian-algeron
      contributors: true
      minimum_modules: 1
  users: [38leinad]
lanyrd:
  enabled: false
gravatar:
  enabled: false
community:
  enabled: false
confluence:
  enabled: false
mappings: {}
```

`SITE_GITHUB_TOKEN` is the environment-variable name chosen by this example,
not a built-in credential name. Set it in your environment before running the
example. Omit `token_env` to make unauthenticated requests when the chosen source
permits them. Every enabled source may name its own environment variable with
`token_env`; a configured but missing/blank value fails. Keep token values out
of configuration, command arguments and repository files. Raw-token options and
URI credentials are rejected; legacy credential files are not read. Disabled
optional services must omit `token_env` and neither fetch nor resolve secrets.

Compile first, then validate the same refresh through the build hook:

```sh
./mvnw -B -ntp compile test-compile
./mvnw -B -ntp -Dsite.refresh.skip=false \
  -Dsite.refresh.config=/absolute/path/refresh.yml \
  -DskipTests -Dquarkus.roq.generator.batch=true package quarkus:run
```

The refresh hook runs in `generate-resources`, before generated pages and blog
indexes. It accepts the same `--config` path and preserves the existing cache
directory. A snapshot-only build leaves the hook skipped (the default used by
tests). For a non-publishing validation, add `--dry-run` to the refresh CLI in a
separate invocation; it validates the staged result without changing the
catalog.

To publish the configured snapshot without rendering, use the CLI explicitly:

```sh
./mvnw -B -ntp exec:java \
  -Dexec.mainClass=org.arquillian.site.refresh.RefreshCli \
  -Dexec.args='--config /absolute/path/refresh.yml'
```

Publication replaces only the configured catalog after all requested collectors
and validation succeed. Start with the copied catalog, inspect its diff, and
review changes before applying them to `data/catalog.json`. Run one refresh
writer at a time. Qute rendering never invokes external services directly.

This example is not an all-repository production configuration and has not been
run against live services. Existing exact coordinates and canonical IDs bind
known records. Changed repositories/coordinates or ambiguous artifact paths
need reviewed `repository_bindings`/`components` mappings; new modules, profiles,
guides and announcement overrides require complete curated `create` records.
Automatic announcements for later versions need a reviewed component
`announcement_policy`. Compiled-against aliases need explicit
`dependency_bindings`; guide collection needs exact `guide_bindings`, including
native paths/chapter references where appropriate. No name, avatar, rank or
repository ownership is inferred to fill missing bindings. Add
`arquillian/arquillian-universe-bom` to `github.repositories` when refreshing the
Universe generator's ordered extension inputs.

Manual identity defaults are embedded in the refresh configuration; optional
`manual_identities` and `github_mapping` paths select reviewed YAML alternatives.
Jira, guide history and optional enrichment sources require explicit source
configuration and canonical mappings. Retired Lanyrd/Gravatar/community/
Confluence integrations default disabled; enable only a deliberately configured,
working service. Enabled-source failures abort publication and preserve the
previous catalog. Unreviewed enrichment candidates do not alter presentation.

The artifact workflow compiles, tests, generates with the official Roq GitHub
Action, checks hosting files and shared assets, and uploads `target/roq/` for
review, including `.nojekyll`. Same-repository pull requests also publish a
Surge preview and tear it down when the pull request closes. Configure a scoped
`SURGE_TOKEN` Actions secret before using previews; fork pull requests skip the
preview because GitHub does not expose repository secrets to them. The workflow
does not publish production. The existing production deployment remains
available during migration; deployment cutover requires separate approval.
