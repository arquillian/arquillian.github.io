# Roq Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans when applicable. The first task is a tightly coupled build scaffold and can be executed inline. Steps use checkbox syntax for tracking. The user's phase approval gates override continuous execution guidance.

**Goal:** Establish a separate, verifiable Roq build before migrating production pages or rewriting collectors.

**Architecture:** Keep the current Awestruct source intact and place the Maven project in `roq/`, excluded from Awestruct. Render an AsciiDoc probe, then prove snapshot-backed CDI access and capture production baselines before porting layouts. Configured external collection runs as a cache-aware Java build step before page derivation; templates read only validated local snapshots.

**Tech Stack:** Maven 3.9.16; JDK 25; initial candidates Roq 2.2.0, Quarkus 3.40.1.2, and Maven Compiler Plugin 3.16.0. Recheck stable releases before implementation and verify compatibility rather than assuming it.

**Spec:** [Approved migration design](../specs/2026-11-13-roq-migration-design.md).

## Global constraints

1. Use the current branch's implementation and the published production site as the migration baseline. Exclude changes from other branches from analysis, reuse, and implementation.
2. Each implementation phase changes at most five repository files and runs verification before the next phase. All remaining phases are approved as of 2026-11-13. Commit and push nothing. Maven settings changes require permission each time.
3. Use stable pinned dependencies and a supported JDK; align Roq plugins and use the Quarkus BOM. For every Java or Java build-file change, run `mvn compile test-compile` and fix every error before reporting completion.
4. Refresh configured API/Git sources during the build before rendering, honoring legacy cache expiry and preserving atomic snapshot writes. Qute reads the resulting snapshots through Java/CDI access; a skip flag keeps tests and snapshot-only previews offline. Preserve existing content, identifiers, public URLs, assets, and visual behavior.
5. Re-read files before editing and after applying changes. New Java types and methods require Javadoc. Any structural refactor on an existing file over 300 lines starts with a separate verified cleanup phase; the no-commit rule still applies.

## Execution boundaries

The user approved all remaining phases on 2026-11-13. Continue through the
remaining migration without approval pauses, verifying each phase before
starting the next dependent phase. Keep work on the current feature branch and
preserve existing uncommitted work. No commits, pushes, deployment, or Maven
settings changes are included in this approval.

This plan makes the foundation phases executable. Later migration milestones
are a dependency-ordered roadmap covered by the user's all-phase approval.
Expand each later milestone into exact batches of at most five files after the
baseline and data inventory are available. Count source-file additions, edits,
moves, and deletions individually; generated ignored artifacts are not source
file edits. Planning documents are preparation, separate from implementation
phase file budgets.

## Project boundaries and ownership

| Path | Responsibility |
| --- | --- |
| `roq/pom.xml`, `roq/config/application.properties` | Dependency versions, JVM target, export configuration, and content discovery |
| `roq/content/`, `roq/templates/` | AsciiDoc content and Qute layouts preserving public routes and HTML hooks |
| `roq/data/` | Reviewed data snapshots; Roq exposes these files as named CDI beans |
| `roq/src/main/java/org/arquillian/site/` | Required data processing and explicit Java collectors; no mechanical ports of build-only Ruby |
| `roq/src/test/`, `roq/target/` | Repeatable fixture/HTTP/browser verification and ignored build/baseline artifacts |

Existing `images/` and `javascripts/` remain their authoritative source during
parallel operation. A later resources-copy step stages them into the Roq public
directory; copying into ignored build output does not create a second editable
source tree. Production CSS is captured and retained as editable plain CSS.

## Phase 1 / Task 1: Isolated dependency and AsciiDoc export probe

**Files: exactly five**

| Action | Path | Change |
| --- | --- | --- |
| Modify | `.awestruct_ignore` | Add `roq/` so the legacy generator excludes the new project |
| Modify | `.gitignore` | Ignore `/roq/target/` and `/roq/node_modules/`; retain the site configuration despite global ignore rules |
| Create | `roq/pom.xml` | Minimal pinned Maven/Quarkus/Roq build with the AsciiDoc extension |
| Create | `roq/config/application.properties` | Site URL, filename preservation, locale, Qute expression syntax, and literal AsciiDoc code |
| Create | `roq/content/index.adoc` | Clearly labeled compatibility probe with a fixed anchor and a Java code sample |

**Interfaces:** Consumes the installed Maven/JDK and published dependency
artifacts. Produces `roq/target/quarkus-app/` and `roq/target/roq/index.html`.
Creates no production route changes, data collectors, or deployment workflow.
The probe page is temporary foundation content and must be replaced by the
faithful homepage before cutover.

- [x] **Step 1: Check scope and the toolchain.**

Run from the repository root:

```sh
git status --short --branch
git log -3 --format='%h %s' -- .awestruct_ignore .gitignore
git blame .awestruct_ignore
git blame .gitignore
java -version
mvn -version
```

Inspect only current-branch history. Check official Roq, Quarkus, Maven, and
Compiler Plugin releases. The installed JDK is Temurin 25.0.5 and Maven is
3.9.16 at planning time. Verify the initial candidate versions can be resolved;
if a newer stable compatible version exists, pin it and record the tested set.
Make no Maven settings changes.

- [x] **Step 2: Establish the missing-export check before creating the scaffold.**

```sh
test -s roq/target/roq/index.html
```

Expected before implementation: nonzero because no Roq export exists. This is
an export acceptance check, not a substitute for Java behavior tests in later
phases.

- [x] **Step 3: Create the five-file scaffold.**

Append the new exclusions to the existing ignore files. Preserve their current
entries, including the existing `docs/superpowers/` Awestruct exclusion.

Use the following `roq/pom.xml` as the initial candidate configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.arquillian</groupId>
    <artifactId>arquillian-site</artifactId>
    <version>1.0.0</version>
    <name>Arquillian Roq Site</name>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>25</maven.compiler.release>
        <quarkus.platform.version>3.40.1.2</quarkus.platform.version>
        <roq.version>2.2.0</roq.version>
        <compiler-plugin.version>3.16.0</compiler-plugin.version>
        <resources-plugin.version>3.5.0</resources-plugin.version>
        <surefire-plugin.version>3.6.1</surefire-plugin.version>
        <jar-plugin.version>3.5.1</jar-plugin.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-bom</artifactId>
                <version>${quarkus.platform.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.quarkiverse.roq</groupId>
            <artifactId>quarkus-roq</artifactId>
            <version>${roq.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkiverse.roq</groupId>
            <artifactId>quarkus-roq-plugin-asciidoc-jruby</artifactId>
            <version>${roq.version}</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <artifactId>maven-resources-plugin</artifactId>
                <version>${resources-plugin.version}</version>
            </plugin>
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${surefire-plugin.version}</version>
            </plugin>
            <plugin>
                <artifactId>maven-jar-plugin</artifactId>
                <version>${jar-plugin.version}</version>
            </plugin>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <version>${quarkus.platform.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${compiler-plugin.version}</version>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The AsciiDoc extension's embedded renderer is Maven-managed; contributors do
not install Ruby. Roq 2.2.0 does not publish the base-theme extension shown in
newer documentation. Use its supported empty `site.page-layout` setting for
the initial fragment export; introduce the site's own HTML shell with the
first Qute layout. This avoids a dependency on unreleased theme artifacts.

Use this `roq/config/application.properties`:

```properties
site.url=https://arquillian.org
site.slugify-files=false
site.page-layout=
quarkus.default-locale=en
quarkus.qute.alt-expr-syntax=true
quarkus.asciidoc.qute=false
quarkus.http.host=127.0.0.1
quarkus.http.port=8182
```

Use this `roq/content/index.adoc`:

```asciidoc
---
title: Arquillian Roq Build Probe
description: Temporary migration compatibility check.
---
= Roq foundation probe

This page checks the migration build and is not production content.

[#legacy_anchor]
== Preserved anchor

[source,java]
----
/** Demonstrates literal braces surviving AsciiDoc rendering. */
class Example { String value = "{not_a_template}"; }
----
```

- [x] **Step 4: Compile, package, and export.**

Run from `roq/`:

```sh
mvn -B -ntp compile test-compile
QUARKUS_ROQ_GENERATOR_BATCH=true mvn -B -ntp package quarkus:run -Dquarkus.http.port=8182
```

Expected: successful Maven compilation phases, Quarkus augmentation, and batch
export with process exit zero. Initial setup may resolve dependencies, but no
GitHub/JIRA/Confluence/Lanyrd collection or Ruby executable should run. There
are no Java unit tests in this scaffold: do not report a unit-test pass count.

- [x] **Step 5: Inspect the artifact and isolation rules.**

Run from the repository root:

```sh
test -s roq/target/roq/index.html
rg -n 'Roq foundation probe|id="legacy_anchor"|not_a_template' roq/target/roq/index.html
git check-ignore roq/target/roq/index.html
git diff --check
git status --short
```

Read the exported HTML: all three markers must be present; braces must remain
literal code content. Check the current Awestruct ignore expansion includes
`roq/`, while existing public documentation remains included. Confirm only the
five implementation files changed in this phase, apart from the separately
prepared approved spec and this plan. Re-read each edited file. Record actual
versions, commands, exit codes, and limitations below, then stop for approval.

### Phase 1 execution record

Status: complete on 2026-11-13; awaiting approval for Phase 2.

Verified with Temurin JDK 25.0.5 and Maven 3.9.16. Tested pins: Roq 2.2.0,
Quarkus 3.40.1.2, Compiler Plugin 3.16.0, Resources Plugin 3.5.0, Surefire
3.6.1, and JAR Plugin 3.5.1.

| Verification | Result |
| --- | --- |
| Missing-export check before implementation | Expected nonzero result: artifact absent |
| `mvn -B -ntp compile test-compile` in `roq/` | Exit 0 |
| Batch `package quarkus:run` using the command above | Exit 0; generated `roq/target/roq/index.html`; listener bound to `127.0.0.1:8182` and exited after export |
| Artifact inspection | Current source, heading, `legacy_anchor`, literal braces, and example Javadoc present |
| Isolation and source checks | Roq/planning directories excluded from Awestruct; generated target/dependencies ignored; site configuration retained despite the user's global ignore; whitespace checks pass |

Exported HTML SHA-256: `f43400fb204e2ede518a3355fa2e29c23159862e54c5565cd357562f5e50e505`.

The sandbox prevents binding the generator's local HTTP listener, so export
verification required the approved escalation. No Maven settings were changed.
AsciidoctorJ's bundled FFI emits a JDK 25 native-access warning; generation
still succeeds. The scaffold contains no Java application classes or unit
tests yet. Visual fidelity and production content have not been migrated or
compared in this phase.

## Phase 2 / Task 2: Prove snapshot-backed CDI template access

Status: complete on 2026-11-13; awaiting approval for Phase 3.

**Files: at most five:** modify `roq/pom.xml` and `roq/content/index.adoc`;
create `roq/data/siteData.yml`, `roq/templates/layouts/probe.html`, and
`roq/src/test/java/org/arquillian/site/SnapshotRenderingTest.java`.

**Interfaces:** `siteData.yml` becomes the named CDI bean `siteData`; the layout
consumes `cdi:siteData.releases`. The test verifies rendered data, not a mock
implementation of Roq.

- [x] Add the BOM-managed `quarkus-junit` and `rest-assured` test dependencies and use the pinned Surefire plugin. Use `@QuarkusTest` for the rendering test and Javadoc on the class and test method.
- [x] Write the test first: GET `/`, require status 200, the marker `release-fixture-1`, and the original literal code braces. Before adding the data binding, verify failure because the marker is absent.

`roq/src/test/java/org/arquillian/site/SnapshotRenderingTest.java`:

```java
package org.arquillian.site;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** Verifies Qute exposes local snapshot data without a collection step. */
@QuarkusTest
class SnapshotRenderingTest {

    /** Checks snapshot content renders while preserving AsciiDoc anchors and literal code. */
    @Test
    void rendersSnapshotData() {
        given().when().get("/").then().statusCode(200)
                .body(containsString("release-fixture-1"),
                        containsString("Fixture release"),
                        containsString("id=\"legacy_anchor\""),
                        containsString("{not_a_template}"));
    }
}
```

- [x] Add the following fixture and template, set `layout: probe` on the probe page, then rerun the test and static export.

`roq/data/siteData.yml`:

```yaml
releases:
  - id: release-fixture-1
    title: Fixture release
    url: /blog/fixture-release/
```

`roq/templates/layouts/probe.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><title>{=page.title}</title></head>
<body>
{#insert /}
<ul id="release-probe">
  {#for release in cdi:siteData.releases}
  <li data-release-id="{=release.id}">{=release.title}</li>
  {/for}
</ul>
</body>
</html>
```

- [x] Run `mvn compile test-compile`, the focused `SnapshotRenderingTest`, and batch export. Require the same release marker in HTTP output and exported HTML. Change the fixture title once and verify the rendered text changes, then restore it.
- [x] Verify template access only reads local snapshots. Keep all collection code absent from rendering startup. Stop for approval; the fixture is a probe, not production release data.

### Phase 2 execution record

Five implementation files changed: `roq/pom.xml`, `roq/content/index.adoc`,
`roq/data/siteData.yml`, `roq/templates/layouts/probe.html`, and
`roq/src/test/java/org/arquillian/site/SnapshotRenderingTest.java`.

| Verification | Result |
| --- | --- |
| Test before data/template integration | Expected failure: HTTP 200, with release ID/title missing; one test, one failure, zero errors |
| Focused `SnapshotRenderingTest` after integration | Exit 0; one test, zero failures/errors/skips |
| `mvn -B -ntp compile test-compile` | Exit 0; test bytecode targets Java 25 |
| Batch export after changing only the snapshot title | Exit 0; release title changed in generated HTML while its ID stayed stable |
| Batch export after restoring the snapshot | Exit 0; original title restored; output otherwise byte-identical to the title-change capture |

Export command: `QUARKUS_ROQ_GENERATOR_BATCH=true mvn -B -ntp -DskipTests package quarkus:run`.
Tests ran separately before the exports; the two export checks skipped rerunning
those unchanged tests. Exported HTML SHA-256: `129116d1e8c49ea9fc2dbb6d9e6bc532b4d55d04ae66b346c180be4e5234546a`.

The final artifact contains the CDI-backed release ID/title, the original
`legacy_anchor`, and literal code braces. The data access path uses Roq's
local YAML data bean; no application collector, external HTTP client, Git
operation, or Ruby process invocation was added. This was checked by source
inspection; no network interception test was added.

In this probe, Roq resolves `page.title` from the AsciiDoc document heading,
which takes precedence over its differing YAML title. Real-content conversion
must align the AsciiDoc heading with the intended published title explicitly.
The fixture remains build-probe data. Production data collection and visual
baseline capture have not been implemented in this phase.

## Phase 3 / Task 3: Capture reproducible production baselines

**Files: at most five:** modify `roq/pom.xml`; create
`roq/src/test/java/org/arquillian/site/ProductionBaseline.java`,
`roq/src/test/java/org/arquillian/site/ProductionBaselineTest.java`,
`roq/src/test/resources/baseline-pages.json`, and
`docs/superpowers/plans/2026-11-13-roq-baseline-inventory.md`.

**Interfaces:** a test-scope Java capture command consumes the URL list and
writes `roq/target/baseline/manifest.json`, page HTML, referenced asset hashes,
and desktop/mobile screenshots. The capture command performs explicit external
reads; ordinary rendering never invokes it.

- [ ] Pin a stable Maven-managed Playwright dependency compatible with JDK 25 and use the BOM-managed JSON facilities already available. Browser binaries are installed by the Java tool's own command, with no separate Node.js installation.
- [ ] Test the capturer against a local HTTP fixture: resolve relative links against the fetched URL, retain URL fragments separately, reject crawling to other origins, and hash the received bytes. Use Javadoc for every new type and method.
- [ ] Capture `/`, `/guides/getting_started/`, `/guides/getting_started_de/`, `/blog/`, `/modules/`, and `/community/contributors/`. Add the release-post URL linked from the production homepage and the current tag/event/ancillary URLs discovered by the inventory; do not infer release dates from source filenames.
- [ ] Capture Chromium at 1440x1000 and 390x844, wait for fonts and images, and record the browser version, timestamps, source revision, final response URLs, and any unavailable assets. Generate separate viewport-boundary interaction checks around the legacy 767px and 979px breakpoints.
- [ ] Run `mvn compile test-compile` and the local capturer tests before the explicit production capture. Record missing or retired services as evidence. Review the baseline before styling or content conversion; the user's all-phase approval removes the earlier approval pause.

## Subsequent migration roadmap

The baseline inventory determines the exact source/output manifest and future
batch boundaries. These milestones preserve the complete approved scope.

| Order | Deliverable | Source and target boundary | Required checks |
| --- | --- | --- | --- |
| 1 | Reproducible tooling and shared assets | Maven wrapper and JDK pins; stage existing `images/` and `javascripts/`; capture production CSS into `roq/public/stylesheets/` | Exact wrapper distribution/checksum; asset-path and hash comparisons; no duplicate editable source assets |
| 2 | Data schemas and safe snapshot updates | Port data behavior from `_ext/common.rb`, `_ext/restclient_extensions.rb`, and collector code into Java under `roq/src/main/java/org/arquillian/site/`; snapshots under `roq/data/` | Deterministic ordering, stable IDs, pagination, malformed response rejection, failed refresh leaves every old file intact |
| 3 | Repository/module/release collection | `_ext/repository.rb`, data visitors in `_ext/arquillian.rb`, `_ext/github.rb`, `_ext/jira.rb`, and data portions of `_ext/releases.rb` | Fixture parity for Git tags, POM/Gradle module scans, coordinates, milestones, issue grouping, release dates, and contributor mapping |
| 4 | Identities, events, and guide history | `_ext/identities.rb`, `_ext/identities/`, `_ext/lanyrd.rb`, and history/translation data in `_ext/guide.rb` | Names, attribution, avatar decisions, languages, event time zones, calendar identity, and current-branch guide history preserved; retired sources retain snapshots |
| 5 | Homepage and complex guide | `_layouts/base.html.haml`, `_layouts/default.html.haml`, guide layouts, `index.html.haml`, `guides/getting_started.textile` to Qute/AsciiDoc | Snapshot-backed CDI lookups; baseline desktop/mobile comparison; existing fragments, navigation, tabs, and scrolling chapters |
| 6 | Ordinary pages and generated directories | `features/`, `docs/`, `code/`, `community/`, `modules/`, `invasion/` to `roq/content/` and data collections | Every inventoried public route accounted for; module categories/order and contributor identities match; no build-time external content requests |
| 7 | Blog/release and guide conversion batches | `blog/` and `guides/`, including embedded Textile in configuration/layouts | Convert in batches of at most five changed files; preserve front matter, dates, tags, excerpts, code, links, translations, comments, anchors, and history |
| 8 | Feeds and ancillary outputs | Atom, redirect entries, event ICS, `api/jbossorg/arquillian.properties.haml`, verification files, 404, `CNAME`, `.nojekyll` | Feed identity and metadata, calendar UIDs/time zones, exact text outputs, static redirect targets, hosting files, and all internal links/fragments |
| 9 | Reviewable CI artifact and authorized cutover | Maven build/refresh workflows and contributor instructions | Artifact-only workflow first; render without content credentials; visual/functional acceptance; explicit approval before deployment or push |
| 10 | Legacy retirement | Awestruct/Ruby/build-only files and obsolete sources | Separate approved cleanup batches, no route loss, no Ruby processes in the final workflow, complete build/export verification |

Collectors must be ported by behavior, not by reproducing Awestruct's object
graph. Prefer Roq's generated CDI data beans for direct snapshot reads. Add a
small named Java bean or Qute template extension only where sorting, lookup,
formatting, or domain processing needs executable logic. Java ports must carry
over error handling and validation.

Before each subsequent milestone, write a bounded plan naming every changed
file, caller, fixture, schema, test, and verification command. A content move
and conversion count against the five-file cap; no directory-wide move or
mass deletion is implied by a roadmap row. Independent batches use parallel
subagents when the repository's swarming rule applies, without bypassing phase
verification gates or sharing write ownership.

## Cross-cutting testing matrix

| Contract | Positive case | Failure or compatibility case |
| --- | --- | --- |
| Dependencies/export | Exact stable pins compile, augment, and batch-export | Unsupported combinations fail the phase; do not report resolution as compatibility |
| Snapshot/CDI rendering | Qute output reflects local data and preserves order | Missing/invalid required data fails clearly; render performs no content-service calls or Ruby execution |
| Refresh | Multi-page service response produces stable snapshots | Timeout, authorization failure, invalid payload, or unexpected identity/removal leaves old snapshots unchanged |
| AsciiDoc/URLs | Existing IDs, translated routes, tables, listings, and callouts survive | Literal braces remain code; no silent slug/case/underscore changes or missing fragments |
| Presentation | Baseline HTML hooks, fonts, colors, spacing, and interactions match | Desktop/mobile and breakpoint checks expose regressions; document every volatile mask |
| Non-HTML outputs | Original feed IDs, calendar IDs, release metadata, and properties remain | No identity churn, broken redirect destinations, missing hosting files, or extra planning/build artifacts in export |

## References

- [Roq directory layout, data beans, and content behavior](https://iamroq.dev/docs/basics/)
- [Roq batch export](https://iamroq.dev/docs/publishing/)
- [Roq 2.2.0 build dependencies](https://github.com/quarkiverse/quarkus-roq/blob/2.2.0/pom.xml)
- [Maven distributions](https://maven.apache.org/download.cgi)
- [Qute CDI integration](https://quarkus.io/guides/qute-reference/#injecting-beans)
