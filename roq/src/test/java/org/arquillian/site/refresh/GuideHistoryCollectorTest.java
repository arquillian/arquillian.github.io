package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies original guide paths, translation metadata and recent history come from explicit Git revisions. */
class GuideHistoryCollectorTest {
    @TempDir Path directory;

    /** Proves history stays revision-bound and translations retain their original metadata without source writes. */
    @Test void readsOriginalPathsTranslationAndRecentHistory() throws Exception {
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            Files.createDirectories(directory.resolve("guides"));
            Files.writeString(directory.resolve("guides/start.textile"), "---\ntitle: Getting started\nguide_group: basics\nguide_order: 2\nauthors: [alice]\n---\nOriginal body");
            Files.writeString(directory.resolve("guides/start_fr.textile"), "---\ntitle: Commencer\ntranslators: [bob]\n---\nCorps");
            Files.writeString(directory.resolve("guides/start_pt_br.textile"), "---\ntitle: Começar\ntranslators: [carol]\n---\nCorpo");
            Files.writeString(directory.resolve("guides/index.html.haml"), "index");
            PersonIdent author = new PersonIdent("Alice", "alice@example.org", Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
            git.add().addFilepattern(".").call();
            RevCommit first = git.commit().setSign(false).setMessage("Create guide.\n\nDetails").setAuthor(author).setCommitter(author).call();
            Files.writeString(directory.resolve("guides/start.textile"), "---\ntitle: New title\n---\nChanged body");
            git.add().addFilepattern(".").call();
            RevCommit head = git.commit().setSign(false).setMessage("Update guide").setAuthor(author).setCommitter(author).call();
            JsonNode guides = new GuideHistoryCollector().collect(git.getRepository(), first.name(), "guides", 3);
            assertEquals(3, guides.size());
            JsonNode base = guides.get(0);
            assertEquals("guides/start.textile", base.path("source_path").asText());
            assertEquals("Getting started", base.path("title").asText());
            assertEquals("en", base.path("language").asText());
            assertEquals("basics", base.path("guide_group").asText());
            assertEquals(first.name(), base.path("changes").get(0).path("commit").asText());
            assertEquals("Alice", base.path("changes").get(0).path("author").asText());
            assertEquals("Create guide.\n\nDetails", base.path("changes").get(0).path("message").asText());
            assertEquals("fr", guides.get(1).path("language").asText());
            assertEquals("guides/start.textile", guides.get(1).path("language_parent").asText());
            assertEquals("bob", guides.get(1).path("translators").get(0).asText());
            assertEquals("pt_br", guides.get(2).path("language").asText());
            assertEquals("guides/start.textile", guides.get(2).path("language_parent").asText());
            assertEquals(2, base.path("languages").size());
            assertEquals(1, new GuideHistoryCollector().collect(git.getRepository(), head.name(), "guides", 1).get(0).path("changes").size());
            assertEquals(head.getId(), git.getRepository().resolve("HEAD"));
            assertTrue(Files.readString(directory.resolve("guides/start.textile")).contains("Changed body"));
        }
    }
}
