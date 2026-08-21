package dev.extos.runtime.packageformat

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class PackageSignatureVerifierTest {
    @Test
    fun verifiesSignedFileTable() {
        val packageFiles = signedFiles()
        val signature = JSONObject(packageFiles.getValue("signature.json").toString(Charsets.UTF_8))
        assertEquals(signature.getString("keyId"), PackageSignatureVerifier().verifyIfPresent(packageFiles))
    }

    @Test
    fun rejectsTamperedContent() {
        val packageFiles = signedFiles().toMutableMap()
        packageFiles["dist/index.html"] = "tampered".toByteArray()
        assertThrows(InvalidPackageException::class.java) {
            PackageSignatureVerifier().verifyIfPresent(packageFiles)
        }
    }

    @Test
    fun acceptsPackageWithoutSignatureMetadataAsDevelopmentPackage() {
        assertNull(PackageSignatureVerifier().verifyIfPresent(mapOf("manifest.json" to byteArrayOf(1))))
    }

    private fun signedFiles(): Map<String, ByteArray> {
        val files = sortedMapOf(
            "dist/index.html" to "<h1>Hello</h1>".toByteArray(),
            "manifest.json" to "{}".toByteArray(),
        )
        val hashes = files.mapValues { sha256Hex(it.value) }
        val payload = hashes.toSortedMap().entries.joinToString("") { (path, hash) ->
            "$path\u0000$hash\n"
        }.toByteArray()
        val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
        val publicKey = privateKey.generatePublicKey().encoded
        val signer = Ed25519Signer().apply {
            init(true, privateKey)
            update(payload, 0, payload.size)
        }
        files["integrity.json"] = JSONObject()
            .put("schemaVersion", 1)
            .put("files", JSONObject(hashes))
            .toString().toByteArray()
        files["signature.json"] = JSONObject()
            .put("schemaVersion", 1)
            .put("algorithm", "Ed25519")
            .put("keyId", sha256Hex(publicKey))
            .put("publicKey", Base64.getEncoder().encodeToString(publicKey))
            .put("signature", Base64.getEncoder().encodeToString(signer.generateSignature()))
            .toString().toByteArray()
        return files
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
