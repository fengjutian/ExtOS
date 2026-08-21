# ExtOS

ExtOS is an extensible application runtime for Android. It runs signed,
capability-based plugins inside a host-controlled environment.

The project is currently in its first usable vertical-slice stage: an Android
host imports a local `.ext` package, validates and installs it, shows it in a
launcher, and runs its active version behind a deliberately small capability
bridge.

## Product principles

- Web-first plugin runtime
- Explicit, fine-grained capabilities
- Host-controlled lifecycle and storage
- Versioned `.ext` package format
- Package signing before remote distribution
- Plugin composition without sharing authority implicitly

## Repository map

- `docs/product.md` — product definition and boundaries
- `docs/architecture.md` — runtime architecture and trust boundaries
- `docs/package-format.md` — initial `.ext` package specification
- `docs/bridge-protocol.md` — asynchronous host/plugin message contract
- `docs/roadmap.md` — delivery phases and acceptance criteria
- `app/` — Android host and bundled example plugin

## Current development slice

The included host demonstrates:

1. choosing and reviewing a local `.ext` package;
2. reading a plugin manifest;
3. enforcing the manifest's capability list and persisted user grants;
4. installing immutable versions with an atomic active-version marker;
5. loading plugin content from a controlled origin in a WebView;
6. exposing a narrow `ExtOS` JavaScript bridge;
7. blocking external navigation and subresources;
8. validating archive paths and bounded expanded content.

The asynchronous bridge currently offers only `runtime.version` and `ui.toast`. It is a
prototype API, not yet a security-complete third-party plugin sandbox.

## Build prerequisites

- JDK 17
- Android SDK 35
- Android Studio or Gradle 8.10+

This repository intentionally does not commit a generated Gradle wrapper JAR.
Open it in Android Studio, or generate a wrapper with a trusted local Gradle
installation before running:

```text
gradle wrapper
./gradlew assembleDebug
```

On Windows, build the included example into an installable development package:

```text
powershell -ExecutionPolicy Bypass -File tools/build-sample.ps1
```

The package is written to `build/sample/hello.ext`. It is unsigned and intended
only for local development.

## Status

Pre-alpha. Do not install untrusted plugin packages yet.
