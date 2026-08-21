# Ext package format, draft 0

An `.ext` file is a ZIP archive with normalized UTF-8 paths. The root contains a
manifest, plugin content, optional presentation assets, and optional signature
metadata.

```text
example.ext
  manifest.json
  dist/index.html
  dist/app.js
  icon.png
  integrity.json
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
  "capabilities": ["runtime.version", "ui.toast"],
  "networkAllowlist": []
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

Signed packages include `integrity.json`, containing the SHA-256 digest of every
non-signature file, and `signature.json`. Ed25519 signs UTF-8 records sorted by
path, with each record encoded as `path + NUL + lowercaseHash + LF`. The key ID
is the lowercase SHA-256 digest of the raw 32-byte public key.

Key trust, rotation, revocation, timestamping, and registry attestations remain
open design work. A valid self-contained signature proves integrity and stable
publisher-key identity; it does not establish that the publisher is trustworthy.

Local unsigned packages are visually distinguished from signed packages. The
current Android host accepts them only in Debug builds; Release builds reject
them.

The host remembers the first accepted signed publisher key for each plugin ID.
Subsequent signed updates must use the same key. Formal key-rotation records are
not implemented yet, so uninstalling currently removes this local continuity
record.

## Compatibility

Unknown required fields, unsupported schema versions, unsupported runtimes, and
an unmet minimum runtime version reject installation. Unknown optional metadata
may be preserved but must not alter authorization behavior.

This document is a draft. It is not yet a stable ecosystem contract.
