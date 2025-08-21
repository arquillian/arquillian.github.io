# Arquillian Website

This repository contains the source code for the Arquillian website, which is built using [Quarkus ROQ](https://github.com/quarkiverse/quarkus-roq) (Quarkus Ruby on Quarkus).

## Project Structure

- `content/`: Contains all the website content in Markdown format
- `data/`: Contains YAML files with site data (authors, menu)
- `public/`: Contains static assets (images, CSS, JavaScript)
- `src/main/resources/templates/`: Contains Qute templates for the site layout
- `src/main/resources/application.properties`: Configuration for the site

## Development

### Prerequisites

- JDK 21 or later
- Maven 3.8.6 or later

### Running the Site Locally

To run the site locally in development mode:

```bash
./mvnw quarkus:dev
```

This will start the development server at http://localhost:8080.

### Building the Site

To build the static site:

```bash
./mvnw package
```

The generated site will be available in the `target/site` directory.

## Deployment

The site is deployed to GitHub Pages using the Maven SCM Publish Plugin. To deploy the site:

```bash
./mvnw clean package -Pgithub-pages scm-publish:publish-scm
```

## Template System

The site uses Qute templates, which are located in `src/main/resources/templates/`. The main templates are:

- `layouts/base.html`: The base layout for all pages
- `layouts/default.html`: The default layout for most pages
- `layouts/blog.html`: Layout for the blog section
- `layouts/guide.html`: Layout for guides
- `layouts/module.html`: Layout for module pages
- `layouts/identity.html`: Layout for author pages
- `layouts/release.html`: Layout for release pages

## Content

Content is written in Markdown format with YAML front matter. The front matter contains metadata about the page, such as title, layout, and author.

Example:

```markdown
---
title: Getting Started
layout: guide
---

# Getting Started with Arquillian

This guide will help you get started with Arquillian...
```

## Migration Notes

This site was migrated from Awestruct (Ruby-based static site generator) to Quarkus ROQ. The migration involved:

1. Converting Haml templates to Qute templates
2. Converting Textile/HTML content to Markdown
3. Adapting the site structure to work with ROQ
4. Setting up GitHub Pages deployment

## Known Issues

- Template validation may show errors due to custom properties used in templates that are not part of the standard ROQ model classes
- Some JavaScript functionality may need adjustments to work with the new template system

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
