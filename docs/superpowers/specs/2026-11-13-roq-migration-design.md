# Awestruct to Roq migration design

Date: 2026-11-13

Status: Written specification approved by the user on 2026-11-13.

## Objective and approved decisions

Replace Awestruct with Roq while maintaining visual fidelity with
<https://arquillian.org/> and preserving the site's public content and behavior.
Use the latest stable, compatible build dependencies, pinned to exact versions.

The user approved these decisions:

1. Refresh configured external data during the site build before rendering; render from the resulting validated snapshots.
2. Convert existing Textile content to AsciiDoc for ongoing authoring.
3. Use Maven/JDK tooling for rendering and refresh, without a separately installed
   Ruby or Awestruct toolchain.
4. Keep Roq alongside Awestruct until visual and functional parity is verified.
5. Preserve the current appearance using Qute layouts and existing presentation
   assets, with implementation divided into phases of at most five changed files.

This specification records the approved architecture. The user approved all
remaining implementation phases on 2026-11-13, removing approval pauses between
phases. Deployment, commits, pushes, and Maven settings changes remain subject
to their existing authorization rules.

## Existing system and branch scope

Use the current branch's implementation and the published production site as
the migration baseline. Exclude changes from other branches from analysis,
reuse, and implementation. Limit repository history inspection to the current
branch.

The current branch is an Awestruct site. Its build is more than a template
renderer: `_ext/pipeline.rb` connects release generation, repository scanning,
identity collection, module pages, guide navigation, pagination, tags, Atom,
redirects, and event exports.

The tracked source includes 284 Textile files. `_ext/textile_plus.rb` adds custom
markup; `_ext/interwiki.rb` resolves special link schemes. `_ext/guide.rb`
generates chapter IDs, translation relationships, and changelogs from Git history.
Layouts and embedded Textile fragments also contain content that needs migration.

The presentation depends on Bootstrap 2, Compass 0.12.7, custom Sass, fonts,
images, and JavaScript. Copying Sass into a modern compiler is insufficient to
preserve its output. Production is the visual reference, including where its
published output differs from a newly executed legacy build.

No `proposals/` directory or applicable `AGENTS-supplemental.md` was found during
reconnaissance. Searches of publicly indexed JIRA results did not identify a
matching migration ticket; this does not establish that none exists.

## Rendering architecture

Roq is a build-time static site generator. GitHub Pages continues serving static
files under the existing domain; production requires no Java application server.
During migration, the legacy and Roq builds have separate inputs and outputs so
neither includes the other's source or generated artifacts.

Use Qute layouts and partials for shared chrome and page-specific structures.
Preserve relevant element structure, CSS classes, IDs, asset paths, responsive
breakpoints, and JavaScript hooks. Use built-in Roq capabilities for collections,
pagination, tags, aliases, and feeds where they reproduce existing behavior;
implement only the missing site-specific behavior.

Convert prose and code examples to editable AsciiDoc. Preserve original public
paths with explicit links where necessary rather than accepting new slug rules.
Set existing fragment IDs explicitly. Treat ordinary code examples as content,
including braces and template-like syntax, rather than evaluating them as Qute.

AsciiDoc produces different HTML structures from Textile. Verify headings,
listings, tables, images, and callouts against existing selectors. Apply small
rendering adaptations or scoped CSS adjustments where needed, with visual
evidence; changing content syntax alone is not proof of fidelity.

## Presentation assets

Capture the CSS actually served by production, together with the referenced
images and fonts, as the initial baseline. Retain its license notices and record
asset origins. Use checked-in, editable plain CSS so the final build does not
depend on Compass or a separate Ruby installation.

Reuse existing repository assets when they match production. Preserve public
paths such as `/images/`, `/stylesheets/`, and `/javascripts/`; preserve references
between CSS, fonts, sprites, and images. Avoid retaining duplicate active copies
of the same source asset merely to support the parallel build.

The existing browser behavior must remain usable: navigation collapse, tabs,
guide menus and scrolling chapters, syntax highlighting, and other interactions
present on migrated pages. The build-dependency upgrade requirement does not
require replacing Bootstrap or redesigning the site. Changes to legacy browser
dependencies must preserve behavior and appearance and be reviewed explicitly.

## Data snapshots and refresh

The data flow is: external services and repository history, Java refresh during
the build, validated snapshots, Java/CDI data access from Qute templates, and
static export.

Snapshots contain the data needed for releases, contributors, modules, guide
history, and any existing event or other generated output found in the inventory.
Keep data in a small number of domain-specific YAML or JSON files, with stable
identifiers and deterministic ordering. Do not duplicate authoritative values
across snapshot files and templates.

Bootstrap historical data from checked-in configuration, available caches,
current-branch history, and published output as needed. Record its provenance. A
retired upstream service must not cause historical pages to disappear or force
an ordinary rendering build to contact that service.

Refresh is a Maven/JDK build step before page derivation and export. It obtains
credentials from the execution environment, never from checked-in files. The
refresh client reuses the existing cache directory and expiry rules, follows
service pagination, validates responses, and stages candidate data before
replacing snapshots. A failed or invalid refresh exits unsuccessfully and leaves
the existing snapshots intact. Tests and snapshot-only previews may set the
build skip flag; Qute rendering itself never performs external I/O.

When the default `data/refresh.yml` is absent, the build constructs an
in-memory configuration from the legacy production pipeline. It uses the Roq
catalog and `roq/.cache/refresh`, requires `SITE_GITHUB_TOKEN`, carries forward
the legacy GitHub teams/release sources, Jira release projects, and guide
history, and keeps retired enrichment services disabled. Explicit configuration
files override these defaults; missing custom paths remain errors.

Rendering requires neither content-service credentials nor external content API
requests. Maven may download dependencies during initial setup. Browser requests
to existing third-party services are a separate concern from build-time data
collection. Maven-managed dependencies may contain an embedded renderer runtime;
contributors do not install a separate Ruby toolchain.

External fetches are scoped as follows:

- A legacy Awestruct production build may fetch GitHub repository, team, issue,
  milestone, and identity data; JIRA project and release notes; Git repository
  history; Maven artifact metadata; Confluence and JBoss Community profiles;
  Gravatar images; and Lanyrd event data. Its dependency bootstrap also uses
  RubyGems. The exact requests depend on environment flags and caches.
- A normal Roq render reads the checked-in site snapshots and local assets. On
  an uncached machine, Maven downloads the pinned Java build dependencies and
  their transitive artifacts. The Maven wrapper downloads its pinned Maven
  distribution; CI downloads its pinned JDK. These are tooling downloads, not
  live site-data collection.
- A site build with refresh enabled fetches the configured GitHub, JIRA, Git,
  identity, event, and artifact sources before deriving pages. Retired or
  unavailable sources stay disabled unless explicitly configured. Responses are
  read from the configured cache when valid, including the legacy one-day rules
  for GitHub milestones and Jira project versions.
- Optional browser comparison downloads the Playwright browser on first use.
  Browser widgets may contact third-party services when the exported pages are
  opened; they do not supply build-time snapshot data.

### Migration step: port Ruby site-data logic to Java and CDI

1. Inventory the current branch's Ruby code that collects, derives, or exposes
   site data. Include repository and artifact scanning in `_ext/repository.rb`
   and `_ext/arquillian.rb`, GitHub and JIRA collectors, `_ext/identities.rb` and
   `_ext/identities/`, Lanyrd event collection, and the data-processing portions
   of release generation and guide history. Build-only scripts and extensions
   are replaced by Roq/Maven capabilities as needed rather than mechanically
   translated to Java.
2. Rewrite the required collection and data-processing behavior in Java. Keep
   external API and Git access in the build-time refresh operation, retaining
   validation, pagination, identity matching, historical data, cache expiry,
   and failure handling. Write the resulting data to the checked-in snapshots
   described above before page derivation.
3. Expose snapshot-backed Java read services to Qute through named CDI beans,
   reusing Roq's data-bean support where it fits. For example, a bean named
   `siteData` could expose releases as `{=cdi:siteData.releases}` with Qute's
   alternative expression syntax enabled. Use the same approach for contributor,
   module, event, and guide-history lookups. Bean initialization and template
   access read local snapshots without fetching external data or invoking Ruby.
4. Replace the corresponding Ruby-backed template data access with the Java/CDI
   interface. Establish this integration for the first homepage and guide slice,
   then migrate the remaining consumers in approved phases. Preserve ordering,
   filtering, identifiers, attribution, and URLs in the rendered output.
5. Verify the Java ports against representative captured data, test refresh
   failures without overwriting good snapshots, and render Qute templates that
   exercise the CDI-backed lookups. Assert that rendering makes no external
   content requests and invokes no Ruby processes. Each Java or Java build-file
   phase also passes `mvn compile test-compile`.

Qute's [CDI bean access](https://quarkus.io/guides/qute-reference/#injecting-beans)
provides the template integration; collection remains a pre-render build step,
not a template-side network call.

## Compatibility contract

Inventory production output and reconcile it with the source before bulk
conversion. Every existing route must map to a migrated page, a preserved static
file, or an intentional redirect with the same destination behavior.

| Area | Required preservation |
| --- | --- |
| Pages and assets | Paths, trailing-slash behavior, case, underscores, translations, relative links, images, fonts, and downloads |
| Guides | Heading fragments, chapters, translations, author attribution, code examples, source/edit links, and historical changelogs |
| Blog and releases | Publication dates, authors, tags, ordering, excerpts, pagination, release details, and existing comment identifiers |
| Generated output | Contributor and module pages, existing API/calendar files, Atom entry identities and release/component metadata |
| Hosting | Domain, `CNAME`, `.nojekyll`, custom 404 behavior, canonical links, and configured static redirect pages |

Preserve the redirect destinations in `_config/redirects.yml`. The current
implementation emits HTML redirect pages; do not assume GitHub Pages supplies
server-side redirect status codes. Preserve existing feed identifiers rather
than changing identity when normalizing display URLs to HTTPS.

## Dependency policy

At investigation time, Roq 2.2.0 is the latest published release. Its release POM
uses Quarkus 3.40.1 and targets Java 21. Quarkus 3.40.1.2 is the latest stable
release returned by the upstream release API and is the initial compatibility
candidate. This pair has not yet been built in this repository.

At the implementation compatibility gate, recheck official releases, select a
supported stable JDK, and verify the newest stable compatible dependency set.
Pin the JDK used in CI, Maven distribution, build plugins, and direct dependency
versions. Use the Quarkus BOM for its managed libraries and align Roq plugins
with the selected Roq release. Do not introduce snapshot or prerelease build
dependencies. Do not independently override managed transitive versions without
evidence that the selected combination needs it.

The extension catalogue labels Roq itself as preview. A numbered release is not
a claim that this extension status has changed.

## Verification and delivery gates

Capture a production route/fragment manifest, representative HTML, and screenshot
baselines. Store bulky generated captures outside published content and record
their revision or checksums. Use fixed desktop and mobile viewports, the same
browser and fonts, and wait for assets to load. Mask only documented volatile
regions. Review visual differences; do not choose a permissive numerical
threshold that hides layout, typography, color, or spacing changes.

| Milestone | Evidence required before expanding scope |
| --- | --- |
| Baseline and compatibility | Route/page-family inventory, production asset references, pinned candidate versions, successful compilation and minimal static export |
| First visible slice | Homepage and a complex guide render through snapshot-backed Java/CDI data access and match the baseline; guide anchors, code blocks, and navigation work on desktop and mobile |
| Page-family migration | Ordinary pages, translated guides, blog/release pages and indexes, contributors, modules, and existing ancillary outputs pass their applicable checks |
| Refresh replacement | Ruby site-data collectors and processing are ported to Java; deterministic snapshots, CDI-backed Qute lookups, pagination, malformed responses, unavailable sources, and failed updates are tested without replacing good data |
| Cutover readiness | Complete route coverage, valid internal links/fragments, stable feed identities, asset loading and interaction checks, plus a reviewed deployable static artifact |

Milestones may require multiple implementation phases. Each phase changes at
most five repository files and includes its applicable verification before the
next phase. All remaining phases are approved; no approval pause is required.
Generated ignored build artifacts
do not expand a source edit into an unreviewable bulk content conversion.

For every Java or Java build-file change, run `mvn compile test-compile` from the
applicable Maven project and fix all errors before reporting completion. Also
run the targeted behavior tests and static export checks appropriate to that
phase. Use a Maven-managed browser test tool for repeatable interactions and
screenshots without requiring a separate Node.js installation.

Review the deployable artifact before switching the GitHub Pages workflow.
Keep the current deployment available until acceptance. Deployment and every
push require explicit authorization; no commit is permitted by the repository
directives. Retire the legacy build only in approved final cleanup phases.

## Boundaries and remaining validation

This migration preserves the site's presentation and publishing behavior. A
redesign, a frontend framework replacement, new site features, and broad prose
rewriting are outside this design.

Implementation planning must account for all existing page families and
collectors, including services that are now unavailable. Do not claim service
availability, successful content conversion, dependency compatibility, or visual
parity until the corresponding gate has produced evidence.

The separate-cleanup-commit instruction conflicts with the explicit prohibition
on commits. Do not commit. If structural work on a file over 300 lines requires
the prescribed dead-code cleanup, perform and verify it as its own approved
phase without a commit. Read files again before every edit and inspect the
result afterward.

## Primary references

- [Roq migration guidance](https://iamroq.dev/docs/migrating/)
- [Roq publishing guidance](https://iamroq.dev/docs/publishing/)
- [Roq extension metadata](https://quarkus.io/extensions/io.quarkiverse.roq/quarkus-roq/)
- [Roq 2.2.0 release POM](https://github.com/quarkiverse/quarkus-roq/blob/2.2.0/pom.xml)
- [Quarkus 3.40.1.2 release](https://github.com/quarkusio/quarkus/releases/tag/3.40.1.2)
