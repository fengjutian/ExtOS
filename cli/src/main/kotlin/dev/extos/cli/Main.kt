package dev.extos.cli

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.extension

fun main(arguments: Array<String>) {
    try {
        when (arguments.firstOrNull()) {
            "create" -> createPlugin(requireArgument(arguments, 1, "directory"))
            "build" -> buildPlugin(
                requireArgument(arguments, 1, "plugin directory"),
                requireArgument(arguments, 2, "output .ext"),
            )
            "inspect" -> inspectPlugin(requireArgument(arguments, 1, ".ext package"))
            "keygen" -> generateKey(requireArgument(arguments, 1, "output key file"))
            "sign" -> signPlugin(
                requireArgument(arguments, 1, "input .ext"),
                requireArgument(arguments, 2, "output .ext"),
                requireArgument(arguments, 3, "key file"),
            )
            else -> printUsage()
        }
    } catch (error: Exception) {
        System.err.println("ext: ${error.message ?: error.javaClass.simpleName}")
        kotlin.system.exitProcess(1)
    }
}

private fun createPlugin(directoryText: String) {
    val directory = Path.of(directoryText).toAbsolutePath().normalize()
    require(!Files.exists(directory)) { "Target directory already exists" }
    Files.createDirectories(directory.resolve("dist"))
    val shortId = directory.fileName.toString().lowercase().replace(Regex("[^a-z0-9]"), "")
    require(shortId.isNotEmpty()) { "Directory name cannot produce a valid plugin ID" }
    val id = "dev.example.$shortId"
    Files.writeString(
        directory.resolve("manifest.json"),
        JSONObject()
            .put("schemaVersion", 1)
            .put("id", id)
            .put("name", directory.fileName.toString())
            .put("version", "0.1.0")
            .put("runtime", "web")
            .put("entry", "dist/index.html")
            .put("minRuntimeVersion", "0.1.0")
            .put("capabilities", listOf("runtime.version"))
            .toString(2) + "\n",
        StandardCharsets.UTF_8,
    )
    Files.writeString(
        directory.resolve("dist/index.html"),
        "<!doctype html><meta charset=\"utf-8\"><h1>Hello ExtOS</h1>\n",
        StandardCharsets.UTF_8,
    )
    println("Created $directory")
}

private fun buildPlugin(directoryText: String, outputText: String) {
    val directory = Path.of(directoryText).toAbsolutePath().normalize()
    val output = Path.of(outputText).toAbsolutePath().normalize()
    require(!output.startsWith(directory)) { "Output package must be outside the plugin source directory" }
    require(Files.isRegularFile(directory.resolve("manifest.json"))) { "manifest.json is missing" }
    val files = readDirectory(directory)
        .filterKeys { it !in SIGNATURE_METADATA }
    require(files.containsKey("manifest.json"))
    require(JSONObject(files.getValue("manifest.json").toString(Charsets.UTF_8)).getInt("schemaVersion") == 1)
    writeZip(output, files)
    println("Built $output (${files.size} files, unsigned)")
}

private fun generateKey(outputText: String) {
    val output = Path.of(outputText).toAbsolutePath().normalize()
    require(!Files.exists(output)) { "Key file already exists" }
    output.parent?.let { Files.createDirectories(it) }
    val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
    val publicKey = privateKey.generatePublicKey().encoded
    Files.writeString(
        output,
        JSONObject()
            .put("schemaVersion", 1)
            .put("algorithm", "Ed25519")
            .put("keyId", sha256Hex(publicKey))
            .put("privateKey", Base64.getEncoder().encodeToString(privateKey.encoded))
            .put("publicKey", Base64.getEncoder().encodeToString(publicKey))
            .toString(2) + "\n",
        StandardCharsets.UTF_8,
    )
    println("Generated ${output} (keep this file secret)")
}

private fun signPlugin(inputText: String, outputText: String, keyText: String) {
    val files = readZip(Path.of(inputText)).filterKeys { it !in SIGNATURE_METADATA }.toMutableMap()
    val hashes = files.toSortedMap().mapValues { sha256Hex(it.value) }
    val integrity = JSONObject().put("schemaVersion", 1).put("files", JSONObject(hashes))
    val key = JSONObject(Files.readString(Path.of(keyText), StandardCharsets.UTF_8))
    require(key.getString("algorithm") == "Ed25519") { "Unsupported key algorithm" }
    val privateKey = Ed25519PrivateKeyParameters(Base64.getDecoder().decode(key.getString("privateKey")), 0)
    val publicKey = privateKey.generatePublicKey().encoded
    val payload = canonicalPayload(hashes)
    val signer = Ed25519Signer().apply {
        init(true, privateKey)
        update(payload, 0, payload.size)
    }
    files["integrity.json"] = (integrity.toString(2) + "\n").toByteArray()
    files["signature.json"] = (JSONObject()
        .put("schemaVersion", 1)
        .put("algorithm", "Ed25519")
        .put("keyId", sha256Hex(publicKey))
        .put("publicKey", Base64.getEncoder().encodeToString(publicKey))
        .put("signature", Base64.getEncoder().encodeToString(signer.generateSignature()))
        .toString(2) + "\n").toByteArray()
    writeZip(Path.of(outputText), files)
    println("Signed ${Path.of(outputText).toAbsolutePath()} as ${sha256Hex(publicKey)}")
}

private fun inspectPlugin(packageText: String) {
    val files = readZip(Path.of(packageText))
    val manifest = JSONObject(files["manifest.json"]?.toString(Charsets.UTF_8)
        ?: error("manifest.json is missing"))
    println("${manifest.getString("name")} ${manifest.getString("version")}")
    println("id: ${manifest.getString("id")}")
    println("files: ${files.size}")
    val hasIntegrity = files.containsKey("integrity.json")
    val hasSignature = files.containsKey("signature.json")
    require(hasIntegrity == hasSignature) { "Signature metadata is incomplete" }
    println(if (!hasSignature) "signature: unsigned" else "publisher: ${verifyPackage(files)} (valid)")
}

private fun readDirectory(root: Path): Map<String, ByteArray> {
    val result = sortedMapOf<String, ByteArray>()
    var totalBytes = 0L
    Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { path ->
            require(!Files.isSymbolicLink(path)) { "Symbolic links are not allowed: $path" }
            val relative = root.relativize(path).joinToString("/") { it.toString() }
            require(result.keys.none { it.equals(relative, ignoreCase = true) }) {
                "Duplicate or case-colliding path: $relative"
            }
            require(result.size < 256) { "Plugin has too many files" }
            val size = Files.size(path)
            require(size <= 4L * 1024 * 1024) { "File is too large: $relative" }
            totalBytes += size
            require(totalBytes <= 20L * 1024 * 1024) { "Plugin expanded size is too large" }
            result[relative] = Files.readAllBytes(path)
        }
    }
    return result
}

private fun readZip(path: Path): Map<String, ByteArray> {
    val result = sortedMapOf<String, ByteArray>()
    var totalBytes = 0L
    ZipFile(path.toFile()).use { zip ->
        zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
            val name = entry.name
            require('\\' !in name && !name.startsWith('/') && name.split('/').none {
                it.isBlank() || it == "." || it == ".."
            }) { "Unsafe ZIP path: $name" }
            require(result.keys.none { it.equals(name, ignoreCase = true) }) {
                "Duplicate or case-colliding ZIP entry: $name"
            }
            require(result.size < 256) { "Package has too many files" }
            val content = zip.getInputStream(entry).use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 4 * 1024 * 1024) { "File is too large: $name" }
                    totalBytes += count
                    require(totalBytes <= 20L * 1024 * 1024) { "Package expanded size is too large" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            result[name] = content
        }
    }
    return result
}

private fun writeZip(output: Path, files: Map<String, ByteArray>) {
    require(output.extension.lowercase() == "ext") { "Output must use the .ext extension" }
    output.toAbsolutePath().parent?.let { Files.createDirectories(it) }
    val absolute = output.toAbsolutePath()
    val temporary = absolute.resolveSibling(".${absolute.fileName}.${java.util.UUID.randomUUID()}.tmp")
    try {
        ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
            files.toSortedMap().forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                zip.write(content)
                zip.closeEntry()
            }
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun canonicalPayload(hashes: Map<String, String>): ByteArray = hashes.toSortedMap()
    .entries.joinToString("") { (path, hash) -> "$path\u0000$hash\n" }
    .toByteArray(Charsets.UTF_8)

private fun verifyPackage(files: Map<String, ByteArray>): String {
    val integrity = JSONObject(files["integrity.json"]?.toString(Charsets.UTF_8)
        ?: error("integrity.json is missing"))
    val hashesJson = integrity.getJSONObject("files")
    val packagePaths = files.keys.filterNot { it in SIGNATURE_METADATA }.sorted()
    val declaredPaths = hashesJson.keys().asSequence().toList().sorted()
    require(packagePaths == declaredPaths) { "Integrity table does not match package files" }
    val hashes = packagePaths.associateWith { path ->
        val actual = sha256Hex(files.getValue(path))
        require(actual == hashesJson.getString(path)) { "Hash mismatch for $path" }
        actual
    }
    val signature = JSONObject(files.getValue("signature.json").toString(Charsets.UTF_8))
    require(signature.getString("algorithm") == "Ed25519") { "Unsupported signature algorithm" }
    val publicKey = Base64.getDecoder().decode(signature.getString("publicKey"))
    val keyId = sha256Hex(publicKey)
    require(keyId == signature.getString("keyId")) { "Publisher key ID mismatch" }
    val signed = Base64.getDecoder().decode(signature.getString("signature"))
    val payload = canonicalPayload(hashes)
    val verifier = Ed25519Signer().apply {
        init(false, Ed25519PublicKeyParameters(publicKey, 0))
        update(payload, 0, payload.size)
    }
    require(verifier.verifySignature(signed)) { "Ed25519 signature is invalid" }
    return keyId
}

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

private fun requireArgument(arguments: Array<String>, index: Int, name: String): String =
    arguments.getOrNull(index) ?: error("Missing $name")

private fun printUsage() {
    println("""
        ExtOS CLI
          ext create <directory>
          ext build <plugin-directory> <output.ext>
          ext inspect <package.ext>
          ext keygen <publisher-key.json>
          ext sign <input.ext> <output.ext> <publisher-key.json>
    """.trimIndent())
}

private val SIGNATURE_METADATA = setOf("integrity.json", "signature.json")
