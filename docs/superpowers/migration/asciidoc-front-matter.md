# AsciiDoc front-matter contract

Roq content uses a YAML front-matter block delimited by `---` at the beginning of each `.adoc` file. The block is followed by authored AsciiDoc; it must not contain captured or copied production HTML.

## Common attributes

| Attribute | Required | Meaning |
| --- | --- | --- |
| `layout` | yes | Qute layout name, such as `page`, `post`, `guide`, or `release`. |
| `title` | yes | Human-readable page title. |
| `description` | no | Page description used for metadata and summaries. |
| `author` | no | Stable identity key used by the identity provider. |
| `date` | no | ISO date (`YYYY-MM-DD`) for posts and release notes. |
| `tags` | no | YAML list of stable tag names. |
| `link` | no | Explicit generated URL when Roq’s default route differs from production. |
| `aliases` | no | Roq aliases-plugin paths that should redirect to this page. |
| `redirect_from` | no | Roq aliases-plugin redirect paths for legacy URLs. |

Unknown provider-specific attributes are allowed, but they must be documented with the page family that consumes them. Generated provider data belongs outside tracked `data/`; only deliberate fallback records belong under `data/curated/`.

## Route rules

### Blog posts

Move `blog/YYYY-MM-DD-slug.textile` to `content/posts/YYYY-MM-DD-slug.adoc`. The date and slug remain part of the public route: `/blog/YYYY/MM/DD/slug/`. Set `link` when the Roq collection route does not produce that exact path, and add the old path to `aliases` when a redirect is required.

Release notes are posts with `layout: release`, `repository`, and `version` attributes. Their authored body contains release highlights; repository metadata and contributor details remain generated data.

### Guides

Move `guides/<slug>.textile` to `content/guides/<slug>.adoc`. The default route is `/guides/<slug>/`. Preserve translated guide suffixes, for example `<slug>-es.adoc`, with `locale: es` and `translation_of: <base-slug>`.

Guide pages preserve these attributes when present:

```yaml
layout: guide
title: Getting Started
guide_summary: Learn how to write your first Arquillian test.
guide_group: fundamentals
guide_order: 10
```

### Static and special pages

Primary pages use explicit `link` values whenever their production URL is not the Roq default. The aliases plugin is the only redirect mechanism for old paths. Pure HTML inputs may remain HTML and may be copied directly, but templated Haml pages must become AsciiDoc plus Qute layouts.

## Textile-to-AsciiDoc mapping

| Legacy Textile | AsciiDoc/Roq form |
| --- | --- |
| `h2. Heading` | `== Heading` |
| `* item` / `# item` | `* item` / `. item` |
| `bc(prettify)..` | `[source,java]` followed by a delimited listing |
| `p(note).` | `[NOTE]` followed by the note text |
| `p(warning).` | `[WARNING]` followed by the warning text |
| `!/images/name.png!` | `image::/images/name.png[Description]` |
| `"label":url` | `link:url[label]` |
| `@code@` | `` `code` `` |
| `#{site.value}` | A Qute expression in the owning template or a typed generated model; never a Ruby expression in content. |

Preserve explicit heading IDs when existing links depend on them, using `[[stable-id]]` before the heading. Use AsciiDoc admonitions for notes and warnings, source blocks for code, and absolute public asset paths for images.

## Validation expectations

Every converted batch must validate front matter before rendering. The compatibility fixtures cover one normal post, one guide, one translated guide, and one release note. The validator checks required family fields and confirms that a body follows the closing delimiter.
