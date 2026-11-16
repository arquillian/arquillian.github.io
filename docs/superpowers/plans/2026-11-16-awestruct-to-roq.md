# Awestruct to Roq Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the Arquillian website from Awestruct/Ruby/Haml/Textile to a Maven/Roq/Java/AsciiDoc build while preserving the production site’s visual appearance and public URL behavior.

**Architecture:** Keep authored content, presentation, and build-time data generation separate. Convert Haml layouts to Qute templates, move authored content to AsciiDoc with `git mv`, and port dynamic providers into typed Java services consumed by Roq page extensions and `@DataMapping`-backed template data. Generate the site with Maven and deploy it through the Roq GitHub Action.

**Tech Stack:** Maven, current compatible Roq release, Quarkus, JDK 27 unless compatibility verification requires the latest supported GA JDK, Qute, AsciiDoc/Asciidoctor, Java HTTP clients, GitHub Actions, and the existing CSS/JavaScript/image assets.

**Spec:** Approved migration design in the preceding conversation on 2026-11-16.

## Global Constraints

- Use live provider fetches by default, with provider-specific caching and staleness checks where available.
- Provide a force-refresh option, standardized as `-Droq.refresh=true` and `ROQ_REFRESH=true`.
- Use `git mv` for content, layout, template, and asset moves or renames before editing their contents.
- Convert all authored Textile and Haml content to AsciiDoc.
- Do not scrape or copy rendered production HTML and wrap it in a Roq template. Production HTML is only a visual comparison target.
- Port every current templated page to AsciiDoc content plus Qute layouts/partials. Existing pure HTML pages may be copied directly into `content/`, then updated to use the new CSS, web assets, and Roq URL helpers.
- Preserve production URL paths, redirects, asset paths, metadata, and responsive visual behavior.
- If Roq changes a generated URL, configure the Roq aliases plugin to redirect the old production URL to the new URL; do not rely only on updating internal links.
- Use Qute for presentation; do not put provider fetching or parsing logic in templates.
- New Java types and methods require Javadoc describing what they do and why.
- Run `mvn compile test-compile` after every Java or Maven build-file phase; fix every resulting error before advancing.
- Visual validation is manual and ad hoc against the same production URLs; do not add screenshot assertions or visual unit tests.
- Each implementation phase may touch no more than 50 files per batch; use parallel subagents for independent work that exceeds 50 files or has no shared-state dependency.
- Do not commit automatically; each completed task must leave a suggested commit boundary for the human maintainer.

## Repository facts and migration map

The current build has no Maven project. Awestruct is configured by `_config/site.yml`, `_ext/pipeline.rb`, and the Ruby extensions under `_ext/`. Presentation is split across `_layouts/*.html.haml`, `_partials/*.haml`, and page-local Haml files. Authored content is primarily `.textile`, with existing AsciiDoc examples in `README.adoc` and `datasheet.asciidoc`.

The main dynamic responsibilities are:

- GitHub authentication, teams, repositories, releases, issues, contributors, and avatars.
- JIRA release notes and component leads.
- Confluence, Gravatar, JBoss Community, and GitHub identity enrichment.
- Repository cloning, Maven/Gradle metadata, tags, commits, and contributor history.
- Generated release blog posts, module pages, identity pages, pagination, tags, Atom output, redirects, and iCalendar event output.

The production visual oracle is the matching URL on `https://arquillian.org`, beginning with `/`, `/features/`, and `/guides/`, followed by representative blog, module, community, and event pages.

## Roq default directory contract

Use Roq’s documented root-level directory structure rather than creating a custom `src/main/resources` site layout:

```text
content/                         # AsciiDoc pages and collections
data/                            # normalized JSON/YAML data for @DataMapping
public/                          # unprocessed images, fonts, favicon, and other static files
web/                             # CSS and JavaScript sources handled by the web bundler
templates/                       # Qute layouts and partials
config/application.properties   # Roq and Quarkus site configuration
pom.xml                          # Maven build
src/main/java/                   # Java providers and Roq extensions
src/test/java/                   # unit and generation tests
```

The migration may use `src/main/resources/` for Java-owned resources only when a Roq extension requires it; authored site content, templates, data, and web assets remain in the root-level directories above.

---

## Phase 0: Preflight and route inventory

### Task 0.1: Capture current repository constraints

**Files:**
- Read: `AGENTS.md`, `AGENTS-supplemental.md`, `RTK.md`
- Read: `README.adoc`, `Gemfile`, `Dockerfile`, `.travis.yml`
- Read: `_config/site.yml`, `_config/redirects.yml`, `_config/redirects.template`

- [ ] Read the current branch status and record unrelated worktree changes.
- [ ] Inspect `git log --follow` and `git blame` for the build entrypoints and every file that will be directly ported.
- [ ] Search the repository for `ARQ-` references, issue numbers, proposals, migration notes, and provider-specific URLs.
- [ ] Confirm that no `proposals/` directory or CodeGraph index exists before using ordinary repository search.
- [ ] Record the current build commands, credentials, cache directories, output directories, and deployment scripts in the migration notes.

**Verification:** The resulting migration notes identify every current build entrypoint, provider, cache, output route, credential, and deployment path before source files are edited.

### Task 0.2: Build the route and asset inventory

**Files:**
- Create: `docs/superpowers/migration/route-inventory.md`
- Read: all page-local `*.haml`, `*.textile`, `*.asciidoc`, and static HTML inputs
- Read: `_layouts/*.html.haml`, `_partials/*.haml`

- [ ] Enumerate every authored route and its source file.
- [ ] Classify each authored page as templated Haml/Textile content or an existing pure HTML page; record the required migration treatment for each class.
- [ ] Enumerate every generated route family and identify the Ruby extension that creates it.
- [ ] Record redirects, pretty-URL transformations, feeds, `.ics` files, and special verification files.
- [ ] Record the old production URL and proposed Roq URL for every route whose generated path may change, and mark each pair that requires a Roq alias.
- [ ] Record every CSS, JavaScript, image, font, and favicon path referenced by layouts or pages.
- [ ] Mark the first manual visual-check set as `/`, `/features/`, `/guides/`, `/blog/`, and one representative page from `/community/`.

**Verification:** Every current public route family has one listed source or generator owner, and every route in the first visual-check set has a production URL.

### Task 0.3: Establish the first commit boundary

**Files:**
- Modify: `docs/superpowers/migration/route-inventory.md`

- [ ] Copy the phase checklist into the execution issue or branch notes.
- [ ] Create the first migration commit only after the route inventory is reviewed by the maintainer.
- [ ] Do not alter application source in this task.

**Verification:** The maintainer can review the inventory and approve Phase 1 without needing to infer missing route or provider scope.

---

## Phase 1: Maven/Roq foundation and deployment skeleton

### Task 1.1: Create the Maven/Roq project skeleton

**Files:**
- Create: `pom.xml`
- Create: `config/application.properties`
- Create: `src/main/java/org/arquillian/website/SiteConfiguration.java`
- Create: `src/test/java/org/arquillian/website/SiteConfigurationTest.java`

**Interfaces:**
- `SiteConfiguration` exposes the site URL, cache directory, refresh flag, and build profile to later generators.
- The configuration reads `ROQ_REFRESH` and `roq.refresh`, with the system property taking precedence.

- [ ] Add the Roq and Quarkus Maven dependencies at mutually compatible pinned versions.
- [ ] Add the Roq aliases plugin and configure it as the single mechanism for redirects caused by Roq URL changes.
- [ ] Keep Roq’s root-level `content`, `data`, `public`, `web`, and `templates` defaults; use `config/application.properties` only for site settings, collection definitions, output generation mode, AsciiDoc support, Qute alternate expression syntax, and site URL.
- [ ] Implement `SiteConfiguration` with Javadoc and a small test covering default and forced-refresh values.
- [ ] Run `mvn compile test-compile` and fix all compile errors.
- [ ] Commit only the Maven/Roq skeleton and its configuration test.

**Verification:** `mvn compile test-compile` passes, and the configuration test distinguishes normal cache behavior from `-Droq.refresh=true`.

### Task 1.2: Add the first generated page

**Files:**
- Create: `content/index.adoc`
- Create: `templates/layouts/page.html`
- Modify: `config/application.properties`
- Create: `src/test/java/org/arquillian/website/SiteGenerationTest.java`

- [ ] Add the homepage at `content/index.adoc` and render it through `templates/layouts/page.html`.
- [ ] Configure output to be generated under `target/roq`.
- [ ] Add a generation test that asserts the output file exists and contains the expected page title.
- [ ] Run `mvn compile test-compile`.
- [ ] Run the Roq batch generation command and inspect `target/roq/index.html`.
- [ ] Commit the first working Roq page.

**Verification:** A clean checkout can generate one static AsciiDoc page through Maven/Roq without Ruby, Awestruct, or Node runtime setup.

### Task 1.3: Add the Roq GitHub Actions workflow

**Files:**
- Create: `.github/workflows/deploy.yml`
- Modify: `README.adoc`

- [ ] Configure checkout, the Roq GitHub Action, JDK 27, Maven cache, and GitHub Pages deployment.
- [ ] Pass provider credentials through GitHub Actions secrets rather than committing credentials.
- [ ] Restore and save the Roq provider cache directory between workflow runs.
- [ ] Add a manual workflow dispatch path that accepts a force-refresh input and maps it to `ROQ_REFRESH=true`.
- [ ] Document local generation and workflow deployment commands in `README.adoc`.
- [ ] Commit the workflow separately from the content conversion.

**Verification:** The workflow can build the foundation site on a branch without publishing production content, and the force-refresh input reaches the build process.

**Phase gate:** Stop here for maintainer review. Do not begin Phase 2 until the Maven build, generated artifact, and workflow skeleton are accepted.

---

## Phase 2: Qute visual shell and static asset parity

### Task 2.1: Move presentation assets with Git history

**Files:**
- Move with `git mv`: `_layouts/` to `templates/layouts/`
- Move with `git mv`: `_partials/` to `templates/partials/`
- Move with `git mv`: `stylesheets/` to `web/`
- Move with `git mv`: `javascripts/` to `web/`
- Move with `git mv`: `images/` to `public/images/`
- Move with `git mv`: static root files such as `favicon.ico`, `CNAME`, and `.nojekyll` to `public/` when the Roq GitHub Action requires them there.

- [ ] Perform each source move with `git mv` before changing file contents.
- [ ] Preserve relative asset paths until the Qute shell is visually verified.
- [ ] Record any path that must change because Roq serves resources from a different root.
- [ ] Do not delete the old location until the new build references every moved asset.

**Verification:** `git diff --summary` reports renames for moves with sufficient similarity, and the route inventory has no unowned asset path.

### Task 2.2: Convert the base and default layouts to Qute

**Files:**
- Modify: `templates/layouts/base.html`
- Modify: `templates/layouts/page.html`
- Modify: `templates/partials/navigation.html`
- Modify: `templates/partials/footer.html`
- Create: `src/test/java/org/arquillian/website/TemplateRenderingTest.java`

- [ ] Port title, description, author, canonical URL, stylesheet, script, body-class, navigation, footer, analytics, and social-link behavior.
- [ ] Preserve menu labels, active-route behavior, footer text, external links, and production asset URLs.
- [ ] Replace Ruby conditionals and loops with Qute expressions while keeping escaping explicit.
- [ ] Add a rendering test for the root page with empty optional metadata and populated site metadata.
- [ ] Run `mvn compile test-compile`.
- [ ] Generate the site and perform manual comparisons of `/` at desktop and mobile widths.
- [ ] Commit the base shell only after the visual check passes.

**Verification:** The generated homepage has the same structure, spacing, typography, colors, navigation state, footer, image treatment, and responsive behavior as `https://arquillian.org/`.

### Task 2.3: Convert section layouts and static primary pages

**Files:**
- Modify: Qute layouts under `templates/layouts/` for blog, guide, identity, module, release, and invasion pages
- Move with `git mv`: `index.html.haml`, `features/index.html.haml`, `docs/index.html.haml`, `community/index.html.haml`, and `404.html.haml` into `content/` AsciiDoc-backed Roq page locations
- Modify: `content/*.adoc` for the primary pages

- [ ] Convert only the primary static routes in this batch; leave generated route families for Phase 4.
- [ ] Port templated pages from their source content and layout files; do not use the production page HTML as migration input.
- [ ] Copy existing pure HTML pages directly into `content/` with `git mv`, then update their CSS, asset, and Roq URL references without wrapping their rendered production output in a new template.
- [ ] Preserve page-specific classes, headings, sidebars, guide navigation, code highlighting, and image dimensions.
- [ ] Preserve front matter names needed by later generators: `layout`, `title`, `author`, `date`, `tags`, `guide_summary`, `guide_group`, and `guide_order`.
- [ ] Add a Roq alias for every primary route whose generated URL differs from the production URL, and verify the old URL redirects to the new URL.
- [ ] Use `git mv` before converting each page.
- [ ] Run `mvn compile test-compile`.
- [ ] Manually compare `/features/`, `/guides/`, `/docs/`, `/community/`, and `/404.html` with production.
- [ ] Commit the static visual shell separately from dynamic provider work.

**Verification:** All primary navigation routes render through Qute and AsciiDoc with no Ruby/Haml dependency.

**Phase gate:** Stop here for maintainer visual review. Approval must cover root, features, guides, docs, community, 404, responsive layout, and asset loading before bulk content conversion.

---

## Phase 3: Full AsciiDoc content conversion

### Task 3.1: Define the AsciiDoc front matter contract

**Files:**
- Create: `docs/superpowers/migration/asciidoc-front-matter.md`
- Modify: `src/test/java/org/arquillian/website/FrontMatterCompatibilityTest.java`
- Read: `README.adoc` content-convention sections

- [ ] Define the exact AsciiDoc attributes for titles, dates, authors, tags, layouts, summaries, guide groups, guide order, translations, and release metadata.
- [ ] Define filename-to-route rules for blog posts, guides, translations, and release pages.
- [ ] Define the alias entry format for any old production URL that differs from the Roq-generated URL.
- [ ] Define how Textile custom tags, interwiki links, code blocks, notes, warnings, and image references translate to AsciiDoc.
- [ ] Add fixture-based tests for one blog post, one guide, one translated guide, and one release note.
- [ ] Run `mvn compile test-compile`.
- [ ] Commit the contract and fixtures before converting the full corpus.

**Verification:** A contributor can create a new AsciiDoc blog post or guide using the documented attributes, and the compatibility tests reject missing or malformed required metadata.

### Task 3.2: Convert blog content in bounded batches

**Files:**
- Move with `git mv`: each selected `blog/*.textile` batch to `content/posts/*.adoc`
- Modify: each moved AsciiDoc file
- Modify: `templates/layouts/blog.html`
- Modify: `templates/layouts/post.html`
- Modify: `templates/partials/post.html`

- [ ] Convert no more than 50 content files per batch.
- [ ] Configure the default `posts` collection in `config/application.properties` and preserve each existing `/blog/` route through front matter or a Roq alias.
- [ ] Preserve dates, titles, authors, tags, release metadata, image paths, links, code blocks, and comments behavior.
- [ ] Use the existing post fixture test after each batch.
- [ ] Run `mvn compile test-compile` after each Java or template change.
- [ ] Render `/blog/`, one normal post, and one release post for manual comparison.
- [ ] Record any parser exception as a focused conversion fix, not a broad template workaround.
- [ ] Commit each reviewed batch with a message naming its content range.

**Verification:** Converted posts render with the same typography, metadata placement, code styling, image placement, pagination controls, and tag links as production.

### Task 3.3: Convert guide, feature, community, invasion, and static content batches

**Files:**
- Move with `git mv`: `guides/**/*.textile` batches to `content/guides/**/*.adoc`
- Move with `git mv`: remaining authored page-local `*.html.haml` files to `content/**/*.adoc`; keep shared layout and partial files as Qute templates
- Modify: corresponding AsciiDoc files and route metadata

- [ ] Convert no more than 50 source files per batch.
- [ ] Configure the `guides` collection in `config/application.properties` with its page layout and future-content behavior so guide pages follow Roq’s collection model.
- [ ] Preserve translated-guide routes and language metadata.
- [ ] Convert every templated Haml/Textile page in these batches to AsciiDoc and Qute; copy only pages classified as pure HTML directly.
- [ ] Preserve guide heading IDs, table of contents behavior, code blocks, admonitions, images, and chapter navigation.
- [ ] Preserve community, invasion, API, verification, license, keybase, and root special-file routes.
- [ ] Run `mvn compile test-compile` after every Java/build-file change.
- [ ] Manually check one converted page from each content family.
- [ ] Commit each batch after route and visual checks.

**Verification:** The route inventory has no remaining authored Textile or page-local Haml inputs except files explicitly classified as Qute templates or generated sources.

**Phase gate:** Stop for review of the converted content inventory, route redirects, and representative manual checks before porting dynamic generators.

---

## Phase 4: Java data providers, caching, and generated pages

### Task 4.1: Implement the shared HTTP/cache contract

**Files:**
- Create: `src/main/java/org/arquillian/website/data/CachePolicy.java`
- Create: `src/main/java/org/arquillian/website/data/CacheEntry.java`
- Create: `src/main/java/org/arquillian/website/data/CachedHttpClient.java`
- Create: `src/main/java/org/arquillian/website/data/RefreshPolicy.java`
- Create: `src/test/java/org/arquillian/website/data/CachedHttpClientTest.java`

**Interfaces:**
- `CachedHttpClient.fetch(URI, CachePolicy)` returns a response body plus freshness metadata.
- `CachePolicy` contains the cache key, maximum age, and whether conditional requests are supported.
- `RefreshPolicy` resolves normal, stale, and forced-refresh behavior from `roq.refresh`/`ROQ_REFRESH`.

- [ ] Implement cache reads before network calls unless force-refresh is enabled.
- [ ] Store response body, fetched time, ETag, Last-Modified, status, and content type in a stable cache format.
- [ ] Revalidate stale entries with conditional headers where supported.
- [ ] Return the stale entry only when the provider explicitly permits stale fallback; otherwise fail with the provider URL and cache key.
- [ ] Add tests for fresh hit, stale revalidation, forced refresh, missing cache, HTTP 304, and provider failure.
- [ ] Add Javadoc to every new public type and method.
- [ ] Run `mvn compile test-compile`.
- [ ] Commit the cache contract independently.

**Verification:** Repeated builds avoid unnecessary network requests, stale entries are revalidated, and `-Droq.refresh=true` bypasses all cache reads.

### Task 4.2: Port provider clients and typed records

**Files:**
- Create: `src/main/java/org/arquillian/website/data/github/*`
- Create: `src/main/java/org/arquillian/website/data/jira/*`
- Create: `src/main/java/org/arquillian/website/data/identity/*`
- Create: `src/main/java/org/arquillian/website/data/repository/*`
- Create: `src/test/java/org/arquillian/website/data/*/*Test.java`

- [ ] Port GitHub authentication, teams, repositories, releases, issues, contributors, and avatars from `_ext/github.rb`, `_ext/repository.rb`, and `_ext/identities/github.rb`.
- [ ] Port JIRA project, component-lead, and release-note retrieval from `_ext/jira.rb`.
- [ ] Port identity enrichment and mapping from `_ext/identities.rb` and its provider crawlers.
- [ ] Port repository clone, tag, commit, Maven/Gradle metadata, and contributor extraction from `_ext/arquillian.rb` and `_ext/repository.rb`.
- [ ] Keep provider DTOs separate from page view models.
- [ ] Add fixtures for successful responses, pagination, missing fields, rate-limit responses, and provider errors.
- [ ] Add Javadoc to all public Java types and methods.
- [ ] Run `mvn compile test-compile`.
- [ ] Commit each independent provider batch separately.

**Verification:** Provider tests run without network access, and live integration checks can be enabled separately with credentials.

### Task 4.3: Port page extensions and `@DataMapping` integration

**Files:**
- Create: `src/main/java/org/arquillian/website/roq/GeneratedDataExtension.java`
- Create: `src/main/java/org/arquillian/website/roq/GeneratedPageExtension.java`
- Create: `src/main/java/org/arquillian/website/data/model/*`
- Create: `data/*` or the generated-data staging directory selected by the Roq lifecycle spike
- Create: `src/test/java/org/arquillian/website/roq/GeneratedPageExtensionTest.java`

**Interfaces:**
- `GeneratedDataExtension` fetches and normalizes provider data before page rendering.
- `GeneratedPageExtension` creates generated routes and supplies typed page models to Qute.
- Generated models expose stable names for components, releases, identities, sessions, contributors, and release notes.

- [ ] Run a lifecycle spike to confirm whether generated JSON/YAML can be materialized before `@DataMapping` binding.
- [ ] Use `@DataMapping` for normalized generated data when Roq binds it in the required phase.
- [ ] Use a page extension with typed models when route creation or lifecycle timing requires it.
- [ ] Port generated modules, identities, releases, release posts, and event `.ics` output.
- [ ] Preserve indexification, pretty URLs, pagination, tag pages, Atom output, and redirects.
- [ ] Register a Roq alias for every generated route whose Roq URL differs from its production URL, including old release, module, identity, and feed paths.
- [ ] Port Lanyrd parsing into an isolated provider with explicit failure behavior; do not let unavailable events prevent unrelated primary pages from rendering unless the route requires event data.
- [ ] Add tests for route names, page metadata, generated filenames, and `.ics` output.
- [ ] Run `mvn compile test-compile`.
- [ ] Commit the data binding and each page-family generator separately.

**Verification:** Generated routes match the route inventory, generated page models render without provider logic in Qute, and force-refresh behavior is observable in build logs.

### Task 4.4: Complete generated-page templates

**Files:**
- Modify: `templates/layouts/module.html`
- Modify: `templates/layouts/release.html`
- Modify: `templates/layouts/identity.html`
- Modify: `templates/layouts/invasion.html`
- Modify: generated-page partials under `templates/partials/`

- [ ] Convert the remaining Haml generated-page layouts to Qute.
- [ ] Preserve release notes, dependency tables, contributor lists, avatars, badges, dates, external links, and guide/module cross-links.
- [ ] Preserve all classes and markup relationships required by the existing CSS and JavaScript.
- [ ] Run `mvn compile test-compile`.
- [ ] Manually compare representative module, release, identity, and event pages with production.
- [ ] Commit templates separately from provider code.

**Verification:** Generated sections visually match production for representative pages, including empty, missing, and populated data states.

**Phase gate:** Stop for maintainer review of live-fetch, cached-fetch, forced-refresh, generated routes, and representative visual output before removing Awestruct.

---

## Phase 5: Cutover, cleanup, and final verification

### Task 5.1: Replace build documentation and developer commands

**Files:**
- Modify: `README.adoc`
- Modify: `Dockerfile` or remove it if the workflow fully replaces the container contract
- Modify: `.gitignore`
- Modify: `.github/workflows/deploy.yml`

- [ ] Document JDK, Maven, local Roq dev mode, batch generation, cache directory, credentials, and force-refresh usage.
- [ ] Document the live-fetch default and cache-staleness behavior.
- [ ] Document GitHub Action secrets and manual force-refresh workflow dispatch.
- [ ] Remove instructions for Ruby, Bundler, Awestruct, JRuby, and obsolete Docker commands.
- [ ] Run `mvn compile test-compile`.
- [ ] Commit documentation and workflow cleanup separately.

**Verification:** A new contributor can clone the repository, configure credentials, run the Maven build, preview the site, and trigger a forced-refresh deployment without reading historical Awestruct instructions.

### Task 5.2: Remove the obsolete Ruby build

**Files:**
- Delete after final verification: `Gemfile`
- Delete after final verification: `_ext/`
- Delete after final verification: `_layouts/` and `_partials/` remnants
- Delete after final verification: obsolete `_bin/` and `_scripts/` build/deploy files
- Modify: `.travis.yml` if no longer used, or delete it after workflow replacement

- [ ] Confirm no active Maven/Roq source references Ruby, Awestruct, Haml, Textile, Compass, or the old deployment scripts.
- [ ] Remove only files proven obsolete by the route inventory and build search.
- [ ] Use `git rm` for genuine deletions; retain moved history through the earlier `git mv` operations.
- [ ] Run a clean checkout build with the old Ruby toolchain unavailable.
- [ ] Run `mvn compile test-compile` and the complete generation command.
- [ ] Commit cleanup only after the generated output is verified.

**Verification:** The website builds and deploys with Maven/Roq alone, and repository search finds no active Awestruct pipeline or Ruby build dependency.

### Task 5.3: Perform the final manual production comparison

**Files:**
- Read: `docs/superpowers/migration/route-inventory.md`
- Read: generated output under `target/roq`

- [ ] Start the generated site locally with the Roq serving command.
- [ ] Compare the same URLs for `/`, `/features/`, `/guides/`, `/blog/`, `/community/`, `/docs/`, one module page, one release page, one identity page, and one event page.
- [ ] Check desktop and mobile widths, navigation states, typography, spacing, image dimensions, code blocks, tables, footer, redirects, and external links.
- [ ] Request every old/new URL pair from the route inventory and verify that each old URL redirects through the Roq aliases plugin.
- [ ] Repeat the build once with cache and once with `-Droq.refresh=true`.
- [ ] Record visual discrepancies as focused follow-up tasks instead of silently accepting them.
- [ ] Obtain the maintainer’s explicit human sign-off before enabling the workflow for production deployment.

**Verification:** The maintainer confirms visual parity against production for the agreed primary routes and representative generated pages, with no automated visual test added.

---

## Required verification commands

Run these from the repository root at every Java/build-file checkpoint:

```bash
mvn compile test-compile
```

Run these before the final cutover:

```bash
ROQ_REFRESH=false mvn -B package quarkus:run
ROQ_REFRESH=true mvn -B package quarkus:run
```

Use this Roq batch-generation command from the repository root:

```bash
QUARKUS_ROQ_GENERATOR_BATCH=true mvn -B package quarkus:run
```

Record the same command in `README.adoc` and the workflow.

## Commit sequence

Keep commits reviewable and independently revertible:

1. Route inventory and migration notes.
2. Maven/Roq foundation.
3. First generated page and build test.
4. GitHub Actions deployment skeleton.
5. Asset moves and Qute visual shell.
6. Primary static AsciiDoc pages.
7. AsciiDoc front matter contract and conversion batches.
8. Shared cache contract.
9. Provider batches.
10. Generated page extensions and templates.
11. Documentation and workflow cutover.
12. Awestruct/Ruby cleanup.

Do not combine content conversion, provider rewrites, and build-system cleanup in one commit.

## Plan self-review

- Spec coverage: live fetch, cache staleness, forced refresh, Maven, Roq, latest compatible JDK, AsciiDoc conversion, Java generators, `@DataMapping`/page extensions, `git mv`, GitHub Action deployment, primary-route visual checks, and human-only visual acceptance all have explicit tasks.
- Placeholder scan: no unfinished markers or unowned follow-up appears in the execution tasks; version compatibility is an explicit Phase 1 verification gate rather than an assumed value.
- Type consistency: `SiteConfiguration`, `CachedHttpClient`, `CachePolicy`, `RefreshPolicy`, `GeneratedDataExtension`, and `GeneratedPageExtension` are defined before later tasks consume them.
- Scope control: visual automation, unrelated redesign, provider feature additions, and hosting changes beyond GitHub Pages are excluded.
