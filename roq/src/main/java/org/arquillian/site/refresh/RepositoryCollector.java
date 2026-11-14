package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Collects explicit Git repository observations without checkout, application startup I/O or snapshot writes. */
public final class RepositoryCollector {
    private static final Pattern TAG = Pattern.compile("^(arquillian-liberty-)?([a-z]+-?)?[0-9]\\d*\\.\\d+\\.\\d+(\\.\\d+)?([.-]((alpha|beta|cr)-?[1-9]\\d*|final))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOREPLY = Pattern.compile("^(?:(\\d+)\\+)?([^@]+)@users\\.noreply\\.github\\.com$", Pattern.CASE_INSENSITIVE);

    /** Creates an explicit-refresh collector; construction performs no I/O. */
    public RepositoryCollector() {}

    /** Clones to a new bare cache or fetches into an existing bare cache; the caller owns the returned Git handle. */
    public Git open(URI origin, Path cache) throws IOException {
        boolean local = "file".equalsIgnoreCase(origin.getScheme()) && (origin.getHost() == null || origin.getHost().isEmpty());
        if (!local && !("https".equalsIgnoreCase(origin.getScheme()) && origin.getHost() != null)
                || origin.getUserInfo() != null || origin.getFragment() != null || origin.getQuery() != null) {
            throw new IllegalArgumentException("Repository origin must be HTTPS or a local file URI without credentials");
        }
        if (Files.isSymbolicLink(cache)) throw new IOException("Repository cache must not be a symbolic link");
        try {
            Git git = Files.exists(cache) ? Git.open(cache.toFile())
                    : Git.cloneRepository().setURI(origin.toString()).setDirectory(cache.toFile()).setBare(true)
                            .setCloneAllBranches(true).setTimeout(60).call();
            try {
                if (!git.getRepository().isBare()) throw new IOException("Refresh requires a dedicated bare repository cache");
                if (!origin.toString().equals(git.getRepository().getConfig().getString("remote", "origin", "url"))) {
                    throw new IOException("Repository cache belongs to a different origin");
                }
                org.eclipse.jgit.transport.FetchResult fetched = git.fetch().setRemote(origin.toString()).setTimeout(60).setTagOpt(TagOpt.FETCH_TAGS)
                        .setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*"), new RefSpec("refs/tags/*:refs/tags/*"))
                        .call();
                for (org.eclipse.jgit.transport.TrackingRefUpdate update : fetched.getTrackingRefUpdates()) {
                    switch (update.getResult()) {
                        case NEW, FAST_FORWARD, FORCED, NO_CHANGE -> { }
                        default -> throw new IOException("Repository fetch rejected ref update: " + update.getLocalName());
                    }
                }
                return git;
            } catch (IOException | GitAPIException | RuntimeException failure) {
                git.close();
                throw failure;
            }
        } catch (GitAPIException failure) {
            throw new IOException("Unable to refresh repository cache", failure);
        }
    }

    /** Returns repository, tag releases, current modules and author observations with stable upstream identities. */
    public ObjectNode collect(Repository git, ObjectNode metadata, String revision) throws IOException {
        if (metadata.path("id").asText().isBlank() || !metadata.hasNonNull("source_id") || metadata.path("source").asText().isBlank()) {
            throw new IllegalArgumentException("Repository metadata needs stable id, source and source_id");
        }
        RevCommit head = commit(git, revision);
        String root = path(metadata.path("relative_path").asText());
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ObjectNode repository = metadata.deepCopy();
        repository.put("relative_path", root.isEmpty() ? "" : root + "/").put("commit", head.name());
        ArrayNode commits = history(git, head.name(), null, root, Integer.MAX_VALUE);
        repository.set("contributors", contributors(commits, metadata.path("id").asText()));
        if (!commits.isEmpty()) repository.set("last_commit", commits.get(0));
        result.putArray("repositories").add(repository);
        result.set("modules", new ArtifactScanner().scan(git, head.name(), metadata));
        result.set("identities", repository.path("contributors").deepCopy());
        ArrayNode releases = result.putArray("releases");
        List<ObjectNode> tagged = new ArrayList<>();
        try {
            for (Ref ref : Git.wrap(git).tagList().call()) {
                String tag = Repository.shortenRefName(ref.getName());
                if (!TAG.matcher(tag).matches()) continue;
                RevCommit taggedCommit = commit(git, ref.getName());
                if (!root.isEmpty() && history(git, taggedCommit.name(), null, root, 1).isEmpty()) continue;
                ObjectNode release = row("git:tag:", metadata.path("id").asText(), tag);
                release.put("tag", tag).put("version", tag.replaceFirst("^([a-zA-Z]+-?)+", ""))
                        .put("legacy_id", tag).put("source_url", metadata.path("html_url").asText() + "/tree/" + tag)
                        .put("commit", taggedCommit.name()).put("date", taggedCommit.getAuthorIdent().getWhenAsInstant().toString())
                        .put("repository", metadata.path("id").asText()).put("html_url", metadata.path("html_url").asText() + "/commit/" + taggedCommit.name());
                release.set("released_by", person(taggedCommit.getCommitterIdent()));
                release.set("author", person(taggedCommit.getAuthorIdent()));
                release.set("artifacts", new ArtifactScanner().scan(git, taggedCommit.name(), metadata));
                release.putObject("source_payload").put("ref", ref.getName()).put("object", ref.getObjectId().name()).put("commit", taggedCommit.name());
                try (RevWalk walk = new RevWalk(git)) {
                    if (walk.parseAny(ref.getObjectId()) instanceof org.eclipse.jgit.revwalk.RevTag annotated) {
                        release.put("tag_message", annotated.getFullMessage());
                        if (annotated.getTaggerIdent() != null) release.set("tagger", person(annotated.getTaggerIdent()));
                    }
                }
                tagged.add(release);
            }
        } catch (GitAPIException failure) {
            throw new IOException("Unable to list repository tags", failure);
        }
        tagged.sort(Comparator.comparing((ObjectNode row) -> row.path("date").asText()).thenComparing(row -> row.path("tag").asText()));
        String previous = null;
        for (ObjectNode release : tagged) {
            release.set("contributors", contributors(history(git, release.path("commit").asText(), previous, root, Integer.MAX_VALUE), metadata.path("id").asText()));
            releases.add(release);
            previous = release.path("commit").asText();
        }
        repository.put("unreleased_commits", history(git, head.name(), previous, root, Integer.MAX_VALUE).size());
        if (!tagged.isEmpty()) repository.put("latest_version", tagged.getLast().path("version").asText()).put("latest_tag", tagged.getLast().path("tag").asText());
        else if (!result.path("modules").isEmpty() && result.path("modules").get(0).has("version")) repository.set("latest_version", result.path("modules").get(0).get("version"));
        return result;
    }

    /** Resolves a revision to a commit without changing refs or the worktree. */
    static RevCommit commit(Repository git, String revision) throws IOException {
        ObjectId object = git.resolve(revision + "^{commit}");
        if (object == null) throw new IOException("Unknown Git commit revision: " + revision);
        try (RevWalk walk = new RevWalk(git)) {
            return walk.parseCommit(object);
        }
    }

    /** Reads a bounded UTF-8 Git blob at an explicit revision, returning null only when the path is absent. */
    static String read(Repository git, String revision, String path) throws IOException {
        try (TreeWalk tree = TreeWalk.forPath(git, path(path), commit(git, revision).getTree())) {
            if (tree == null) return null;
            if (tree.getFileMode(0).getObjectType() != Constants.OBJ_BLOB || tree.getFileMode(0).getBits() == 0120000) {
                throw new IOException("Metadata must be a regular Git blob: " + path);
            }
            return new String(git.open(tree.getObjectId(0), Constants.OBJ_BLOB).getBytes(4 * 1024 * 1024), StandardCharsets.UTF_8);
        }
    }

    /** Normalizes repository paths and rejects absolute paths, alternate separators and root escapes. */
    static String path(String value) throws IOException {
        if (value.contains("\\") || value.indexOf('\0') >= 0 || value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe repository path: " + value);
        }
        Path normalized = Path.of(value).normalize();
        if (normalized.startsWith("..")) throw new IOException("Repository path escapes root: " + value);
        return normalized.toString().equals(".") ? "" : normalized.toString();
    }

    /** Reads recent path-filtered commits and optional exclusive range boundaries using JGit alone. */
    static ArrayNode history(Repository git, String revision, String excluded, String path, int limit) throws IOException {
        ArrayNode rows = JsonNodeFactory.instance.arrayNode();
        try {
            org.eclipse.jgit.api.LogCommand log = Git.wrap(git).log().add(commit(git, revision));
            if (excluded != null) log.not(commit(git, excluded));
            if (!path.isEmpty()) log.addPath(path);
            log.setMaxCount(limit);
            for (RevCommit commit : log.call()) {
                ObjectNode row = rows.addObject().put("commit", commit.name()).put("sha", commit.name())
                        .put("author", commit.getAuthorIdent().getName()).put("email", commit.getAuthorIdent().getEmailAddress())
                        .put("date", commit.getAuthorIdent().getWhenAsInstant().toString()).put("message", commit.getFullMessage());
                row.set("committer", person(commit.getCommitterIdent()));
            }
            return rows;
        } catch (GitAPIException failure) {
            throw new IOException("Unable to read repository history", failure);
        }
    }

    /** Groups exact email/noreply identities and retains per-repository counts without weak name matching. */
    private ArrayNode contributors(ArrayNode commits, String repositoryId) {
        Map<String, ObjectNode> rows = new TreeMap<>();
        for (JsonNode commit : commits) {
            String email = commit.path("email").asText().toLowerCase(Locale.ROOT);
            Matcher noreply = NOREPLY.matcher(email);
            boolean matched = noreply.matches();
            String numeric = matched ? noreply.group(1) : null;
            String id = "git:author:" + email;
            ObjectNode identity = rows.computeIfAbsent(id, ignored -> {
                ObjectNode author = JsonNodeFactory.instance.objectNode().put("id", id).put("source", "git-email")
                        .put("source_id", email).put("name", commit.path("author").asText()).put("email", email).put("commits", 0)
                        .put("source_url", "git:author:" + email);
                author.putArray("emails").add(email);
                author.putObject("repository_commits");
                author.putObject("source_payload").put("email", commit.path("email").asText()).put("sample_commit", commit.path("commit").asText());
                if (matched) {
                    author.put("github_id_hint", noreply.group(2));
                    if (numeric != null) author.put("github_numeric_id", numeric);
                }
                return author;
            });
            if (java.util.stream.StreamSupport.stream(identity.path("emails").spliterator(), false).noneMatch(value -> value.asText().equals(email))) {
                ((ArrayNode) identity.path("emails")).add(email);
            }
            identity.put("commits", identity.path("commits").asInt() + 1);
            ((ObjectNode) identity.path("repository_commits")).put(repositoryId, identity.path("commits").asInt());
        }
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        rows.values().forEach(result::add);
        return result;
    }

    /** Creates stable Git-derived row identities scoped by immutable repository identity and original source key. */
    static ObjectNode row(String prefix, String repositoryId, String key) {
        return JsonNodeFactory.instance.objectNode().put("id", prefix + repositoryId + ":" + key)
                .put("source", "git:" + repositoryId).put("source_id", key).put("source_url", repositoryId);
    }

    /** Retains Git author/committer names, emails and instants for downstream identity normalization. */
    private static ObjectNode person(PersonIdent identity) {
        return JsonNodeFactory.instance.objectNode().put("name", identity.getName()).put("email", identity.getEmailAddress())
                .put("date", identity.getWhenAsInstant().toString());
    }
}
