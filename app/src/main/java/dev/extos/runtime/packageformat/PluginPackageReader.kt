package dev.extos.runtime.packageformat

import dev.extos.runtime.model.ManifestParser
import dev.extos.runtime.model.PluginManifest
import java.io.InputStream
import java.nio.charset.CodingErrorAction

data class ValidatedPluginPackage(
    val manifest: PluginManifest,
    val files: Map<String, ByteArray>,
    val publisherKeyId: String?,
)

class PluginPackageReader(
    private val archiveValidator: ArchiveValidator = ArchiveValidator(),
    private val signatureVerifier: PackageSignatureVerifier = PackageSignatureVerifier(),
) {
    fun read(source: InputStream): ValidatedPluginPackage {
        val archive = archiveValidator.read(source)
        val manifestBytes = archive.requireFile("manifest.json")
        val manifestSource = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(manifestBytes))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw InvalidPackageException("manifest.json must be valid UTF-8")
        }
        val manifest = try {
            ManifestParser.parse(manifestSource)
        } catch (error: Exception) {
            throw InvalidPackageException("Invalid manifest: ${error.message ?: "unknown error"}")
        }
        archive.requireFile(manifest.entry)
        val publisherKeyId = signatureVerifier.verifyIfPresent(archive.files)
        return ValidatedPluginPackage(manifest, archive.files, publisherKeyId)
    }
}
