# ExtOS CLI

The CLI is a Kotlin/JVM application in the `cli` module and requires JDK 17.
Windows users can invoke the same commands through `tools/ext.ps1` after a
Gradle wrapper has been generated.

```text
./gradlew :cli:run --args="create plugins/hello"
./gradlew :cli:run --args="build plugins/hello build/hello.ext"
./gradlew :cli:run --args="inspect build/hello.ext"
./gradlew :cli:run --args="keygen keys/publisher.json"
./gradlew :cli:run --args="sign build/hello.ext build/hello-signed.ext keys/publisher.json"
```

`build` creates a deterministic-entry-order unsigned development package.
`keygen` creates a raw Ed25519 publisher key. The key file contains private key
material and must never be committed, shared, or included in a plugin package.
`sign` calculates every non-signature file's SHA-256 digest, creates the canonical
integrity payload, and writes `integrity.json` and `signature.json`.

`inspect` validates hashes and the Ed25519 signature when signature metadata is
present. Both CLI and Android enforce bounded file counts and expanded sizes.

The current package signature is self-contained. A future trust store and
registry attestation will decide whether a valid publisher key is trusted.
