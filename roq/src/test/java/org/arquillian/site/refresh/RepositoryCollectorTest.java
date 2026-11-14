package org.arquillian.site.refresh;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises explicit repository refresh against local Git objects without builds or network services. */
class RepositoryCollectorTest {
    @TempDir Path directory;

    /** Proves tagged artifacts use their revision and preserve inheritance, author counts and HEAD. */
    @Test void readsTaggedArtifactsAndCommitIdentities() throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(directory.toFile()).call()) {
            write("product/pom.xml", """
                    <project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId>
                    <artifactId>parent</artifactId><version>${revision}</version><packaging>pom</packaging>
                    <properties><revision>1.0.0.Final</revision><dep.version>2.1</dep.version></properties>
                    <licenses><license><name>Apache-2.0</name><url>https://example.org/license</url></license></licenses>
                    <modules><module>child</module></modules></project>
                    """);
            write("product/child/pom.xml", """
                    <project><parent><groupId>org.example</groupId><artifactId>parent</artifactId>
                    <version>${revision}</version></parent><artifactId>child</artifactId><name>Child</name>
                    <description>Child library</description><dependencies><dependency><groupId>org.dep</groupId>
                    <artifactId>dependency</artifactId><version>${dep.version}</version></dependency></dependencies></project>
                    """);
            RevCommit first = commit(git, "First", "123+alice@users.noreply.github.com", "2020-01-01T12:00:00Z");
            git.tag().setName("1.0.0.Final").setSigned(false).setMessage("release").setTagger(first.getAuthorIdent()).call();
            git.tag().setName("not-a-release").setAnnotated(false).call();
            write("product/child/pom.xml", Files.readString(directory.resolve("product/child/pom.xml")).replace("Child library", "Changed later"));
            RevCommit head = commit(git, "Second", "123+alice@users.noreply.github.com", "2020-01-02T12:00:00Z");
            ObjectNode rows = new RepositoryCollector().collect(git.getRepository(), metadata("product/"), head.name());
            assertEquals(head.getId(), git.getRepository().resolve("HEAD"));
            assertEquals(1, rows.path("releases").size());
            JsonNode release = rows.path("releases").get(0);
            assertEquals("2020-01-01T12:00:00Z", release.path("date").asText());
            assertEquals(first.name(), release.path("commit").asText());
            assertEquals("release", release.path("tag_message").asText());
            JsonNode taggedChild = release.path("artifacts").get(1);
            assertEquals("Child library", taggedChild.path("description").asText());
            assertEquals("org.example", taggedChild.path("groupId").asText());
            assertEquals("1.0.0.Final", taggedChild.path("version").asText());
            assertEquals("2.1", taggedChild.path("dependencies").get(0).path("version").asText());
            assertEquals("Apache-2.0", taggedChild.path("licenses").get(0).path("name").asText());
            assertEquals("product/child/", taggedChild.path("relative_path").asText());
            assertEquals("Changed later", rows.path("modules").get(1).path("description").asText());
            assertEquals(2, rows.path("identities").get(0).path("commits").asInt());
            assertEquals("alice", rows.path("identities").get(0).path("github_id_hint").asText());
            assertEquals("123", rows.path("identities").get(0).path("github_numeric_id").asText());
            ObjectNode renamed = metadata("product/").put("full_name", "renamed/product");
            assertEquals(release.path("id"), new RepositoryCollector().collect(git.getRepository(), renamed, head.name()).path("releases").get(0).path("id"));
            SnapshotStore store = new SnapshotStore(directory.resolve("catalog.json"));
            ObjectNode profile = JsonNodeFactory.instance.objectNode();
            profile.putArray("identities").addObject().put("id", "github:user:123").put("source", "github").put("source_id", 123).put("github_id", "alice");
            store.merge(profile);
            assertEquals(2, store.merge(rows).path("identities").size());
        }
    }

    /** Proves XML entity expansion and modules outside the repository fail before data is accepted. */
    @Test void rejectsUnsafeXmlAndEscapingModules() throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(directory.toFile()).call()) {
            write("pom.xml", "<!DOCTYPE project [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><project><artifactId>&secret;</artifactId></project>");
            RevCommit xml = commit(git, "unsafe XML", "person@example.org", "2020-01-01T00:00:00Z");
            assertThrows(IOException.class, () -> new ArtifactScanner().scan(git.getRepository(), xml.name(), metadata("")));
            write("pom.xml", "<project><artifactId>parent</artifactId><modules><module>../escape</module></modules></project>");
            RevCommit path = commit(git, "unsafe path", "person@example.org", "2020-01-02T00:00:00Z");
            assertThrows(IOException.class, () -> new ArtifactScanner().scan(git.getRepository(), path.name(), metadata("")));
        }
    }

    /** Proves supported Gradle literals are retained while expressions are reported rather than guessed. */
    @Test void readsLiteralGradleAndRetainsUnknownExpressions() throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(directory.toFile()).call()) {
            write("settings.gradle", "rootProject.name = 'Example'\ninclude 'child'\n");
            write("build.gradle", "group = 'org.example'\nversion = '2.0.0'\ndescription = 'Library'\ndependencies {\n implementation 'org.dep:lib:3.0'\n api(\"org.dep:api:4.0\");\n implementation 'org.dep:dynamic:1.' + patch\n}\n");
            write("child/build.gradle", "version = releaseVersion\n");
            RevCommit head = commit(git, "Gradle metadata", "alice@users.noreply.github.com", "2020-01-01T00:00:00Z");
            JsonNode modules = new ArtifactScanner().scan(git.getRepository(), head.name(), metadata(""));
            assertEquals(2, modules.size());
            assertEquals("Example", modules.get(0).path("name").asText());
            assertEquals("org.example", modules.get(0).path("groupId").asText());
            assertEquals("3.0", modules.get(0).path("dependencies").get(0).path("version").asText());
            assertEquals(2, modules.get(0).path("dependencies").size());
            assertEquals("4.0", modules.get(0).path("dependencies").get(1).path("version").asText());
            assertTrue(modules.get(0).path("diagnostics").toString().contains("Unresolved Gradle dependency: implementation 'org.dep:dynamic:1.' + patch"));
            assertEquals("releaseVersion", modules.get(1).path("version").asText());
            assertFalse(modules.get(1).path("diagnostics").isEmpty());
        }
    }

    /** Proves child overrides affect inherited properties, direct dependencies and managed versions without mutating siblings. */
    @Test void resolvesInheritedDeclarationsInEachChildPropertyContext() throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(directory.toFile()).call()) {
            write("pom.xml", """
                    <project><groupId>org.example</groupId><artifactId>parent</artifactId><version>1.0</version>
                    <properties><dep.version>1.0</dep.version><selected.version>${dep.version}</selected.version></properties>
                    <modules><module>child</module><module>sibling</module></modules><dependencies><dependency>
                    <groupId>org.dep</groupId><artifactId>direct</artifactId><version>${selected.version}</version>
                    </dependency></dependencies><dependencyManagement><dependencies><dependency><groupId>org.dep</groupId>
                    <artifactId>managed</artifactId><version>${selected.version}</version></dependency></dependencies></dependencyManagement></project>
                    """);
            String child = """
                    <project><parent><groupId>org.example</groupId><artifactId>parent</artifactId><version>1.0</version></parent>
                    <artifactId>child</artifactId><properties><dep.version>2.0</dep.version></properties><dependencies>
                    <dependency><groupId>org.dep</groupId><artifactId>managed</artifactId></dependency></dependencies></project>
                    """;
            write("child/pom.xml", child);
            write("sibling/pom.xml", child.replace("<dep.version>2.0</dep.version>", "").replace("<artifactId>child</artifactId>", "<artifactId>sibling</artifactId>"));
            RevCommit commit = commit(git, "Inherited expressions", "one@example.org", "2020-01-01T00:00:00Z");
            JsonNode rows = new ArtifactScanner().scan(git.getRepository(), commit.name(), metadata(""));
            assertEquals("2.0", rows.get(1).path("properties").path("selected.version").asText());
            assertEquals("2.0", rows.get(1).path("dependencies").get(0).path("version").asText());
            assertEquals("2.0", rows.get(1).path("dependencies").get(1).path("version").asText());
            assertEquals("2.0", rows.get(1).path("dependency_management").get(0).path("version").asText());
            assertEquals("1.0", rows.get(2).path("dependencies").get(0).path("version").asText());
            assertEquals("1.0", rows.get(2).path("dependencies").get(1).path("version").asText());
        }
    }

    /** Proves local bare clone/fetch preserves dirty source files and refuses insecure remote transports. */
    @Test void clonesAndFetchesWithoutCheckoutOrSourceChanges() throws Exception {
        Path origin = directory.resolve("origin");
        Path cache = directory.resolve("cache.git");
        try (Git git = Git.init().setInitialBranch("master").setDirectory(origin.toFile()).call()) {
            Files.writeString(origin.resolve("README"), "one");
            RevCommit first = commit(git, "one", "one@example.org", "2020-01-01T00:00:00Z");
            try (Git clone = new RepositoryCollector().open(origin.toUri(), cache)) {
                assertTrue(clone.getRepository().isBare());
                assertEquals(first.getId(), clone.getRepository().resolve("refs/heads/master"));
            }
            Files.writeString(origin.resolve("README"), "two");
            RevCommit second = commit(git, "two", "one@example.org", "2020-01-02T00:00:00Z");
            Files.writeString(origin.resolve("README"), "dirty");
            try (Git clone = new RepositoryCollector().open(origin.toUri(), cache)) {
                assertEquals(second.getId(), clone.getRepository().resolve("refs/remotes/origin/master"));
            }
            assertEquals("dirty", Files.readString(origin.resolve("README")));
            assertEquals(second.getId(), git.getRepository().resolve("HEAD"));
            assertThrows(IOException.class, () -> new RepositoryCollector().open(directory.resolve("other").toUri(), cache));
            assertThrows(IOException.class, () -> new RepositoryCollector().open(origin.toUri(), origin));
        }
        assertThrows(IllegalArgumentException.class, () -> new RepositoryCollector().open(URI.create("git://example.org/repo"), directory.resolve("bad")));
    }

    /** Proves external parents and profile modules are explicit and unresolved properties remain diagnostic data. */
    @Test void retainsExternalParentsAndProfileModulesWithoutGuessingProperties() throws Exception {
        try (Git git = Git.init().setInitialBranch("master").setDirectory(directory.toFile()).call()) {
            write("pom.xml", """
                    <project><parent><groupId>org.external</groupId><artifactId>base</artifactId><version>3.0</version></parent>
                    <artifactId>parent</artifactId><properties><known>7</known><loop>${loop}</loop></properties>
                    <profiles><profile><id>optional</id><modules><module>extra</module></modules></profile></profiles>
                    <dependencyManagement><dependencies><dependency><groupId>org.dep</groupId><artifactId>lib</artifactId>
                    <version>${known}</version></dependency></dependencies></dependencyManagement></project>
                    """);
            write("extra/pom.xml", """
                    <project><parent><groupId>org.external</groupId><artifactId>parent</artifactId><version>3.0</version></parent>
                    <artifactId>extra</artifactId><description>${unknown}</description><dependencies><dependency>
                    <groupId>org.dep</groupId><artifactId>lib</artifactId></dependency></dependencies></project>
                    """);
            RevCommit commit = commit(git, "Models", "one@example.org", "2020-01-01T00:00:00Z");
            JsonNode rows = new ArtifactScanner().scan(git.getRepository(), commit.name(), metadata(""));
            assertEquals(2, rows.size());
            assertEquals("org.external", rows.get(1).path("groupId").asText());
            assertEquals("3.0", rows.get(1).path("version").asText());
            assertEquals("7", rows.get(1).path("dependencies").get(0).path("version").asText());
            assertEquals("${unknown}", rows.get(1).path("description").asText());
            assertFalse(rows.get(0).path("diagnostics").isEmpty());
            assertFalse(rows.get(1).path("diagnostics").isEmpty());
        }
    }

    /** Provides stable upstream identity and a repository-relative component root. */
    private ObjectNode metadata(String root) {
        return JsonNodeFactory.instance.objectNode().put("id", "github:repository:42").put("source", "github")
                .put("source_id", 42).put("full_name", "example/product").put("relative_path", root)
                .put("html_url", "https://github.com/example/product");
    }

    /** Writes fixture content under the temporary repository. */
    private void write(String path, String contents) throws IOException {
        Path file = directory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }

    /** Creates a deterministic real commit with caller-selected author, timestamp and staged fixture files. */
    private RevCommit commit(Git git, String message, String email, String date) throws Exception {
        git.add().addFilepattern(".").call();
        PersonIdent author = new PersonIdent("Alice", email, Instant.parse(date), ZoneOffset.UTC);
        return git.commit().setSign(false).setMessage(message).setAuthor(author).setCommitter(author).call();
    }
}
