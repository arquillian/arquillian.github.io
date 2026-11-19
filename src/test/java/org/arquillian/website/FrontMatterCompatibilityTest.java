package org.arquillian.website;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Validates the required metadata contract for each authored content family. */
class FrontMatterCompatibilityTest {

    private static final Path FIXTURE_DIRECTORY = Path.of("src/test/resources/frontmatter");

    /** Ensures representative fixtures contain valid delimiters, required fields, and authored bodies. */
    @Test
    void representativeFixturesUseTheDocumentedContract() throws IOException {
        Map<String, List<String>> requiredFields = Map.of(
                "blog-post", List.of("layout", "title", "date", "author", "tags", "link"),
                "guide", List.of("layout", "title", "guide_summary", "guide_group", "guide_order", "link"),
                "translated-guide", List.of("layout", "title", "locale", "translation_of", "guide_summary", "link"),
                "release-note", List.of("layout", "title", "date", "repository", "version", "link"));

        for (Map.Entry<String, List<String>> fixture : requiredFields.entrySet()) {
            Map<String, String> frontMatter = readFrontMatter(fixture.getKey());
            for (String field : fixture.getValue()) {
                assertNotNull(frontMatter.get(field), fixture.getKey() + " is missing " + field);
                assertFalse(frontMatter.get(field).isBlank(), fixture.getKey() + " has blank " + field);
            }
            assertTrue(frontMatter.get("layout").matches("[a-z-]+"));
            assertTrue(frontMatter.get("link").startsWith("/"));
        }
    }

    /** Reads one fixture's YAML-like front matter and verifies that an authored body follows it. */
    private Map<String, String> readFrontMatter(String fixtureName) throws IOException {
        Path fixture = FIXTURE_DIRECTORY.resolve(fixtureName + ".adoc");
        List<String> lines = Files.readAllLines(fixture);
        assertTrue(!lines.isEmpty() && lines.get(0).equals("---"), fixture + " must start with ---");

        Map<String, String> values = new LinkedHashMap<>();
        boolean closed = false;
        boolean hasBody = false;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!closed && line.equals("---")) {
                closed = true;
            } else if (!closed && !line.isBlank() && !line.startsWith("#")) {
                int separator = line.indexOf(':');
                assertTrue(separator > 0, fixture + " has malformed front matter: " + line);
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            } else if (closed && !line.isBlank()) {
                hasBody = true;
            }
        }

        assertTrue(closed, fixture + " is missing its closing ---");
        assertTrue(hasBody, fixture + " is missing an authored body");
        return values;
    }
}
