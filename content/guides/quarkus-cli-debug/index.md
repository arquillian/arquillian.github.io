---
title: Run Quarkus in Debug Mode from the CLI
description: Use the Quarkus command line tool to start your application in dev mode with a remote debugger attached (or waiting to attach).
authors: arquillian-team
tags: quarkus, cli, debug, dev-mode, maven, gradle
guide_group: 2
guide_order: 20
layout: guide
---

This short guide shows how to run a Quarkus application in debug mode using the Quarkus CLI. It also includes the equivalent Maven and Gradle commands for convenience.

## Prerequisites

- Quarkus CLI installed (see https://quarkus.io/guides/cli-tooling)
- A Quarkus application (any project created with Quarkus)

## Debug with the Quarkus CLI

Start your app in dev mode and enable the Java Debug Wire Protocol (JDWP):

- Enable debug on the default port (5005):

```
quarkus dev -Ddebug
```

- Enable debug on a specific port (for example 5007):

```
quarkus dev -Ddebug=5007
```

- Start and suspend the application until the debugger attaches:

```
quarkus dev -Ddebug -Dsuspend
```

Notes:
- If you pass `-Ddebug` without a value, Quarkus uses port `5005` by default.
- Use your IDE to attach a remote debugger to `localhost:<port>` (e.g., 5005).
- `-Dsuspend` causes the JVM to wait for a debugger before running your app code.

## Equivalent for Maven

```
./mvnw quarkus:dev -Ddebug            # default port 5005
./mvnw quarkus:dev -Ddebug=5007       # custom port
./mvnw quarkus:dev -Ddebug -Dsuspend  # wait for debugger
```

## Equivalent for Gradle

```
./gradlew quarkusDev -Ddebug            # default port 5005
./gradlew quarkusDev -Ddebug=5007       # custom port
./gradlew quarkusDev -Ddebug -Dsuspend  # wait for debugger
```

## Tips

- To disable debugging explicitly, use `-Ddebug=false`.
- If another process uses the chosen port, pick a different one (e.g., `-Ddebug=5010`).
- In containers, make sure to expose the debug port and set the appropriate host bindings.

That’s it—you can now run and debug your Quarkus app directly from the Quarkus CLI.