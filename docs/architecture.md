# Runtime architecture

## Layers

```text
ExtOS shell
  Home / search / settings / permission UI
        |
Host runtime
  lifecycle / routing / package manager / policy engine
        |
Capability bridge
  typed messages / validation / grants / auditing
        |
Plugin surface
  isolated WebView and plugin-scoped data
        |
Android framework
```

## Main components

`PackageManager` installs an `.ext` archive into an application-private,
plugin-specific directory. It validates paths, size limits, manifest schema,
file hashes, signatures, and runtime compatibility before activation.

`PluginRegistry` stores installed versions and performs atomic activation and
rollback. One version is active while the previous valid version may be retained
for recovery.

The on-disk draft layout is:

```text
plugins/
  com.example.plugin/
    active.json
    versions/
      1.0.0/
      1.1.0/
  .staging/
```

Installation validates the complete archive in memory, writes into a unique
staging directory, and moves that directory into its immutable version location.
Activation updates a small marker by atomic replacement where the filesystem
supports it, with replacement rename as the compatibility fallback. A crash can
therefore leave an inactive candidate version, but never overwrites the files of
the currently active version.

`PluginSession` owns one running plugin instance and its lifecycle. It binds a
validated manifest and a user grant set to a WebView.

`PolicyEngine` makes every bridge authorization decision. Android permission,
manifest declaration, user grant, foreground state, and parameter policy are
checked independently.

`CapabilityBridge` exposes asynchronous, versioned APIs. Responses use explicit
success and error envelopes. Unknown methods and malformed parameters fail
closed.

## Trust boundaries

Plugin HTML and JavaScript are untrusted. A package signature identifies a
publisher and protects integrity; it does not make plugin behavior trustworthy.

No Android object, filesystem path, ContentProvider, raw Intent, or unrestricted
network client is exposed through the JavaScript bridge. Bridge methods accept
data-transfer values only and validate size and shape before use.

The host's Android permission is not a plugin grant. A call succeeds only when:

```text
declared by plugin
AND granted by user
AND available to host
AND allowed in current lifecycle state
AND parameters satisfy policy
```

## WebView policy

- JavaScript is enabled only for an active plugin surface.
- file and content URL access are disabled unless a reviewed loader requires it;
- plugin content is served from a controlled local origin;
- external navigation is denied by default;
- external subresources and direct network requests are denied by default;
- mixed content and cleartext traffic are disabled;
- debugging is enabled only in developer builds;
- each plugin receives logically isolated storage.

Installed plugin resources are served through a plugin-specific intercepted
synthetic HTTPS origin. Every resolved file must remain inside the active immutable version
directory, including after canonical path and symbolic-link resolution. Default
responses include a restrictive content security policy. Origin-isolation and
navigation-escape instrumentation tests are still required.

## Lifecycle

```text
installed -> validated -> ready -> running -> suspended -> stopped
                   |                    |
                   +---- quarantined <--+
```

Activation is transactional. A new package becomes active only after validation
and health checks. Crashes or integrity failures can quarantine a version.

## Architectural decisions

1. The first runtime is web-first; downloadable native bytecode is excluded.
2. Capabilities are finer-grained than Android permissions.
3. Plugin packages and SDK APIs are independently versioned.
4. Native integrations are host-owned system modules.
5. Composition passes explicit data, never another plugin's authority token.
