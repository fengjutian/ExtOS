# Product definition

## Vision

ExtOS is an application runtime on top of Android. A plugin is presented to the
user as an application inside ExtOS, while Android remains the operating system
and ExtOS remains an ordinary Android application.

ExtOS is not an Android ROM, a compatibility layer for arbitrary APK files, or
a mechanism for bypassing Android permissions.

## Product promise

For users, ExtOS provides one place to install, authorize, launch, update, and
compose lightweight applications.

For developers, ExtOS provides a small SDK, a versioned package format, and a
runtime that turns web code into an Android-integrated plugin without requiring
a complete native Android project.

## Initial audience

- developers building personal tools and AI-assisted workflows;
- teams distributing internal lightweight applications;
- advanced users who want local-first, composable utilities.

The public consumer marketplace is deliberately deferred until package signing,
review, revocation, and runtime isolation have been validated.

## Plugin classes

### Standard plugin

Third-party web content using public ExtOS capabilities. This is the only plugin
class supported by the first public SDK.

### Privileged plugin

A plugin needing an Android role or sensitive capability. Installation and each
sensitive grant require explicit user action. This class is a later milestone.

### System module

Native code shipped and signed with ExtOS. System modules are part of the host,
not downloadable third-party plugins.

## MVP scope

The MVP includes local package installation, manifest validation, a WebView
runtime, plugin-scoped storage, explicit capability grants, lifecycle events,
developer tooling, and one example plugin.

The MVP excludes arbitrary DEX/APK loading, a public marketplace, background
execution without visible user intent, plugin-to-plugin authority sharing, and
generation of standalone APK files.

## Success criteria

A developer can create, preview, package, install, launch, and update a small
plugin. A user can inspect requested capabilities, deny any optional capability,
clear plugin data, and uninstall the plugin. A plugin cannot access a host API
that is absent from both its manifest and the user's grants.
