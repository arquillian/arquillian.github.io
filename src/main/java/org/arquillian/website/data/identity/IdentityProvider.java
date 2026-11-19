package org.arquillian.website.data.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Loads committed manual identity records used when optional providers are unavailable. */
public final class IdentityProvider {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /** Loads the manual identity mapping file without contacting a provider. */
    public List<IdentityRecord> load(Path mappingFile) throws IOException {
        Objects.requireNonNull(mappingFile, "mappingFile");
        if (!Files.exists(mappingFile)) {
            return List.of();
        }
        ManualIdentity[] records = mapper.readValue(Files.readString(mappingFile), ManualIdentity[].class);
        return Arrays.stream(records).map(ManualIdentity::toRecord).toList();
    }

    /** Maps the YAML shape into the normalized page-facing identity record. */
    private record ManualIdentity(String github_id, String name, String email, String role, String jboss_username,
            Integer commits) {
        IdentityRecord toRecord() {
            String resolvedName = name == null || name.isBlank() ? github_id : name;
            String resolvedUsername = jboss_username == null || jboss_username.isBlank() ? github_id : jboss_username;
            String profile = github_id == null ? "" : "https://github.com/" + github_id;
            return new IdentityRecord(github_id == null ? "" : github_id, resolvedUsername == null ? "" : resolvedUsername,
                    resolvedName == null ? "" : resolvedName, email == null ? "" : email, profile, "", role == null ? "" : role,
                    "", "", false, commits != null && commits > 0, false, false, commits == null ? 0 : commits,
                    List.of());
        }
    }
}
