# Delivery roadmap

## Phase 0: foundation

- product and architecture documents;
- Android host project;
- bundled example plugin;
- manifest parsing and capability-gated bridge;
- build and smoke-test instructions.

Exit criterion: the bundled plugin runs and undeclared bridge capabilities are
rejected. The repository currently implements this phase in source form, pending
compilation in an Android-capable environment.

## Phase 1: local developer loop

- replace asset URL loading with a controlled local origin (implemented for
  installed plugins; instrumentation tests remain);
- define JSON request/response bridge protocol (initial draft implemented);
- lifecycle events and structured errors (ready event and error envelope implemented);
- JVM CLI `create`, `build`, `inspect`, `keygen`, and `sign` implemented; `dev`
  and device `install` remain;
- package parser with ZIP path and expanded-size safety limits (implemented);
- automated unit and instrumentation tests.

Exit criterion: a developer can build and sideload an unsigned development
package without editing the host.

## Phase 2: user-safe installation

- plugin registry, staged installation, atomic activation, and rollback foundation
  (implemented and connected to the Android UI);
- Android document picker, install review, capability grant persistence, launcher,
  and active-plugin loading (implemented in source form);
- plugin management UI for version activation, inactive-version deletion,
  permission changes, data clearing, and uninstall (implemented in source form);
- startup repair of abandoned staging directories and missing/corrupt active
  version markers (implemented);
- plugin-scoped storage and data removal (implemented);
- permission review and grant UI (implemented);
- SHA-256 file tables and Ed25519 package verification (implemented); publisher
  trust, revocation, and registry attestations remain;
- rollback, quarantine, and audit records.

Exit criterion: packages from multiple publishers can be managed without sharing
data or undeclared capabilities.

## Phase 3: platform APIs

- plugin-private JSON storage with quota and atomic writes (implemented);
- allowlisted HTTPS GET capability with private-address, redirect, timeout, and
  response-size controls (implemented);
- notifications, network policy, file picker, sharing, and approximate location;
- background work with quotas and visible user controls;
- declarative plugin intents and explicit composition;
- SDK version negotiation and compatibility suite.

## Phase 4: distribution

- private registry first;
- publisher verification, review, revocation, and abuse reporting;
- staged rollout and update transparency;
- public marketplace only after a security review.

## Immediate next tasks

1. Compile the host and CLI with JDK 17 and Android SDK 35, then run all tests.
2. Add WebView origin/navigation instrumentation tests on an Android emulator.
3. Add signed key-rotation records, revocation, and registry attestations.
4. Implement CLI `dev` and device `install` after ADB is available.
