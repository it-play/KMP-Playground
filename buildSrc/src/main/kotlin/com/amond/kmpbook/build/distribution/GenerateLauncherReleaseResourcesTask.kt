package com.amond.kmpbook.build.distribution

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

abstract class GenerateLauncherReleaseResourcesTask : DefaultTask() {
    @get:Input
    abstract val publicKeyBase64: Property<String>

    @get:Input
    abstract val buildChannel: Property<String>

    @get:Input
    abstract val minimumGameVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val channel = buildChannel.get().trim().lowercase()
        require(channel == DEVELOPMENT_CHANNEL || channel == RELEASE_CHANNEL) {
            "ML_BUILD_CHANNEL must be 'dev' or 'release'."
        }
        val configuredKey = publicKeyBase64.get().trim()
        require(channel != RELEASE_CHANNEL || configuredKey.isNotEmpty()) {
            "The stable-feed Ed25519 public key is required for a release launcher."
        }
        val decoded = if (configuredKey.isEmpty()) {
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded
        } else {
            try {
                Base64.getDecoder().decode(configuredKey)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("The launcher release public key is not valid Base64.", error)
            }
        }
        try {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(decoded))
        } catch (error: Exception) {
            throw IllegalArgumentException("The launcher release public key is not a valid X.509 Ed25519 key.", error)
        }
        val encoded = Base64.getEncoder().encodeToString(decoded)

        val output = outputDirectory.get().file(
            "market-ledger/release/stable-feed-public-key.b64",
        ).asFile.toPath()
        Files.createDirectories(output.parent)
        val temporary = Files.createTempFile(output.parent, ".stable-feed-public-key-", ".part")
        try {
            Files.writeString(temporary, encoded, StandardCharsets.US_ASCII)
            try {
                Files.move(
                    temporary,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
            decoded.fill(0)
        }
        val minimumVersion = minimumGameVersion.get().trim()
        require(MSI_VERSION.matches(minimumVersion)) {
            "minimumGameVersion must use MAJOR.MINOR.PATCH."
        }
        val versionOutput = outputDirectory.get().file(
            "market-ledger/release/minimum-game-version.txt",
        ).asFile.toPath()
        Files.createDirectories(versionOutput.parent)
        Files.writeString(versionOutput, "$minimumVersion\n", StandardCharsets.US_ASCII)
    }

    private companion object {
        const val DEVELOPMENT_CHANNEL = "dev"
        const val RELEASE_CHANNEL = "release"
        val MSI_VERSION = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
    }
}
