# Presentation assets

`public/stylesheets/screen.css` is the compiled production stylesheet captured
from `https://arquillian.org/stylesheets/screen.css?e0e71a3a85916364d66959de2468e50cc63bbf50`
on 2026-11-13. Bootstrap copyright and Apache License notices remain in the file.
The initial CSS change makes `http://arquillian.org/` image references
root-relative so local preview uses the same local sprites.
The unusable HTTP Marvel font import is removed: production HTTPS blocks it,
so retaining that import made HTTP previews render a different font. Removing
it preserves the captured Arial fallback in both preview and production.

The retained stylesheet SHA-256 is
`20b5be8007382524d553219fde5f0676307cfcaa00b626254c66a31f79167f8e`.

`public/stylesheets/dashboard.css` is the 265,277-byte production stylesheet
captured from `https://arquillian.org/stylesheets/dashboard.css` on 2026-11-13.
It is the published output of the legacy `stylesheets/dashboard.scss` and keeps
the Bootstrap copyright and license notices. The capture is copied without
modification; its SHA-256 is
`c4b25ebfc86a470970d415c316819937a2b3a96a49c87e6b2fbd3df7774c619e`.

The root `images/` and `javascripts/` directories, favicon, domain and hosting
files remain authoritative while Awestruct runs alongside Roq. Maven stages
these files and Roq's `public/` contents into ignored `site-public/` before
compilation. It clears only that staging directory at each initialize phase,
preventing removed assets from surviving a later build. Do not edit staging.

Production `javascripts/site.js` matches the existing source byte for byte.
The homepage book cover also matches its existing source. Browser libraries
and external widgets retain their production URLs; unavailable services
and blocked mixed content are recorded in the production baseline inventory.
