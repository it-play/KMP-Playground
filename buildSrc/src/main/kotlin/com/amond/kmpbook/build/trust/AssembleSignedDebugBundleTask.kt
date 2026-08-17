package com.amond.kmpbook.build.trust

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.PrivateKey
import java.security.PublicKey
import java.util.jar.JarFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@DisableCachingByDefault(because = "Signing consumes an undeclared private key and development signing material is ephemeral.")
abstract class AssembleSignedDebugBundleTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val coverFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val trustMaterialDirectory: DirectoryProperty

    @get:Input
    abstract val bundleId: Property<String>

    @get:Input
    abstract val bundleVersion: Property<String>

    @get:Input
    abstract val apiVersion: Property<Int>

    @get:Input
    abstract val capabilities: ListProperty<String>

    @get:Input
    abstract val hostVersion: Property<String>

    @get:Input
    abstract val entrypoint: Property<String>

    @get:Input
    abstract val runtimeJarBundlePath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputZip: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun assemble() {
        TrustBuildSupport.requireJava21()
        val material = trustMaterialDirectory.get().asFile.toPath()
        val challengeBytes = Files.readAllBytes(material.resolve("challenge.dat"))
        TrustBuildSupport.validatePairingDat(challengeBytes)
        val cohort = TrustBuildSupport.readCanonicalSingleLine(material.resolve("build-cohort.txt"), "build cohort")
        TrustBuildSupport.validateCohort(cohort)
        val channel = TrustBuildSupport.readCanonicalSingleLine(material.resolve("channel.txt"), "build channel")
        require(channel == TrustBuildSupport.channelFromEnvironment()) {
            "Trust material channel changed during the build. Run packaging in one Gradle invocation."
        }

        val id = TrustBuildSupport.validateClaimValue("bundleId", bundleId.get())
        val version = TrustBuildSupport.validateClaimValue("version", bundleVersion.get())
        val api = apiVersion.get().also { require(it > 0) { "apiVersion must be positive." } }
        val sortedCapabilities = capabilities.get().map { capability ->
            TrustBuildSupport.validateClaimValue("capability", capability)
        }.distinct().sorted()
        require(sortedCapabilities.isNotEmpty()) { "At least one capability claim is required." }
        val host = TrustBuildSupport.validateClaimValue("hostVersion", hostVersion.get())
        val provider = TrustBuildSupport.validateClaimValue("entrypoint", entrypoint.get())
        val jarBundlePath = TrustBuildSupport.validateBundlePath(runtimeJarBundlePath.get())

        verifyManifest(
            path = manifestFile.get().asFile.toPath(),
            expectedId = id,
            expectedVersion = version,
            expectedApi = api,
            expectedCapabilities = sortedCapabilities,
            expectedRuntimePath = jarBundlePath,
        )
        verifyThinJar(runtimeJar.get().asFile.toPath(), provider)
        val payloadFiles = sortedMapOf(
            "cover.png" to Files.readAllBytes(coverFile.get().asFile.toPath()),
            jarBundlePath to Files.readAllBytes(runtimeJar.get().asFile.toPath()),
            "manifest.xml" to Files.readAllBytes(manifestFile.get().asFile.toPath()),
            "trust/challenge.dat" to challengeBytes,
        )
        payloadFiles.keys.forEach(TrustBuildSupport::validateBundlePath)

        val integrityBytes = buildIntegrity(
            id = id,
            version = version,
            api = api,
            capabilities = sortedCapabilities,
            hostVersion = host,
            cohort = cohort,
            entrypoint = provider,
            channel = channel,
            payloadFiles = payloadFiles,
        )

        val (privateKey, publicKey) = signingKeys(channel, material)
        val signedPayload = TrustBuildSupport.signaturePayload(integrityBytes)
        val signatureBytes = TrustBuildSupport.sign(privateKey, signedPayload)
        require(signatureBytes.size == ED25519_SIGNATURE_SIZE) { "Unexpected Ed25519 signature size." }
        require(TrustBuildSupport.verify(publicKey, signedPayload, signatureBytes)) {
            "Ed25519 signature round-trip verification failed."
        }

        val allFiles = payloadFiles + mapOf(
            INTEGRITY_PATH to integrityBytes,
            SIGNATURE_PATH to signatureBytes,
        )
        writeDirectory(allFiles)
        TrustBuildSupport.createDeterministicZip(outputZip.get().asFile.toPath(), allFiles)
        verifyFinalOutputs(allFiles, publicKey)
        logger.lifecycle("Assembled and round-trip verified signed debug bundle: ${outputZip.get().asFile}")
    }

    private fun signingKeys(channel: String, material: Path): Pair<PrivateKey, PublicKey> {
        val publicKey = TrustBuildSupport.decodePublicKey(
            Files.readAllBytes(material.resolve("debug-signing-public-key.der")),
        )
        val privateKey = if (channel == TrustBuildSupport.RELEASE_CHANNEL) {
            val raw = requireNotNull(System.getenv(TrustBuildSupport.PRIVATE_KEY_ENV)) {
                "${TrustBuildSupport.PRIVATE_KEY_ENV} is required for a release build."
            }
            val configuredPublic = requireNotNull(System.getenv(TrustBuildSupport.PUBLIC_KEY_ENV)) {
                "${TrustBuildSupport.PUBLIC_KEY_ENV} is required for a release build."
            }
            val releasePublicKey = TrustBuildSupport.decodePublicKey(configuredPublic)
            require(releasePublicKey.encoded.contentEquals(publicKey.encoded)) {
                "Release public key changed during the build."
            }
            TrustBuildSupport.decodePrivateKey(raw)
        } else {
            val privatePath = material.resolve("debug-signing-private-key.pk8")
            require(Files.isRegularFile(privatePath)) { "Development private key is missing from the build directory." }
            TrustBuildSupport.decodePrivateKey(Files.readAllBytes(privatePath))
        }
        TrustBuildSupport.requireMatchingKeyPair(privateKey, publicKey)
        return privateKey to publicKey
    }

    private fun buildIntegrity(
        id: String,
        version: String,
        api: Int,
        capabilities: List<String>,
        hostVersion: String,
        cohort: String,
        entrypoint: String,
        channel: String,
        payloadFiles: Map<String, ByteArray>,
    ): ByteArray {
        val lines = buildList {
            add(TrustBuildSupport.INTEGRITY_HEADER)
            add("bundleId=$id")
            add("version=$version")
            add("apiVersion=$api")
            add("capabilities=${capabilities.joinToString(",")}")
            add("hostVersion=$hostVersion")
            add("cohort=$cohort")
            add("entrypoint=$entrypoint")
            add("channel=$channel")
            add("files=${payloadFiles.size}")
            payloadFiles.toSortedMap().forEach { (path, bytes) ->
                add("file=$path\t${bytes.size}\t${TrustBuildSupport.sha256(bytes)}")
            }
        }
        lines.forEach { line ->
            require('\r' !in line && '\n' !in line) { "Integrity lines must not contain embedded newlines." }
        }
        return (lines.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun verifyThinJar(path: Path, expectedProvider: String) {
        val servicePath = "META-INF/services/com.amond.kmpbook.modding.api.runtime.ExecutableGameMod"
        val forbiddenPrefixes = listOf(
            "com/amond/kmpbook/modding/api/",
            "kotlin/",
            "kotlinx/",
            "org/jetbrains/compose/",
        )
        JarFile(path.toFile(), true).use { jar ->
            val entries = jar.entries().asSequence().filterNot { it.isDirectory }.toList()
            require(entries.none { entry -> forbiddenPrefixes.any(entry.name::startsWith) }) {
                "The debug bundle JAR is not thin; it contains host API or bundled runtime classes."
            }
            val serviceEntries = entries.filter { it.name == servicePath }
            require(serviceEntries.size == 1) { "The debug bundle JAR must contain exactly one ExecutableGameMod service file." }
            val providers = jar.getInputStream(serviceEntries.single()).bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
            }
            require(providers == listOf(expectedProvider)) {
                "The ExecutableGameMod service file must contain exactly the signed entrypoint."
            }
            val providerClass = expectedProvider.replace('.', '/') + ".class"
            require(entries.count { it.name == providerClass } == 1) { "The signed entrypoint class is missing from the runtime JAR." }
        }
    }

    private fun verifyManifest(
        path: Path,
        expectedId: String,
        expectedVersion: String,
        expectedApi: Int,
        expectedCapabilities: List<String>,
        expectedRuntimePath: String,
    ) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = Files.newInputStream(path).use { input -> factory.newDocumentBuilder().parse(input) }
        val root = document.documentElement
        require(root.tagName == "mod") { "Debug bundle manifest root must be <mod>." }
        require(root.getAttribute("schemaVersion") == "3") { "Debug bundle manifest must use schemaVersion 3." }
        require(root.getAttribute("apiVersion") == expectedApi.toString()) {
            "Debug bundle manifest apiVersion differs from the signed claim."
        }
        require(root.getAttribute("id") == expectedId) {
            "Debug bundle manifest ID differs from the signed claim."
        }
        require(singleElementText(document, "version") == expectedVersion) {
            "Debug bundle manifest version differs from the signed claim."
        }
        require(singleElementText(document, "jvmJar") == expectedRuntimePath) {
            "Debug bundle manifest runtime JAR differs from the signed inventory path."
        }
        val permissionNodes = document.getElementsByTagName("permission")
        val permissions = (0 until permissionNodes.length)
            .map { index -> permissionNodes.item(index).textContent.trim() }
            .sorted()
        require(permissions == expectedCapabilities) {
            "Debug bundle manifest permissions differ from the signed capability policy."
        }
    }

    private fun singleElementText(
        document: org.w3c.dom.Document,
        tagName: String,
    ): String {
        val elements = document.getElementsByTagName(tagName)
        require(elements.length == 1) { "Debug bundle manifest must contain exactly one <$tagName>." }
        return elements.item(0).textContent.trim()
    }

    private fun writeDirectory(files: Map<String, ByteArray>) {
        val output = outputDirectory.get().asFile.toPath()
        Files.createDirectories(output)
        TrustBuildSupport.deleteDirectoryContents(output)
        files.forEach { (relativePath, bytes) ->
            TrustBuildSupport.atomicWrite(output.resolve(relativePath), bytes)
        }
    }

    private fun verifyFinalOutputs(expectedFiles: Map<String, ByteArray>, publicKey: PublicKey) {
        val output = outputDirectory.get().asFile.toPath()
        val directoryFiles = Files.walk(output).use { paths ->
            paths.filter(Files::isRegularFile).iterator().asSequence().associate { path ->
                output.relativize(path).toString().replace('\\', '/') to Files.readAllBytes(path)
            }
        }
        requireSameFiles(expectedFiles, directoryFiles, "signed bundle directory")

        val zipFiles = TrustBuildSupport.readZipStrict(outputZip.get().asFile.toPath())
        requireSameFiles(expectedFiles, zipFiles, "signed bundle ZIP")
        val integrity = zipFiles.getValue(INTEGRITY_PATH)
        val signature = zipFiles.getValue(SIGNATURE_PATH)
        require(TrustBuildSupport.verify(publicKey, TrustBuildSupport.signaturePayload(integrity), signature)) {
            "Final signed debug bundle verification failed."
        }
        verifyIntegrityInventory(integrity, zipFiles)
    }

    private fun requireSameFiles(
        expected: Map<String, ByteArray>,
        actual: Map<String, ByteArray>,
        label: String,
    ) {
        require(actual.keys == expected.keys) { "$label file set differs from the signed file set." }
        expected.forEach { (path, expectedBytes) ->
            require(actual.getValue(path).contentEquals(expectedBytes)) { "$label entry changed after signing: $path" }
        }
    }

    private fun verifyIntegrityInventory(integrityBytes: ByteArray, files: Map<String, ByteArray>) {
        require(integrityBytes.none { it == '\r'.code.toByte() }) { "Integrity inventory contains CR characters." }
        val text = integrityBytes.toString(StandardCharsets.UTF_8)
        require(text.endsWith('\n')) { "Integrity inventory must end in LF." }
        val lines = text.dropLast(1).split('\n')
        require(lines.firstOrNull() == TrustBuildSupport.INTEGRITY_HEADER) { "Unexpected integrity header." }
        val count = lines.firstOrNull { it.startsWith("files=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
            ?: error("Missing integrity file count.")
        val inventory = lines.filter { it.startsWith("file=") }.map { line ->
            val fields = line.removePrefix("file=").split('\t')
            require(fields.size == 3) { "Malformed integrity inventory entry." }
            val path = TrustBuildSupport.validateBundlePath(fields[0])
            val size = fields[1].toLongOrNull() ?: error("Malformed file size for $path")
            val sha = TrustBuildSupport.validateSha256(fields[2])
            Triple(path, size, sha)
        }
        require(inventory.size == count && inventory.map { it.first } == inventory.map { it.first }.sorted()) {
            "Integrity inventory count or ordering is invalid."
        }
        val unsignedPaths = files.keys - setOf(INTEGRITY_PATH, SIGNATURE_PATH)
        require(inventory.map { it.first }.toSet() == unsignedPaths) { "Integrity inventory file set is incomplete." }
        inventory.forEach { (path, expectedSize, expectedSha) ->
            val bytes = files.getValue(path)
            require(bytes.size.toLong() == expectedSize && TrustBuildSupport.sha256(bytes) == expectedSha) {
                "Integrity inventory mismatch for $path"
            }
        }
    }

    private companion object {
        const val INTEGRITY_PATH = "META-INF/market-ledger/bundle.integrity"
        const val SIGNATURE_PATH = "META-INF/market-ledger/signature.ed25519"
        const val ED25519_SIGNATURE_SIZE = 64
    }
}
