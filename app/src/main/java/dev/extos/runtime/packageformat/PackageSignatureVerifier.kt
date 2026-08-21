package dev.extos.runtime.packageformat

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64

class PackageSignatureVerifier {
    fun verifyIfPresent(files: Map<String, ByteArray>): String? {
        val integrityBytes = files[INTEGRITY_PATH]
        val signatureBytes = files[SIGNATURE_PATH]
        if (integrityBytes == null && signatureBytes == null) return null
        if (integrityBytes == null || signatureBytes == null) invalid("Signature metadata is incomplete")

        val integrity = parseObject(integrityBytes, INTEGRITY_PATH)
        requireField(integrity.getInt("schemaVersion") == 1, "Unsupported integrity schema")
        val declaredFiles = integrity.getJSONObject("files")
        val expectedPaths = files.keys.filterNot { it in METADATA_PATHS }.sorted()
        val declaredPaths = declaredFiles.keys().asSequence().toList().sorted()
        requireField(declaredPaths == expectedPaths, "Integrity file table does not match package")

        expectedPaths.forEach { path ->
            val expectedHash = declaredFiles.getString(path)
            requireField(expectedHash.matches(Regex("^[0-9a-f]{64}$")), "Invalid SHA-256 for $path")
            requireField(expectedHash == sha256Hex(files.getValue(path)), "Hash mismatch for $path")
        }

        val signature = parseObject(signatureBytes, SIGNATURE_PATH)
        requireField(signature.getInt("schemaVersion") == 1, "Unsupported signature schema")
        requireField(signature.getString("algorithm") == "Ed25519", "Unsupported signature algorithm")
        val publicKey = decodeBase64(signature.getString("publicKey"), "public key")
        val signatureValue = decodeBase64(signature.getString("signature"), "signature")
        requireField(publicKey.size == 32, "Invalid Ed25519 public key")
        requireField(signatureValue.size == 64, "Invalid Ed25519 signature")

        val keyId = sha256Hex(publicKey)
        requireField(signature.getString("keyId") == keyId, "Publisher key ID does not match public key")
        val payload = canonicalPayload(declaredFiles)
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(publicKey, 0))
            update(payload, 0, payload.size)
        }
        requireField(verifier.verifySignature(signatureValue), "Package signature is invalid")
        return keyId
    }

    private fun canonicalPayload(files: JSONObject): ByteArray = files.keys().asSequence()
        .toList()
        .sorted()
        .joinToString(separator = "", transform = { "$it\u0000${files.getString(it)}\n" })
        .toByteArray(Charsets.UTF_8)

    private fun parseObject(bytes: ByteArray, name: String): JSONObject = try {
        JSONObject(bytes.toString(Charsets.UTF_8))
    } catch (error: Exception) {
        throw InvalidPackageException("Invalid $name: ${error.message ?: "malformed JSON"}")
    }

    private fun decodeBase64(value: String, field: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        invalid("Invalid Base64 $field")
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun requireField(condition: Boolean, message: String) {
        if (!condition) invalid(message)
    }

    private fun invalid(message: String): Nothing = throw InvalidPackageException(message)

    companion object {
        const val INTEGRITY_PATH = "integrity.json"
        const val SIGNATURE_PATH = "signature.json"
        val METADATA_PATHS = setOf(INTEGRITY_PATH, SIGNATURE_PATH)
    }
}
