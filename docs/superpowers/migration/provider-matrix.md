# Provider viability matrix

Probe date: 2026-11-16. HTTP probes used `curl -L --max-time 15`; a successful HTTP response only proves host reachability, not that the legacy API or scraper contract still works.

| Provider | Status | Endpoint / authentication | Cache path | Route families | Failure policy | Evidence |
|---|---|---|---|---|---|---|
| GitHub REST API | live | `https://api.github.com`; token optional for public data, recommended for rate limits | `github/` | modules, releases, contributors, identities | stale fallback only where page is explicitly permitted | HTTP 200 from `/orgs/arquillian/repos`; legacy URLs and API shape are documented in `_ext/github.rb` and `_ext/repository.rb` |
| JIRA REST/API | retired | `https://issues.jboss.org` redirects to the current Atlassian host; legacy anonymous REST and HTML endpoints are not a supported contract | `jira/` for retained fixtures only | release notes, issues, component leads | use committed fixture/manual data; never fail the site or scrape production HTML | `/browse/ARQ` redirects; legacy release-note parser depends on obsolete JIRA HTML |
| Confluence | manual | `https://docs.jboss.org/author`; hidden credentials historically required | `confluence/` | identity enrichment only | do not fail the site; use GitHub identity data or curated mapping | Host returned HTTP 200; legacy crawler is authenticated and must be probed with credentials before enabling |
| JBoss Community | manual | `https://community.jboss.org`; no supported authenticated API identified | `community/` | identity/profile links | do not crawl; preserve links or use curated identity records | Host returned HTTP 200; legacy source contains a TODO instead of a working crawler |
| Lanyrd | retired | `http://lanyrd.com`; legacy HTML scraper | none | invasion events and iCalendar | drop generated event routes; do not recreate from `_backup/lanyrd/` HTML | Host returned HTTP 200, but the legacy provider is retired and its event data contract is unavailable |
| Gravatar | manual | `https://gravatar.com`; no credentials | `identity/` | identity avatars | prefer GitHub avatar; use stable fallback image | Legacy source uses an obsolete JSON endpoint and Lanyrd mapping |

## Clone URL normalization

The legacy repository catalog uses `git://github.com/...` clone URLs. Provider code must normalize live GitHub repositories to their HTTPS clone URL before cloning:

```text
git://github.com/OWNER/REPOSITORY.git
https://github.com/OWNER/REPOSITORY.git
```

The migration must not attempt repository clones for providers marked `retired` or `manual`, and must keep clone output outside the repository's tracked `data/` tree.
