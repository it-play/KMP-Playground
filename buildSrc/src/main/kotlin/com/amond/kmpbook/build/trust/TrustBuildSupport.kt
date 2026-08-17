package com.amond.kmpbook.build.trust

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object TrustBuildSupport {
    const val CHANNEL_ENV: String = "ML_BUILD_CHANNEL"
    const val COHORT_ENV: String = "ML_BUILD_COHORT"
    const val PRIVATE_KEY_ENV: String = "ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64"
    const val PUBLIC_KEY_ENV: String = "ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64"
    const val DEV_CHANNEL: String = "dev"
    const val RELEASE_CHANNEL: String = "release"
    const val INTEGRITY_HEADER: String = "MLDBI1"
    const val SIGNATURE_DOMAIN: String = "MarketLedger2040.DebugBundle.Integrity.v1\u0000"

    private val fragmentPattern = Regex("[A-Za-z0-9_-]{1000}")
    private val cohortPattern = Regex("[0-9a-f]{64}")
    private val claimValuePattern = Regex("[A-Za-z0-9._:/@,+-]+")
    private val bundlePathPattern = Regex("[A-Za-z0-9._/-]+")
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun requireJava21() {
        require(Runtime.version().feature() >= 21) {
            "Debug bundle trust tasks require JDK 21 or newer."
        }
    }

    fun channelFromEnvironment(): String {
        val channel = System.getenv(CHANNEL_ENV)?.trim()?.lowercase().orEmpty().ifEmpty { DEV_CHANNEL }
        require(channel == DEV_CHANNEL || channel == RELEASE_CHANNEL) {
            "$CHANNEL_ENV must be either '$DEV_CHANNEL' or '$RELEASE_CHANNEL'."
        }
        return channel
    }

    fun validatePairingDat(bytes: ByteArray): List<List<String>> {
        require(bytes.isNotEmpty()) { "The debug pairing DAT must not be empty." }
        require(bytes.none { it == '\r'.code.toByte() }) { "The debug pairing DAT must use canonical LF line endings." }
        val text = bytes.toString(StandardCharsets.US_ASCII)
        require(text.toByteArray(StandardCharsets.US_ASCII).contentEquals(bytes)) {
            "The debug pairing DAT must contain ASCII only."
        }
        require(text.endsWith('\n')) { "The debug pairing DAT must end with one LF." }
        val lines = text.dropLast(1).split('\n')
        require(lines.size == 4) { "The debug pairing DAT must contain exactly four bracketed groups." }
        val groups = lines.mapIndexed { groupIndex, line ->
            require(line.startsWith('[') && line.endsWith(']')) {
                "DAT group ${groupIndex + 1} must be enclosed by '[' and ']'."
            }
            val fragments = line.substring(1, line.length - 1).split("&^")
            require(fragments.size == 3) {
                "DAT group ${groupIndex + 1} must contain exactly three '&^'-delimited fragments."
            }
            fragments.forEachIndexed { fragmentIndex, fragment ->
                require(fragmentPattern.matches(fragment)) {
                    "DAT group ${groupIndex + 1}, fragment ${fragmentIndex + 1} must be exactly 1000 characters from [A-Za-z0-9_-]."
                }
            }
            fragments
        }
        require(groups.flatten().toSet().size == 12) { "All twelve DAT fragments must be unique." }
        return groups
    }

    fun validateCohort(value: String): String {
        require(cohortPattern.matches(value)) {
            "$COHORT_ENV must contain exactly 64 lowercase hexadecimal characters."
        }
        return value
    }

    fun randomCohort(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun generateEd25519KeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    fun decodePrivateKey(raw: String): PrivateKey {
        return decodePrivateKey(PRIVATE_KEY_ENV, raw)
    }

    fun decodePrivateKey(environmentName: String, raw: String): PrivateKey {
        val bytes = decodeBase64Environment(environmentName, raw)
        return try {
            KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    fun decodePublicKey(raw: String): PublicKey {
        return decodePublicKey(PUBLIC_KEY_ENV, raw)
    }

    fun decodePublicKey(environmentName: String, raw: String): PublicKey {
        val bytes = decodeBase64Environment(environmentName, raw)
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun decodePublicKey(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(bytes))

    fun decodePrivateKey(bytes: ByteArray): PrivateKey =
        KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(bytes))

    fun requireMatchingKeyPair(privateKey: PrivateKey, publicKey: PublicKey) {
        val challenge = ByteArray(64).also(SecureRandom()::nextBytes)
        val signature = sign(privateKey, challenge)
        require(verify(publicKey, challenge, signature)) {
            "The configured Ed25519 private and public keys do not form a matching pair."
        }
        challenge.fill(0)
        signature.fill(0)
    }

    fun sign(privateKey: PrivateKey, bytes: ByteArray): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(bytes)
            sign()
        }

    fun verify(publicKey: PublicKey, bytes: ByteArray, signature: ByteArray): Boolean =
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(bytes)
            verify(signature)
        }

    fun signaturePayload(integrityBytes: ByteArray): ByteArray =
        SIGNATURE_DOMAIN.toByteArray(StandardCharsets.UTF_8) + integrityBytes

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    fun validateClaimValue(name: String, value: String): String {
        require(claimValuePattern.matches(value)) { "$name contains characters that are not canonical claim characters." }
        return value
    }

    fun validateBundlePath(path: String): String {
        require(bundlePathPattern.matches(path) && !path.startsWith('/') && ".." !in path.split('/')) {
            "Unsafe or non-canonical bundle path: $path"
        }
        return path
    }

    fun validateSha256(value: String): String {
        require(sha256Pattern.matches(value)) { "Invalid lowercase SHA-256 value: $value" }
        return value
    }

    fun canonicalText(vararg lines: String): ByteArray =
        (lines.joinToString(separator = "\n") + "\n").toByteArray(StandardCharsets.UTF_8)

    fun readCanonicalSingleLine(path: Path, label: String): String {
        val bytes = Files.readAllBytes(path)
        require(bytes.none { it == '\r'.code.toByte() }) { "$label must use LF line endings." }
        val text = bytes.toString(StandardCharsets.UTF_8)
        require(text.endsWith('\n') && text.count { it == '\n' } == 1) { "$label must contain one line ending in LF." }
        return text.dropLast(1)
    }

    fun atomicWrite(path: Path, bytes: ByteArray) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun restrictOwnerReadWrite(path: Path) {
        if (!Files.getFileStore(path).supportsFileAttributeView("posix")) return
        Files.setPosixFilePermissions(
            path,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )
    }

    fun createDeterministicZip(zipPath: Path, files: Map<String, ByteArray>) {
        Files.createDirectories(zipPath.parent)
        val temporary = Files.createTempFile(zipPath.parent, ".${zipPath.fileName}.", ".tmp")
        try {
            ZipOutputStream(Files.newOutputStream(temporary)).use { zip ->
                files.toSortedMap().forEach { (rawPath, bytes) ->
                    val path = validateBundlePath(rawPath)
                    val entry = ZipEntry(path).apply { time = 0L }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            try {
                Files.move(
                    temporary,
                    zipPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, zipPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun readZipStrict(zipPath: Path): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(Files.newInputStream(zipPath)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "The signed debug bundle must not contain directory entries." }
                val path = validateBundlePath(entry.name)
                require(path !in entries) { "Duplicate ZIP entry: $path" }
                val output = ByteArrayOutputStream()
                zip.copyTo(output)
                entries[path] = output.toByteArray()
                zip.closeEntry()
            }
        }
        return entries
    }

    fun deleteDirectoryContents(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path != directory) Files.deleteIfExists(path)
            }
        }
    }

    private fun decodeBase64Environment(name: String, raw: String): ByteArray = try {
        require(raw.isNotBlank() && raw.none(Char::isWhitespace)) { "$name must be a non-empty base64 value without whitespace." }
        Base64.getDecoder().decode(raw)
    } catch (exception: IllegalArgumentException) {
        throw IllegalArgumentException("$name is not valid base64.", exception)
    }
}
