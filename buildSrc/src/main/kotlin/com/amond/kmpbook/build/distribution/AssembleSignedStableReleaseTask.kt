package com.amond.kmpbook.build.distribution

import com.amond.kmpbook.build.trust.TrustBuildSupport
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Stable release signing consumes a CI private key that must never become a cache input.")
abstract class AssembleSignedStableReleaseTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val gameArchive: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val debugBundleArchive: RegularFileProperty

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val buildCohort: Property<String>

    @get:Input
    abstract val publishedAt: Property<String>

    @get:Input
    abstract val gameEntryPoint: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val allowedBuildDirectory: DirectoryProperty

    init {
        gameEntryPoint.convention(DEFAULT_GAME_ENTRY_POINT)
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun assemble() {
        TrustBuildSupport.requireJava21()
        require(TrustBuildSupport.channelFromEnvironment() == TrustBuildSupport.RELEASE_CHANNEL) {
            "Signed stable release metadata may only be assembled with ML_BUILD_CHANNEL=release."
        }
        val version = appVersion.get().also { value ->
            require(VERSION_PATTERN.matches(value)) { "appVersion must use canonical MAJOR.MINOR.PATCH form." }
        }
        val cohort = TrustBuildSupport.validateCohort(buildCohort.get())
        val published = canonicalPublishedAt(publishedAt.get())
        val resourceDirectory = BUNDLED_RELEASE_RESOURCE_DIRECTORY
        val entryPoint = validateRelativePath(gameEntryPoint.get())
        require(entryPoint.endsWith(".exe", ignoreCase = true)) { "The stable game entrypoint must be a Windows .exe." }

        val output = outputDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        val projectBuild = allowedBuildDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(output.startsWith(projectBuild) && output != projectBuild) {
            "Stable release outputs must be contained by this project's build directory."
        }
        Files.createDirectories(output)
        require(!Files.isSymbolicLink(output) && Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS)) {
            "Stable release output is not a safe directory."
        }
        TrustBuildSupport.deleteDirectoryContents(output)

        val gameCopy = copyArchive(gameArchive.get().asFile.toPath(), output, MAX_GAME_ARCHIVE_BYTES)
        val debugCopy = copyArchive(debugBundleArchive.get().asFile.toPath(), output, MAX_DEBUG_ARCHIVE_BYTES)
        require(gameCopy.fileName != debugCopy.fileName) { "Game and debug bundle archive names must be distinct." }

        val debugSigningPublicKey = TrustBuildSupport.decodePublicKey(
            TrustBuildSupport.PUBLIC_KEY_ENV,
            requireNotNull(System.getenv(TrustBuildSupport.PUBLIC_KEY_ENV)) {
                "${TrustBuildSupport.PUBLIC_KEY_ENV} is required to verify the release debug bundle."
            },
        )
        val challengeDat = verifyDebugBundle(
            archive = output.resolve(debugCopy.fileName),
            expectedCohort = cohort,
            expectedHostVersion = version,
            publicKey = debugSigningPublicKey,
        )
        val inventoryEntries = createGameInventoryAndVerifyTrust(
            archive = output.resolve(gameCopy.fileName),
            expectedCohort = cohort,
            expectedDebugPublicKey = debugSigningPublicKey.encoded,
            expectedChallengeDat = challengeDat,
            expectedHostVersion = version,
            requiredEntryPoint = entryPoint,
            temporaryDirectory = output,
        )
        val inventoryBytes = canonicalInventory(inventoryEntries)
        require(inventoryBytes.size <= MAX_INVENTORY_BYTES) {
            "Canonical game inventory exceeds the launcher size policy."
        }
        val inventoryFileName = "market-ledger-game-$version-windows-x64.inventory.json"
        val inventoryPath = output.resolve(inventoryFileName)
        TrustBuildSupport.atomicWrite(inventoryPath, inventoryBytes)
        val inventoryMetadata = ReleaseArtifactMetadata(
            fileName = inventoryFileName,
            size = inventoryBytes.size.toLong(),
            sha256 = sha256(inventoryBytes),
        )

        val feedBytes = canonicalFeed(
            version = version,
            publishedAt = published,
            cohort = cohort,
            game = gameCopy,
            inventory = inventoryMetadata,
            entryPoint = entryPoint,
            debugBundle = debugCopy,
            resourceDirectory = resourceDirectory,
        )
        require(feedBytes.size <= MAX_FEED_BYTES) { "Canonical stable feed exceeds the launcher size policy." }
        val privateValue = requireNotNull(System.getenv(FEED_PRIVATE_KEY_ENV)) {
            "$FEED_PRIVATE_KEY_ENV is required to assemble a signed stable release."
        }
        val publicValue = requireNotNull(System.getenv(FEED_PUBLIC_KEY_ENV)) {
            "$FEED_PUBLIC_KEY_ENV is required to assemble a signed stable release."
        }
        val privateKey = TrustBuildSupport.decodePrivateKey(FEED_PRIVATE_KEY_ENV, privateValue)
        val publicKey = TrustBuildSupport.decodePublicKey(FEED_PUBLIC_KEY_ENV, publicValue)
        TrustBuildSupport.requireMatchingKeyPair(privateKey, publicKey)
        val signaturePayload = FEED_SIGNATURE_DOMAIN + feedBytes
        val signature = TrustBuildSupport.sign(privateKey, signaturePayload)
        require(signature.size == ED25519_SIGNATURE_BYTES) { "Unexpected stable feed Ed25519 signature size." }
        require(TrustBuildSupport.verify(publicKey, signaturePayload, signature)) {
            "Stable feed signature round-trip verification failed."
        }
        val signatureText = Base64.getEncoder().encode(signature)
        require(signatureText.none { byte -> byte <= 0x20 || byte >= 0x7f }) {
            "Stable feed signature is not canonical single-line Base64."
        }

        TrustBuildSupport.atomicWrite(output.resolve(FEED_FILE_NAME), feedBytes)
        TrustBuildSupport.atomicWrite(output.resolve(FEED_SIGNATURE_FILE_NAME), signatureText)
        verifyFinalRelease(
            output = output,
            game = gameCopy,
            debugBundle = debugCopy,
            inventory = inventoryMetadata,
            inventoryBytes = inventoryBytes,
            feedBytes = feedBytes,
            signatureText = signatureText,
            publicKey = publicKey,
        )
        logger.lifecycle("Assembled and verified signed stable release metadata in $output")
    }

    private fun copyArchive(source: Path, output: Path, maximumSize: Long): ReleaseArtifactMetadata {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("Release input must be a safe regular ZIP file: ${source.fileName}")
        }
        val fileName = source.fileName.toString()
        require(SAFE_ARCHIVE_NAME.matches(fileName)) { "Release archive name is not canonical: $fileName" }
        val size = Files.size(source)
        require(size in 1..maximumSize) { "Release archive size is outside its policy limit: $fileName" }
        val destination = output.resolve(fileName)
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
        require(Files.size(destination) == size) { "Release archive changed while it was staged: $fileName" }
        val sourceHash = sha256(source)
        val copiedHash = sha256(destination)
        require(MessageDigest.isEqual(sourceHash.toAscii(), copiedHash.toAscii())) {
            "Release archive changed while it was copied: $fileName"
        }
        return ReleaseArtifactMetadata(fileName, size, copiedHash)
    }

    private fun createGameInventoryAndVerifyTrust(
        archive: Path,
        expectedCohort: String,
        expectedDebugPublicKey: ByteArray,
        expectedChallengeDat: ByteArray,
        expectedHostVersion: String,
        requiredEntryPoint: String,
        temporaryDirectory: Path,
    ): List<ReleaseInventoryEntry> {
        val inventory = mutableListOf<ReleaseInventoryEntry>()
        val cohortMatches = mutableListOf<String>()
        val publicKeyMatches = mutableListOf<ByteArray>()
        val challengeMatches = mutableListOf<ByteArray>()
        val channelMatches = mutableListOf<String>()
        val hostVersionMatches = mutableListOf<String>()
        val allIdentities = mutableSetOf<String>()
        val fileIdentities = mutableSetOf<String>()
        var totalBytes = 0L
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.size in 1..MAX_GAME_ENTRIES) { "Game ZIP entry count is outside the release policy." }
            entries.forEach { entry ->
                val canonicalPath = validateZipEntryPath(entry)
                val identity = windowsIdentity(canonicalPath)
                require(allIdentities.add(identity)) { "Game ZIP contains a duplicate Windows path: $canonicalPath" }
                if (entry.isDirectory) return@forEach
                require(fileIdentities.add(identity)) { "Game ZIP contains a duplicate file path: $canonicalPath" }
                val size = entry.size
                require(size in 0..MAX_GAME_FILE_BYTES) { "Game ZIP entry size is outside policy: $canonicalPath" }
                totalBytes = Math.addExact(totalBytes, size)
                require(totalBytes <= MAX_GAME_EXPANDED_BYTES) { "Game ZIP expanded size exceeds release policy." }
                val entryHash = hashZipEntry(zip, entry, MAX_GAME_FILE_BYTES)
                inventory += ReleaseInventoryEntry(canonicalPath, size, entryHash)
                if (canonicalPath.endsWith(".jar", ignoreCase = true)) {
                    readTrustResourcesFromNestedJar(
                        zip = zip,
                        entry = entry,
                        temporaryDirectory = temporaryDirectory,
                        cohorts = cohortMatches,
                        publicKeys = publicKeyMatches,
                        challenges = challengeMatches,
                        channels = channelMatches,
                        hostVersions = hostVersionMatches,
                    )
                }
            }
        }
        fileIdentities.forEach { fileIdentity ->
            val segments = fileIdentity.split('/')
            for (index in 1 until segments.size) {
                require(segments.take(index).joinToString("/") !in fileIdentities) {
                    "Game ZIP contains a file/directory collision: $fileIdentity"
                }
            }
        }
        val sorted = inventory.sortedBy(ReleaseInventoryEntry::path)
        require(sorted.any { it.path == requiredEntryPoint }) {
            "Game ZIP does not contain the configured entrypoint: $requiredEntryPoint"
        }
        require(cohortMatches.size == 1) {
            "Game ZIP must contain exactly one $GAME_COHORT_RESOURCE across all nested JARs."
        }
        require(MessageDigest.isEqual(cohortMatches.single().toAscii(), expectedCohort.toAscii())) {
            "Game ZIP build cohort differs from ML_BUILD_COHORT."
        }
        require(publicKeyMatches.size == 1 && MessageDigest.isEqual(publicKeyMatches.single(), expectedDebugPublicKey)) {
            "Game ZIP must contain exactly one debug public key matching the configured release key."
        }
        require(challengeMatches.size == 1 && MessageDigest.isEqual(challengeMatches.single(), expectedChallengeDat)) {
            "Game ZIP and debug bundle pairing DAT files differ."
        }
        require(channelMatches == listOf(TrustBuildSupport.RELEASE_CHANNEL)) {
            "Game ZIP must contain exactly one canonical release channel resource."
        }
        require(hostVersionMatches.size == 1 && MessageDigest.isEqual(
            hostVersionMatches.single().toAscii(),
            expectedHostVersion.toAscii(),
        )) {
            "Game ZIP must contain exactly one host version matching appVersion."
        }
        return sorted
    }

    private fun readTrustResourcesFromNestedJar(
        zip: ZipFile,
        entry: ZipEntry,
        temporaryDirectory: Path,
        cohorts: MutableList<String>,
        publicKeys: MutableList<ByteArray>,
        challenges: MutableList<ByteArray>,
        channels: MutableList<String>,
        hostVersions: MutableList<String>,
    ) {
        val temporary = Files.createTempFile(temporaryDirectory, ".cohort-", ".jar")
        try {
            zip.getInputStream(entry).use { input ->
                Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            require(Files.size(temporary) == entry.size) { "Nested JAR changed while reading: ${entry.name}" }
            ZipFile(temporary.toFile()).use { nested ->
                nested.entries().asSequence().forEach { nestedEntry ->
                    when (nestedEntry.name) {
                        GAME_COHORT_RESOURCE -> cohorts += parseCanonicalCohort(
                            readNestedResource(nested, nestedEntry, CANONICAL_COHORT_BYTES),
                            "game JAR",
                        )
                        GAME_DEBUG_PUBLIC_KEY_RESOURCE -> publicKeys += readNestedResource(
                            nested,
                            nestedEntry,
                            MAX_PUBLIC_KEY_BYTES,
                        )
                        GAME_CHALLENGE_RESOURCE -> challenges += readNestedResource(
                            nested,
                            nestedEntry,
                            MAX_CHALLENGE_DAT_BYTES,
                        ).also(TrustBuildSupport::validatePairingDat)
                        GAME_CHANNEL_RESOURCE -> channels += parseCanonicalSingleLine(
                            readNestedResource(nested, nestedEntry, MAX_CHANNEL_BYTES),
                            "game channel",
                        )
                        GAME_HOST_VERSION_RESOURCE -> hostVersions += parseCanonicalSingleLine(
                            readNestedResource(nested, nestedEntry, MAX_HOST_VERSION_BYTES),
                            "game host version",
                        )
                    }
                }
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun verifyDebugBundle(
        archive: Path,
        expectedCohort: String,
        expectedHostVersion: String,
        publicKey: java.security.PublicKey,
    ): ByteArray {
        ZipFile(archive.toFile()).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val fileNames = entries.filterNot(ZipEntry::isDirectory).map(ZipEntry::getName)
            require(fileNames.toSet() == EXPECTED_DEBUG_FILES && fileNames.size == EXPECTED_DEBUG_FILES.size) {
                "Debug bundle ZIP file closure differs from the signed bundle contract."
            }
            require(entries.none(ZipEntry::isDirectory)) { "Debug bundle ZIP must not contain directory entries." }
            fileNames.forEach(::validateRelativePath)
            val integrityEntries = entries.filter { it.name == DEBUG_INTEGRITY_PATH }
            require(integrityEntries.size == 1) { "Debug bundle ZIP must contain exactly one integrity document." }
            val integrityEntry = integrityEntries.single()
            require(integrityEntry.size in 1..MAX_DEBUG_INTEGRITY_BYTES.toLong()) {
                "Debug bundle integrity document size is outside policy."
            }
            val integrityBytes = zip.getInputStream(integrityEntry).use { input ->
                input.readNBytes(MAX_DEBUG_INTEGRITY_BYTES + 1)
            }
            verifyDebugIntegrityInventory(zip, integrityBytes)
            val lines = parseCanonicalIntegrity(integrityBytes)
            val cohort = uniqueIntegrityClaim(lines, "cohort")
            require(COHORT_PATTERN.matches(cohort) && MessageDigest.isEqual(cohort.toAscii(), expectedCohort.toAscii())) {
                "Debug bundle build cohort differs from ML_BUILD_COHORT."
            }
            require(uniqueIntegrityClaim(lines, "channel") == TrustBuildSupport.RELEASE_CHANNEL) {
                "Stable release metadata requires a release-channel debug bundle."
            }
            require(MessageDigest.isEqual(
                uniqueIntegrityClaim(lines, "hostVersion").toAscii(),
                expectedHostVersion.toAscii(),
            )) {
                "Debug bundle host version differs from appVersion."
            }
            val signatureEntry = entries.single { it.name == DEBUG_SIGNATURE_PATH }
            require(signatureEntry.size == ED25519_SIGNATURE_BYTES.toLong()) {
                "Debug bundle signature must be exactly 64 bytes."
            }
            val signature = zip.getInputStream(signatureEntry).use { input ->
                input.readNBytes(ED25519_SIGNATURE_BYTES + 1)
            }
            require(signature.size == ED25519_SIGNATURE_BYTES && TrustBuildSupport.verify(
                publicKey,
                TrustBuildSupport.signaturePayload(integrityBytes),
                signature,
            )) {
                "Debug bundle signature is not valid for the configured release debug key."
            }
            val challengeEntry = entries.single { it.name == DEBUG_CHALLENGE_PATH }
            val challengeBytes = zip.getInputStream(challengeEntry).use { input ->
                input.readNBytes(MAX_CHALLENGE_DAT_BYTES + 1)
            }
            TrustBuildSupport.validatePairingDat(challengeBytes)
            return challengeBytes
        }
    }

    private fun uniqueIntegrityClaim(lines: List<String>, name: String): String {
        val prefix = "$name="
        val matches = lines.filter { it.startsWith(prefix) }
        require(matches.size == 1) { "Debug bundle integrity must contain exactly one $name claim." }
        return matches.single().removePrefix(prefix)
    }

    private fun readNestedResource(zip: ZipFile, entry: ZipEntry, maximumBytes: Int): ByteArray {
        require(!entry.isDirectory && entry.size in 1..maximumBytes.toLong()) {
            "Nested JAR trust resource is outside its size policy: ${entry.name}"
        }
        val bytes = zip.getInputStream(entry).use { input -> input.readNBytes(maximumBytes + 1) }
        require(bytes.size.toLong() == entry.size) { "Nested JAR trust resource size differs: ${entry.name}" }
        return bytes
    }

    private fun verifyDebugIntegrityInventory(zip: ZipFile, integrityBytes: ByteArray) {
        val lines = parseCanonicalIntegrity(integrityBytes)
        require(lines.size >= 10 && lines.first() == DEBUG_INTEGRITY_HEADER) {
            "Debug bundle integrity header is invalid."
        }
        val countLine = lines.singleOrNull { it.startsWith("files=") }
            ?: throw IllegalArgumentException("Debug bundle integrity has no unique file count.")
        val expectedCount = countLine.removePrefix("files=").toIntOrNull()
            ?: throw IllegalArgumentException("Debug bundle integrity file count is invalid.")
        val inventoryLines = lines.filter { it.startsWith("file=") }
        require(inventoryLines.size == expectedCount) { "Debug bundle integrity file count differs from its inventory." }
        val actualPayloadNames = EXPECTED_DEBUG_FILES - setOf(DEBUG_INTEGRITY_PATH, DEBUG_SIGNATURE_PATH)
        val seen = mutableSetOf<String>()
        inventoryLines.forEach { line ->
            val fields = line.removePrefix("file=").split('\t')
            require(fields.size == 3) { "Debug bundle integrity inventory entry is malformed." }
            val path = validateRelativePath(fields[0])
            val expectedSize = fields[1].toLongOrNull()
                ?: throw IllegalArgumentException("Debug bundle inventory file size is invalid: $path")
            val expectedHash = fields[2]
            require(path in actualPayloadNames && seen.add(path) && SHA256_PATTERN.matches(expectedHash)) {
                "Debug bundle integrity inventory path or hash is invalid: $path"
            }
            val entry = zip.entries().asSequence().single { it.name == path }
            require(entry.size == expectedSize && hashZipEntry(zip, entry, MAX_DEBUG_FILE_BYTES) == expectedHash) {
                "Debug bundle payload differs from its integrity inventory: $path"
            }
        }
        require(seen == actualPayloadNames) { "Debug bundle integrity inventory is incomplete." }
    }

    private fun parseCanonicalIntegrity(bytes: ByteArray): List<String> {
        require(bytes.isNotEmpty() && bytes.size <= MAX_DEBUG_INTEGRITY_BYTES && bytes.last() == '\n'.code.toByte()) {
            "Debug bundle integrity must be a non-empty LF-terminated document."
        }
        require(bytes.none { byte ->
            val value = byte.toInt() and 0xff
            value != '\n'.code && value != '\t'.code && value !in 0x20..0x7e
        }) { "Debug bundle integrity must use canonical ASCII/LF encoding." }
        val lines = bytes.toString(StandardCharsets.US_ASCII).dropLast(1).split('\n')
        require(lines.none(String::isBlank)) { "Debug bundle integrity contains a blank line." }
        return lines
    }

    private fun canonicalInventory(entries: List<ReleaseInventoryEntry>): ByteArray {
        val json = buildString {
            append("{\"schema\":1,\"files\":[")
            entries.forEachIndexed { index, entry ->
                if (index > 0) append(',')
                append("{\"path\":")
                appendJsonString(entry.path)
                append(",\"size\":${entry.size},\"sha256\":\"${entry.sha256}\"}")
            }
            append("]}\n")
        }
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    private fun canonicalFeed(
        version: String,
        publishedAt: String,
        cohort: String,
        game: ReleaseArtifactMetadata,
        inventory: ReleaseArtifactMetadata,
        entryPoint: String,
        debugBundle: ReleaseArtifactMetadata,
        resourceDirectory: String,
    ): ByteArray {
        val json = buildString {
            append("{\"schema\":1,\"channel\":\"stable\",\"version\":\"$version\",\"publishedAt\":")
            appendJsonString(publishedAt)
            append(",\"buildCohort\":\"$cohort\",\"game\":{\"resource\":")
            appendJsonString("$resourceDirectory/${game.fileName}")
            append(",\"size\":${game.size},\"sha256\":\"${game.sha256}\",\"inventory\":{\"resource\":")
            appendJsonString("$resourceDirectory/${inventory.fileName}")
            append(",\"size\":${inventory.size},\"sha256\":\"${inventory.sha256}\"},\"entryPoint\":")
            appendJsonString(entryPoint)
            append("},\"debugBundle\":{\"resource\":")
            appendJsonString("$resourceDirectory/${debugBundle.fileName}")
            append(",\"size\":${debugBundle.size},\"sha256\":\"${debugBundle.sha256}\"}}\n")
        }
        return json.toByteArray(StandardCharsets.UTF_8)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun verifyFinalRelease(
        output: Path,
        game: ReleaseArtifactMetadata,
        debugBundle: ReleaseArtifactMetadata,
        inventory: ReleaseArtifactMetadata,
        inventoryBytes: ByteArray,
        feedBytes: ByteArray,
        signatureText: ByteArray,
        publicKey: java.security.PublicKey,
    ) {
        val expectedNames = setOf(
            game.fileName,
            debugBundle.fileName,
            inventory.fileName,
            FEED_FILE_NAME,
            FEED_SIGNATURE_FILE_NAME,
        )
        val actualNames = Files.list(output).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() }
        require(actualNames == expectedNames) { "Stable release output closure differs from the signed release set." }
        require(Files.readAllBytes(output.resolve(inventory.fileName)).contentEquals(inventoryBytes)) {
            "Stable release inventory changed after hashing."
        }
        val finalFeed = Files.readAllBytes(output.resolve(FEED_FILE_NAME))
        val finalSignatureText = Files.readAllBytes(output.resolve(FEED_SIGNATURE_FILE_NAME))
        require(finalFeed.contentEquals(feedBytes) && finalSignatureText.contentEquals(signatureText)) {
            "Stable feed or detached signature changed after signing."
        }
        val rawSignature = Base64.getDecoder().decode(finalSignatureText)
        require(TrustBuildSupport.verify(publicKey, FEED_SIGNATURE_DOMAIN + finalFeed, rawSignature)) {
            "Final stable feed signature verification failed."
        }
    }

    private fun hashZipEntry(zip: ZipFile, entry: ZipEntry, maximumBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var actualSize = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        zip.getInputStream(entry).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                actualSize = Math.addExact(actualSize, count.toLong())
                require(actualSize <= maximumBytes) { "ZIP entry expanded beyond its release limit: ${entry.name}" }
                digest.update(buffer, 0, count)
            }
        }
        require(actualSize == entry.size) { "ZIP entry size differs from its central directory: ${entry.name}" }
        return hex(digest.digest())
    }

    private fun sha256(path: Path): String {
        require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            "Cannot hash a non-regular release file: $path"
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private fun validateZipEntryPath(entry: ZipEntry): String {
        val raw = if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name
        return validateRelativePath(raw)
    }

    private fun validateRelativePath(value: String): String {
        require(value.isNotBlank() && value.length <= MAX_PATH_LENGTH && !value.startsWith('/') &&
            !value.startsWith('\\') && '\\' !in value && !DRIVE_PREFIX.containsMatchIn(value)
        ) { "Release ZIP contains an unsafe path: $value" }
        val segments = value.split('/')
        require(segments.size <= MAX_PATH_SEGMENTS && segments.none(::isUnsafePathSegment)) {
            "Release ZIP path is outside the Windows path policy: $value"
        }
        return segments.joinToString("/")
    }

    private fun isUnsafePathSegment(segment: String): Boolean {
        if (segment.isBlank() || segment == "." || segment == ".." || segment.length > MAX_PATH_SEGMENT_LENGTH ||
            segment.endsWith(' ') || segment.endsWith('.') || segment.any { character ->
                character.code < 0x20 || character in WINDOWS_FORBIDDEN_CHARACTERS
            }
        ) {
            return true
        }
        return segment.substringBefore('.').uppercase(Locale.ROOT) in WINDOWS_DEVICE_NAMES
    }

    private fun windowsIdentity(path: String): String = path.lowercase(Locale.ROOT)

    private fun parseCanonicalCohort(bytes: ByteArray, label: String): String {
        require(bytes.size == CANONICAL_COHORT_BYTES && bytes.last() == '\n'.code.toByte()) {
            "$label cohort must be exactly 64hex+LF."
        }
        val value = bytes.copyOf(bytes.size - 1).toString(StandardCharsets.US_ASCII)
        require(COHORT_PATTERN.matches(value)) { "$label cohort must be lowercase hexadecimal." }
        return value
    }

    private fun parseCanonicalSingleLine(bytes: ByteArray, label: String): String {
        require(bytes.size >= 2 && bytes.last() == '\n'.code.toByte() && bytes.count { it == '\n'.code.toByte() } == 1) {
            "$label must be one non-empty LF-terminated line."
        }
        require(bytes.dropLast(1).all { byte -> (byte.toInt() and 0xff) in 0x21..0x7e }) {
            "$label must use canonical printable ASCII."
        }
        return bytes.copyOf(bytes.size - 1).toString(StandardCharsets.US_ASCII)
    }

    private fun canonicalPublishedAt(value: String): String = try {
        Instant.parse(value).toString()
    } catch (error: DateTimeException) {
        throw IllegalArgumentException("ML_RELEASE_PUBLISHED_AT must be an ISO-8601 instant.", error)
    }

    private fun String.toAscii(): ByteArray = toByteArray(StandardCharsets.US_ASCII)

    companion object {
        const val FEED_FILE_NAME: String = "market-ledger-stable-feed.json"
        const val FEED_SIGNATURE_FILE_NAME: String = "market-ledger-stable-feed.json.sig"
        const val FEED_PRIVATE_KEY_ENV: String = "ML_FEED_SIGNING_KEY_PKCS8_B64"
        const val FEED_PUBLIC_KEY_ENV: String = "ML_FEED_SIGNING_PUBLIC_KEY_X509_B64"
        const val BUNDLED_RELEASE_RESOURCE_DIRECTORY: String = "/bundled-release"
        const val PUBLISHED_AT_ENV: String = "ML_RELEASE_PUBLISHED_AT"

        private const val DEFAULT_GAME_ENTRY_POINT = "MarketLedger2040.exe"
        private const val GAME_COHORT_RESOURCE = "market-ledger/trust/build-cohort.txt"
        private const val GAME_DEBUG_PUBLIC_KEY_RESOURCE = "market-ledger/trust/debug-bundle-public-key.der"
        private const val GAME_CHALLENGE_RESOURCE = "market-ledger/trust/challenge.dat"
        private const val GAME_CHANNEL_RESOURCE = "market-ledger/trust/channel.txt"
        private const val GAME_HOST_VERSION_RESOURCE = "market-ledger/trust/host-version.txt"
        private const val DEBUG_INTEGRITY_HEADER = "MLDBI1"
        private const val DEBUG_INTEGRITY_PATH = "META-INF/market-ledger/bundle.integrity"
        private const val DEBUG_SIGNATURE_PATH = "META-INF/market-ledger/signature.ed25519"
        private const val DEBUG_CHALLENGE_PATH = "trust/challenge.dat"
        private const val CANONICAL_COHORT_BYTES = 65
        private const val ED25519_SIGNATURE_BYTES = 64
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_GAME_ENTRIES = 50_000
        private const val MAX_GAME_ARCHIVE_BYTES = 8L * 1024L * 1024L * 1024L
        private const val MAX_DEBUG_ARCHIVE_BYTES = 256L * 1024L * 1024L
        private const val MAX_GAME_FILE_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MAX_GAME_EXPANDED_BYTES = 12L * 1024L * 1024L * 1024L
        private const val MAX_DEBUG_FILE_BYTES = 64L * 1024L * 1024L
        private const val MAX_DEBUG_INTEGRITY_BYTES = 64 * 1024
        private const val MAX_CHALLENGE_DAT_BYTES = 16 * 1024
        private const val MAX_PUBLIC_KEY_BYTES = 4 * 1024
        private const val MAX_CHANNEL_BYTES = 32
        private const val MAX_HOST_VERSION_BYTES = 128
        private const val MAX_FEED_BYTES = 4 * 1024 * 1024
        private const val MAX_INVENTORY_BYTES = 16 * 1024 * 1024
        private const val MAX_PATH_LENGTH = 1_024
        private const val MAX_PATH_SEGMENT_LENGTH = 255
        private const val MAX_PATH_SEGMENTS = 64
        private val VERSION_PATTERN = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})")
        private val COHORT_PATTERN = Regex("[0-9a-f]{64}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val SAFE_ARCHIVE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}\\.zip")
        private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
        private val WINDOWS_FORBIDDEN_CHARACTERS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
        private val WINDOWS_DEVICE_NAMES = buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL", "CLOCK$"))
            (1..9).forEach { number ->
                add("COM$number")
                add("LPT$number")
            }
        }
        private val EXPECTED_DEBUG_FILES = setOf(
            "cover.png",
            "lib/market-ledger-debug.jar",
            "manifest.xml",
            "trust/challenge.dat",
            DEBUG_INTEGRITY_PATH,
            DEBUG_SIGNATURE_PATH,
        )
        private val FEED_SIGNATURE_DOMAIN = "MarketLedger2040.StableFeed.v1\u0000"
            .toByteArray(StandardCharsets.UTF_8)
    }
}
