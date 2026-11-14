# Production baseline inventory

Date: 2026-11-13. Scope: the published `https://arquillian.org/` and the
current source branch; no other branch implementation was consulted.

The explicit capture entry point is test-scoped `ProductionBaseline capture`.
Ordinary tests use a local HTTP fixture and never execute the production URL
list. All generated HTML, raw ancillary responses, received assets, screenshots,
browser binaries, and the manifest live under ignored `roq/target/baseline/`.

## Reproduction

Run in `roq/` with the configured JDK 25 and Maven:

```sh
mvn -B -ntp compile test-compile -Dtest=ProductionBaselineTest test
PLAYWRIGHT_BROWSERS_PATH=target/baseline/browser mvn -B -ntp org.codehaus.mojo:exec-maven-plugin:3.7.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.classpathScope=test '-Dexec.args=install chromium --only-shell'
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 PLAYWRIGHT_BROWSERS_PATH=target/baseline/browser mvn -B -ntp org.codehaus.mojo:exec-maven-plugin:3.7.0:exec -Dexec.executable=java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath org.arquillian.site.ProductionBaseline capture'
```

The Maven-managed Playwright 1.65.0 CLI supplies Chromium and its bundled
runtime; contributors do not install Node.js separately. Browser installation
needs network access. No Maven settings changes are required.

## Selected production families

| Route | Evidence |
| --- | --- |
| `/` | Homepage, navigation, latest published release |
| `/guides/getting_started/` | Complex guide, chapter fragments and interactions |
| `/guides/getting_started_de/` | Translation route and fragments |
| `/blog/` | Blog index, pagination and tag links |
| `/blog/page/2/` | Published pagination behavior |
| `/blog/tags/release/` | Representative published tag directory |
| `/modules/` | Generated module directory |
| `/community/contributors/` | Generated contributor directory and avatars |
| `/invasion/events/` | Historical event directory |
| `/blog/2018/06/13/arquillian-cube-1-16-0/` | Latest release linked from the captured homepage |
| `/api/jbossorg/arquillian.properties` | Raw published properties output |
| `/blog/atom.xml` | Raw published feed output |
| `/invasion/events/arquillian.ics` | Raw published calendar output |

Release selection comes from the production homepage link, not the date in a
source filename. This is a representative baseline, not an exhaustive route
crawl. Same-origin links and fragments discovered in these responses are
retained in `manifest.json` for the complete migration inventory.

## Evidence contract

The manifest records source revision and dirty worktree state, capture times,
Playwright and Chromium versions, requested/final URLs, response status and
content type, original received bytes and SHA-256 checksums, fragment IDs and
same-origin link targets. Asset records retain URLs, downloaded paths and
checksums, CSS `url()` dependencies from external stylesheets, inline `style`
attributes and `<style>` blocks, and HTTP/network failures. Non-HTML
ancillary responses are retained as raw bytes in the page artifact path.

HTML page families receive full-page PNGs at viewport sizes 1440×1000 and
390×844. Screenshots wait for fonts and images, record timeouts, broken image
URLs, and browser request failures. The homepage and English guide also record
navigation/tab behavior and horizontal overflow at widths 766, 767, 768, 978,
979, and 980 around the legacy responsive breakpoints.

Production's shared stylesheet is
`https://arquillian.org/stylesheets/screen.css?e0e71a3a85916364d66959de2468e50cc63bbf50`.
Its original bytes and asset origins must be retained when staging editable CSS
in a later phase. The baseline does not modify published content or assets.

## Execution results

`mvn -B -ntp compile test-compile -Dtest=ProductionBaselineTest test` passed:
two tests, zero failures/errors/skips. The fixture first failed with one
assertion failure and zero errors before implementation. Browser installation
and remote capture required escalation because sandbox DNS is restricted.
The final capture command above exited zero without Maven thread-cleanup
warnings; using a child JVM avoids JGit's daemon-thread warnings from
`exec:java`. `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` prevents Java's driver startup
from trying to install additional browser distributions after the explicit
headless-shell install.

Final capture after inline CSS review fix: 2026-11-13 17:23:27–17:24:06 UTC, Chromium 158.0.8127.18,
Playwright 1.65.0, source revision
`89b7f4ea709970469af61a4d37dd022ecb88f3a1` with migration work uncommitted.

| Check | Result |
| --- | --- |
| Selected responses | 13, all HTTP 200 |
| Screenshots | 20 across 10 HTML pages; no screenshot errors, asset wait timeouts, or broken images |
| Referenced assets | 784 distinct URLs; 782 received and checksummed |
| Fragment inventory | 489 IDs across selected responses |
| Linked route inventory | 249 unique same-origin target pages; not recursively crawled |
| Artifact integrity | Every page/asset checksum and PNG signature checked against the manifest |
| Responsive navigation | Toggle visible through 979px, absent at 980px; expansion recorded at all tested narrower widths |

Manifest: `roq/target/baseline/manifest.json`, SHA-256
`6faf562a27286d7d2342dce19c82dc0c2dbd51e2b780604ae63b5781f12a7568`.
The shared CSS is stored at
`roq/target/baseline/assets/bfd329eaef0eaf545779de9c53d80da151a220344bca2221956df80cdf1677de`.
Marvel font bytes were also captured from the referenced Google Fonts URLs.

The inline homepage background
`https://arquillian.org/images/testing-java-microservices-cover.png` is saved as
`assets/9f386d2f7c8db36efd65c268b6053975d1cb3c3993fc6712b4b4b4af719a29a1`.
Its bytes match the existing repository image. The offline fixture reproduced
the omission before the fix (expected four assets, found two), then both tests
and `mvn compile test-compile` passed with inline attributes and style blocks
covered. Every recaptured checksum and PNG signature was checked again.
The preceding manifest and page artifacts are retained under
`roq/target/baseline/history/pre-inline-css/`, sharing the unchanged asset store;
the preserved manifest hash is
`159a7266fdba1f30e1e7ea5934de859d39f7be82d3b32dda5c8522430e8697ea`.

Unavailable external assets are retained as failures:
`http://arquillian.disqus.com/popular_threads_widget.js?num_items=5` returned
404, and `http://s7.addthis.com/js/250/addthis_widget.js` failed to connect.
Chromium separately reports mixed-content blocking for legacy HTTP widgets and
the HTTP Google Fonts stylesheet, plus blocked/aborted Disqus and Google+
requests. These are current production browser behavior, not capture omissions.

The homepage desktop/mobile PNGs were visually inspected. The current 390px
homepage has a fixed-width book promotion that overflows horizontally; its
full-page PNG therefore extends beyond the viewport width. No volatile regions
were masked. Navigation checks at 766px and above showed no horizontal
overflow. No tab controls were present on the two pages used for breakpoint
checks, so those records establish navigation behavior only. Exhaustive route
coverage, every interactive widget, and Roq visual parity remain later gates.
