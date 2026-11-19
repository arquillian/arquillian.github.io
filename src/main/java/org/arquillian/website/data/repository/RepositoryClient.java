package org.arquillian.website.data.repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.arquillian.website.data.github.GitHubClient;

/** Clones live repositories outside the site tree and extracts stable build metadata. */
public final class RepositoryClient {

    private final Path repositoryDirectory;

    /** Creates a repository client rooted at an ignored checkout directory. */
    public RepositoryClient(Path repositoryDirectory) {
        this.repositoryDirectory = Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
    }

    /** Clones a repository with a shallow checkout and returns its local path. */
    public Path cloneRepository(String name, java.net.URI cloneUrl) throws IOException, InterruptedException {
        Objects.requireNonNull(name, "name");
        java.net.URI normalizedUrl = GitHubClient.normalizeCloneUrl(cloneUrl);
        Path destination = repositoryDirectory.resolve(name).normalize();
        if (!destination.startsWith(repositoryDirectory.normalize())) {
            throw new IllegalArgumentException("repository name escapes checkout directory: " + name);
        }
        Files.createDirectories(repositoryDirectory);
        if (Files.exists(destination.resolve(".git"))) {
            run(destination, "git", "fetch", "--tags", "--depth=1", "origin");
        } else {
            run(repositoryDirectory, "git", "clone", "--filter=blob:none", normalizedUrl.toString(), destination.toString());
        }
        return destination;
    }

    /** Extracts the current commit, tags, and Maven/Gradle build markers from a checkout. */
    public RepositorySnapshot inspect(Path checkout) throws IOException, InterruptedException {
        Path root = Objects.requireNonNull(checkout, "checkout").toAbsolutePath().normalize();
        String commit = run(root, "git", "rev-parse", "HEAD").trim();
        List<String> tags = lines(run(root, "git", "tag", "--list"));
        return new RepositorySnapshot(root, commit, tags, Files.exists(root.resolve("pom.xml")),
                Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts")));
    }

    /** Runs one bounded Git command and includes its output in failures. */
    private static String run(Path directory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Repository command failed (" + exitCode + "): " + String.join(" ", command) + "\n" + output);
        }
        return output;
    }

    /** Converts newline-delimited command output into an immutable list. */
    private static List<String> lines(String value) {
        List<String> result = new ArrayList<>();
        value.lines().map(String::trim).filter(line -> !line.isBlank()).forEach(result::add);
        return List.copyOf(result);
    }

    /** Describes repository data needed by generated module and release pages. */
    public record RepositorySnapshot(Path path, String commit, List<String> tags, boolean maven, boolean gradle) {
        /** Creates an immutable repository snapshot. */
        public RepositorySnapshot {
            tags = List.copyOf(tags);
        }
    }
}
