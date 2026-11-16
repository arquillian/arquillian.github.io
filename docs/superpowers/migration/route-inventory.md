# Arquillian Website Route and Build Inventory

Status: Phase 0 inventory for the Awestruct-to-Roq migration.

Source branch: `mig2roq`  
Source revision: `5560d6f313` (`Update plan to forbid scraping and wrapping`)  
Production visual oracle: `https://arquillian.org`

This inventory describes the current source of truth. Production-rendered HTML is only a visual comparison target; it is not a migration source for templated pages.

## Phase 0 verification

- The checkout is an isolated Git worktree on branch `mig2roq`.
- The worktree was clean before this inventory was created.
- No `proposals/` directory exists in the repository.
- No `.codegraph/` directory exists; ordinary Git search was used as required.
- The current branch contains only migration-plan commits after `develop`; no alternate migration implementation was used.
- No Maven project exists yet: there is no `pom.xml`.
- The current build is Ruby/Awestruct-based and produces `_site/`.

## Source counts

Counts exclude `_backup/` unless explicitly stated.

| Source family | Count | Current location | Migration treatment |
| --- | ---: | --- | --- |
| Page/layout Haml inputs | 39 | repository root, content directories, `_layouts/`, `_partials/` | Shared Haml becomes Qute; authored/page-local Haml becomes AsciiDoc unless it is a pure HTML source elsewhere |
| Textile content | 284 | `blog/`, `guides/`, `_templates/` | Move with `git mv`, convert to AsciiDoc, preserve routes with front matter or Roq aliases |
| AsciiDoc sources | 2 | `README.adoc`, `datasheet.asciidoc` | Reuse or relocate into Roq content/documentation locations as applicable |
| Pure HTML inputs outside `_backup/` | 3 | `animated-ike.html`, `code/dashboard/index.html`, `images/blog/arquillian-graphene-2.1.0.Alpha1/arquillian_report.html` | Copy directly with `git mv` when becoming site content; update CSS, assets, and Roq URL helpers without wrapping production HTML in a template |
| Shared Haml layouts | 11 | `_layouts/` | Move with `git mv` to `templates/layouts/`, then port to Qute |
| Shared Haml partials | 1 | `_partials/post.html.haml` | Move with `git mv` to `templates/partials/`, then port to Qute |
| Stylesheet files | 6 | `stylesheets/` | Move with `git mv` to `web/`; preserve compiled visual behavior |
| JavaScript files | 7 | `javascripts/` | Move with `git mv` to `web/`; preserve runtime behavior |
| Image files | 209 | `images/` | Move with `git mv` to `public/images/` |

## Authored route inventory

### Primary navigation and static pages

| Current route | Current source | Source type | Roq destination/treatment |
| --- | --- | --- | --- |
| `/` | `index.html.haml` | Templated Haml | `content/index.adoc` with Qute `base`/`page` layouts |
| `/features/` | `features/index.html.haml` | Templated Haml | `content/features/index.adoc` with Qute page layout |
| `/docs/` | `docs/index.html.haml` | Templated Haml | `content/docs/index.adoc` with Qute page layout |
| `/guides/` | `guides/index.html.haml` plus `Guide::Index` | Templated Haml plus generated index | `content/guides/index.adoc` plus Roq collection configuration |
| `/blog/` | `blog/index.html.haml` plus `Posts`, paginator, tagger, and tag cloud extensions | Templated Haml plus generated collection pages | `content/posts/` plus Qute `post`/index/tag layouts |
| `/community/` | `community/index.html.haml` | Templated Haml | `content/community/index.adoc` with Qute page layout |
| `/code/` | `code/index.html.haml` | Templated Haml | `content/code/index.adoc` with Qute page layout |
| `/invasion/` | `invasion/index.html.haml` | Templated Haml | `content/invasion/index.adoc` with Qute invasion layout |
| `/modules/` | `modules/index.html.haml` plus `Modules::Page` | Templated Haml plus generated pages | Generated Roq module collection/pages under `content/` using typed Java models |
| `/404.html` | `404.html.haml` | Templated Haml | `content/404.adoc` or a direct Roq-compatible page with the existing visual treatment |

### Secondary authored pages

| Route family | Current sources | Source type | Migration treatment |
| --- | --- | --- | --- |
| `/guides/<slug>` and translated guide routes | 48 files under `guides/*.textile` | Textile with guide front matter | Move to `content/guides/**/*.adoc`; configure the `guides` collection and preserve language suffix routes |
| `/blog/<date>/<slug>` and release posts | 235 files under `blog/*.textile`, `blog/_release.html.haml` | Textile posts plus Haml release template | Move posts to `content/posts/**/*.adoc`; generate release pages from Java models and Qute layouts |
| `/community/contributors` | `community/contributors.html.haml` | Templated Haml | Port to AsciiDoc/Qute; contributor data remains generated |
| `/community/nobles` | `community/nobles.html.haml` | Templated Haml with historical issue links | Port to AsciiDoc/Qute; preserve `issue://` links through the interwiki equivalent |
| `/community/webchat` | `community/webchat.html.haml` | Templated Haml | Port to AsciiDoc/Qute |
| `/community/<github-id>` | `community/_identity.html.haml` plus `Identities::Page` | Generated identity page | Generate from typed identity models and Qute identity layout |
| `/code/architecture`, `/code/builds`, `/code/issues` | `code/architecture.html.haml`, `code/builds.html.haml`, `code/issues.html.haml` | Templated Haml | Port to AsciiDoc/Qute; preserve external data links and embedded scripts |
| `/code/dashboard/` | `code/dashboard/index.html` | Existing pure HTML | Copy directly to `content/code/dashboard/index.html`; update CSS, assets, and Roq URL helpers only |
| `/invasion/<topic>` | 8 Haml pages under `invasion/` | Templated Haml | Port to AsciiDoc/Qute invasion layout |
| `/api/jbossorg/arquillian.properties` | `api/jbossorg/arquillian.properties.haml` | Haml data/file page | Port to a direct Roq content file while preserving the exact output path and property format |
| `/animated-ike.html` | `animated-ike.html` | Existing pure HTML | Copy directly into `public/` or `content/` according to the required output path; update asset references only |
| Verification and special files | `google8ad3dd04249bcf67.html.haml`, `keybase.txt.haml`, `license.txt`, `datasheet.asciidoc`, `CNAME`, `favicon.ico`, `.nojekyll` | Mixed Haml, text, AsciiDoc, and static files | Preserve exact output paths; use `content/` for rendered files and `public/` for files served as-is |

The full source manifest is reproducible with:

```bash
rg --files -g '!_backup/**' | sort
```

The migration must classify each file from that manifest as shared template, authored content, generated template, pure HTML, static public asset, or obsolete build support before moving it.

## Generated route inventory

The current generator ownership comes from `_ext/pipeline.rb` and the referenced Ruby extensions.

| Output family | Current owner | Inputs/data | Roq port |
| --- | --- | --- | --- |
| Module index and module pages | `Awestruct::Extensions::Modules::Page`, `Awestruct::Extensions::Repository`, `_ext/arquillian.rb`, `modules/index.html.haml`, `modules/_module.html.haml` | GitHub repositories, cloned project POMs/Gradle files, tags, commits, contributors | Java repository/model services plus generated Roq pages and Qute module templates |
| Identity index and identity pages | `Identities::Collect`, `Identities::Crawl`, `Identities::Page`, `Identities::Cache`, `community/_identity.html.haml` | GitHub teams/users, Gravatar, Confluence, JBoss Community, cached identity data | Java identity providers, normalized `data/` records or page extension models, Qute identity templates |
| Release blog pages | `Releases::Posts`, `_layouts/release.html.haml`, `_layouts/post.html.haml`, `_ext/arquillian.rb` | Repository tags/commits, existing release posts, JIRA/GitHub release notes, contributors | Java release model/page extension, `content/posts/`, Qute release/post layouts |
| GitHub release notes | `_ext/github.rb` | GitHub milestones and closed issues for configured repositories | Cached Java GitHub client and typed release-note model |
| JIRA release notes and leads | `_ext/jira.rb` | JIRA project/component/release endpoints and configured project IDs | Cached Java JIRA client and typed release-note/component models |
| Blog collection | `Awestruct::Extensions::Posts`, `AutoTag`, `Paginator`, `Tagger`, `TagCloud`, `_layouts/blog.html.haml` | Converted posts and front matter | Roq `posts` collection, Qute layouts, pagination/tagging configuration |
| Atom feed | `PatchedAtomizer` at `/blog/atom.xml` | Blog collection plus custom release/component fields | Roq feed/page extension preserving `/blog/atom.xml` |
| Guide index | `Guide::Index.new('/guides', 15)`, `_ext/guide.rb`, `guides/index.html.haml` | Guide front matter and headings | Roq `guides` collection, Qute index/layout, preserved ordering and heading IDs |
| Event pages and calendar | `Lanyrd::Search`, `Lanyrd::Export`, `_ext/lanyrd.rb`, `_ext/lanyrd.export.haml` | Lanyrd search/detail HTML, future sessions, speaker data | Isolated Java event provider and `.ics` page extension at `/invasion/events/arquillian.ics` |
| Redirects | `RedirectCreator`, `_ext/redirect.rb`, `_config/redirects.yml` | Four Pact-to-Algeron legacy mappings | Roq aliases plugin for every old/new URL pair, including any Roq-generated route changes |
| Asset fingerprinting | `AssetFingerprinter`, `_ext/asset_fingerprinter.rb` | CSS/JS source mtime or Git SHA | Roq/web-bundler asset handling; preserve cache-busting behavior only if visual/deployment checks require it |
| Interwiki links | `_ext/interwiki.rb` | `issue://`, `profile://`, and related link schemes | Qute/AsciiDoc link helper preserving issue and profile destinations |

## URL and redirect map

The current explicit redirect file contains these mappings:

| Old URL | Current target |
| --- | --- |
| `/blog/2016/09/23/arquillian-pact-1-0-0-Alpha1` | `/blog/2016/09/23/arquillian-algeron-1-0-0-Alpha1` |
| `/blog/2016/09/29/arquillian-pact-1-0-0-Alpha2` | `/blog/2016/09/29/arquillian-algeron-1-0-0-Alpha2` |
| `/blog/2016/11/03/arquillian-pact-1-0-0-Alpha3` | `/blog/2016/11/03/arquillian-algeron-1-0-0-Alpha3` |
| `/blog/2016/11/14/arquillian-pact-1-0-0-Alpha4` | `/blog/2016/11/14/arquillian-algeron-1-0-0-Alpha4` |

During each migration batch, compare the generated Roq URL with the old production URL. If Roq generates a different path, register a Roq aliases-plugin redirect from the old URL to the new URL. Updating internal links alone is insufficient.

Awestruct’s `Indexifier` currently turns page files into pretty URLs, with the documented example `features.html -> /features/index.html`. This behavior must be explicitly mapped to Roq content paths and checked against production.

## Provider, credential, and cache inventory

### Credentials and external services

| Provider | Current credential/configuration | Current use |
| --- | --- | --- |
| GitHub | `.github-auth`; `github_organization: arquillian`; teams 146647, 125938, 146643 | Repositories, teams, releases, issues, contributors, avatars, repository metadata |
| JBoss Confluence | `.jboss-auth` | Identity crawling against `https://docs.jboss.org/author` |
| JIRA | Public/configured endpoint `https://issues.jboss.org`; project keys and IDs in `_ext/pipeline.rb` | Project data, component leads, release notes |
| Lanyrd | No credential in current build | Event/session search and `.ics` export |
| Maven/JBoss repositories | `_bin/settings-jboss-community.xml` | Artifact/POM/Javadoc metadata and published artifact links |

No credentials are committed. The Roq build must read credentials from GitHub Actions secrets or local ignored files.

### Current cache and clone locations

| Data | Current location | Migration note |
| --- | --- | --- |
| Awestruct temporary/cache tree | `_tmp/` | Replace with an ignored Roq cache directory persisted by GitHub Actions |
| Identity cache | `_tmp/datacache/identities.yml` | Normalize into typed Java data/cache records |
| GitHub REST cache | `_tmp/restcache/github/` | Preserve cache keys and add freshness/conditional-request behavior |
| JIRA cache | `_tmp/jira/` and documented `_tmp/jira/jira-ARQ.json` | Preserve provider-specific cache invalidation and add force-refresh |
| Lanyrd HTML cache | `_tmp/lanyrd/` | Preserve cached search/detail inputs; isolate provider failure |
| Repository clones | `/tmp/arqrepos` from `_config/site.yml`, otherwise `_tmp/repos/` | Keep clones outside authored content and make refresh behavior explicit |
| Ruby gems | `.gems/` and CI `$HOME/cache/.gems` | Remove after Maven/Roq workflow is verified |

The current scripts copy `_tmp/` into and out of CI cache storage. The replacement workflow must cache the new Roq provider directory without committing generated data.

## Build, output, and deployment inventory

### Current commands

| Purpose | Current command or script |
| --- | --- |
| Local live preview | `awestruct -d` |
| Development server | `awestruct --server -P development` |
| Production generation | `awestruct -P production -g` or `awestruct --force -g -P production` |
| Regeneration helper | `_bin/regenerate.sh`, calls `awestruct -s` |
| Docker setup | `Dockerfile`, `docker build -t arquillian/blog .`, containerized Awestruct scripts |
| CI build | `.travis.yml` calls `_scripts/prepare_build_prod_and_run.sh` and `_scripts/travis-verify-publish.sh` |
| Deployment | `_bin/deploy-production.sh` and `_scripts/deploy_push_verify.sh` |

### Current output and deployment

- Awestruct output: `_site/`.
- Deployment working clone: `_deploy/` by default.
- Production profile: `base_url: http://arquillian.org`, GitHub Pages host, branch `master`, repository `upstream`.
- Source branch: `develop`.
- Current production deployment pushes generated `_site/` content to the `master` branch.
- Target deployment: Roq GitHub Action workflow; do not preserve the old push-to-master deployment mechanism as the final deployment path.

## History and blame evidence

### Recent current-branch commits

- `5560d6f313` — Update plan to forbid scraping and wrapping.
- `bbca5c2d22` — Update batch file-limit to reduce iterations.
- `8ef4a6ed20` — Update plan for Roq default directory structure.
- `fd4a7fd9f4` — Add the Awestruct-to-Roq migration plan.
- `89b7f4ea70` — Fix the reference guide URL; this is the current `develop` baseline commit before migration-plan changes.
- `be8dc8f570` — Update Awestruct URL.
- `39f474845e` — Use the latest Awestruct version.
- `48aa6ad446` — Update the Docker image used to build the website.

### Relevant file history

- `README.adoc` build instructions originate primarily in `bdde902ebb` and were last materially updated for the Awestruct URL in `be8dc8f570`.
- `_config/site.yml` is mostly historical configuration from Dan Allen’s original site setup, with the current reference guide URL from `89b7f4ea70`.
- `_ext/pipeline.rb` preserves the original extension ordering from `951a30407a9`; later commits added redirects, modules, release-note providers, and newer repositories.
- `_ext/arquillian.rb` contains the most recent repository/component additions, including Liberty Arquillian and Smart Testing.
- `_ext/releases.rb` contains the latest release-generation fix from `1a94820f86`.
- `Dockerfile` was last updated by `48aa6ad446` and still installs Awestruct 0.6.1.
- `.travis.yml` and `_scripts/` retain the historical Docker/Travis deployment workflow.

Before editing any directly ported file, re-run `git log --follow` and `git blame` for that file and preserve relevant behavior from its history.

## Related issue and JIRA references

The repository search found no `proposals/` directory and no migration-specific JIRA ticket. Existing issue references that must survive content conversion include:

- Historical Arquillian issues in `community/nobles.html.haml`: `ARQ-33`, `ARQ-48`, `ARQ-147`, `ARQ-167`, `ARQ-176`, `ARQ-188`, `ARQ-189`, `ARQ-190`, `ARQ-193`, `ARQ-225`, `ARQ-230`, `ARQ-255`, `ARQ-291`, `ARQ-299`, `ARQ-323`, and `ARQ-379`.
- Historical project issues: `SHRINKWRAP-140`, `SHRINKWRAP-163`, and `SEAMFORGE-146`.
- Live JIRA project/release identifiers in `_ext/pipeline.rb`: `ARQ:12310885`, `ARQGRA:12312222`, `SHRINKWRAP:12310884`, `SHRINKRES:12312120`, and `SHRINKDESC:12311080`.
- The configured issue tracker is `https://issues.jboss.org/browse/ARQ`.

The migration must preserve `issue://` links through the new AsciiDoc/Qute interwiki helper and must not turn historical issue references into live-data dependencies.

## Initial manual visual-check set

Compare the same production and local Roq URLs manually. Do not add screenshot assertions or visual unit tests.

1. `/`
2. `/features/`
3. `/guides/`
4. `/blog/`
5. `/community/`

At the first visual checkpoint, also inspect `/docs/`, `/code/`, `/invasion/`, `/modules/`, one normal blog post, one release post, one guide, one translated guide, and one identity page.

## Phase 0 exit criteria

- Every current authored source family and generated route family has an owner and proposed Roq destination.
- Every explicit redirect is recorded, and the aliases-plugin rule is attached to URL changes caused by Roq.
- Every external provider, credential, cache, clone directory, output directory, and deployment script is recorded.
- Every direct port candidate is classified as templated content, Qute template, generated page, pure HTML, or static public asset.
- The route inventory is reviewed before Phase 1 creates `pom.xml` or Java source.
