package org.arquillian.website.data.identity;

import java.util.List;

/** Represents the normalized identity fields shared by provider and page models. */
public record IdentityRecord(String githubId, String username, String name, String email, String profileUrl,
        String avatarUrl, String role, String location, String bio, boolean core, boolean contributor,
        boolean speaker, boolean translator, int contributions, List<String> repositories) {

    /** Returns an immutable identity with no provider-specific optional fields. */
    public IdentityRecord {
        repositories = List.copyOf(repositories == null ? List.of() : repositories);
    }
}
