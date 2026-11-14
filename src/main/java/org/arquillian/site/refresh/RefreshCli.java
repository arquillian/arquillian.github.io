package org.arquillian.site.refresh;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

/** Runs refresh explicitly from Maven exec or a JDK class path without starting CDI or collecting during rendering. */
public final class RefreshCli {
    /** Prevents construction of the static command entry point. */
    private RefreshCli() {}

    /** Accepts only an explicit configuration file and optional dry run; credentials come from named environment variables. */
    public static void main(String[] arguments) throws IOException {
        ObjectNode result = run(arguments, System.getenv(), Clock.systemUTC());
        System.out.println("Refresh validated: " + result.path("repositories").size() + " repositories, " + result.path("modules").size() + " modules, " + result.path("releases").size() + " announcements");
    }

    /** Exposes deterministic CLI parsing to offline tests without replacing process environment or terminating the JVM. */
    static ObjectNode run(String[] arguments, Map<String, String> environment, Clock clock) throws IOException {
        Path configuration = null;
        boolean dryRun = false;
        for (int index = 0; index < arguments.length; index++) {
            switch (arguments[index]) {
                case "--config" -> {
                    if (configuration != null || ++index >= arguments.length || arguments[index].startsWith("--")) throw new IOException("Usage: --config <file> [--dry-run]");
                    configuration = Path.of(arguments[index]);
                }
                case "--dry-run" -> { if (dryRun) throw new IOException("Duplicate --dry-run"); dryRun = true; }
                default -> throw new IOException("Unknown refresh option; use --config <file> [--dry-run]");
            }
        }
        if (configuration == null) throw new IOException("Usage: --config <file> [--dry-run]");
        return new RefreshCoordinator(RefreshConfig.load(configuration, environment), clock).refresh(dryRun);
    }
}
