# Ext package format, draft 0

An `.ext` file is a ZIP archive with normalized UTF-8 paths. The root contains a
manifest, plugin content, an icon, and eventually a detached signature.

```text
example.ext
  manifest.json
  dist/index.html
  dist/app.js
  icon.png
  signature.json
```

## Manifest

```json
{
  "schemaVersion": 1,
  "id": "com.example.hello",
  "name": "Hello",
  "version": "0.1.0",
  "runtime": "web",
  "entry": "dist/index.html",
  "minRuntimeVersion": "0.1.0",
  "capabilities": ["runtime.version", "ui.toast"]
}
```

## Validation rules

- `id` uses reverse-domain lowercase ASCII notation.
- `version` and `minRuntimeVersion` use semantic versioning.
- `entry` is a normalized relative path contained by the package.
- capability identifiers must exist in the target runtime API catalog.
- duplicate ZIP paths, absolute paths, `..`, links, and case-colliding paths are
  rejected.
- unpacked file count, individual file size, and total size are bounded.
- undeclared executable entry points are ignored.

## Signatures

Remote distribution will require a publisher signature over a canonical manifest
and a sorted table of file paths and SHA-256 digests. Key identity, rotation,
revocation, timestamping, and registry attestations remain open design work.

Local unsigned packages may eventually be allowed only behind an explicit
developer mode and must be visually distinguished from trusted packages.

## Compatibility

Unknown required fields, unsupported schema versions, unsupported runtimes, and
an unmet minimum runtime version reject installation. Unknown optional metadata
may be preserved but must not alter authorization behavior.

This document is a draft. It is not yet a stable ecosystem contract.
