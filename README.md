# ExtOS

ExtOS is an extensible application runtime for Android. It runs signed,
capability-based plugins inside a host-controlled environment.

The project is currently in its first vertical-slice stage: an Android host
loads a bundled web plugin, validates its manifest, and exposes a deliberately
small capability bridge.

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
- `docs/roadmap.md` — delivery phases and acceptance criteria
- `app/` — Android host and bundled example plugin

## Current development slice

The included host demonstrates:

1. reading a plugin manifest;
2. enforcing the manifest's capability list;
3. loading local plugin content in a WebView;
4. exposing a narrow `ExtOS` JavaScript bridge;
5. keeping navigation inside the packaged plugin.

The bridge currently offers only `runtime.version` and `ui.toast`. It is a
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

## Status

Pre-alpha. Do not install untrusted plugin packages yet.
