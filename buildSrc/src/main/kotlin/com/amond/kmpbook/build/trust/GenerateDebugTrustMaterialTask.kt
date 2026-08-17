package com.amond.kmpbook.build.trust

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

@DisableCachingByDefault(because = "This task creates ephemeral keys and consumes release secrets without declaring them as cache inputs.")
abstract class GenerateDebugTrustMaterialTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val pairingDatFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        TrustBuildSupport.requireJava21()
        val pairingBytes = Files.readAllBytes(pairingDatFile.get().asFile.toPath())
        TrustBuildSupport.validatePairingDat(pairingBytes)

        val channel = TrustBuildSupport.channelFromEnvironment()
        val output = outputDirectory.get().asFile.toPath()
        Files.createDirectories(output)
        val privateKeyPath = output.resolve("debug-signing-private-key.pk8")
        val publicKeyPath = output.resolve("debug-signing-public-key.der")
        if (channel == TrustBuildSupport.RELEASE_CHANNEL) {
            Files.deleteIfExists(privateKeyPath)
        }
        val configuredCohort = System.getenv(TrustBuildSupport.COHORT_ENV)?.trim().orEmpty()
        val cohort = when {
            configuredCohort.isNotEmpty() -> TrustBuildSupport.validateCohort(configuredCohort)
            channel == TrustBuildSupport.RELEASE_CHANNEL -> error(
                "${TrustBuildSupport.COHORT_ENV} is required for a release build.",
            )
            else -> TrustBuildSupport.randomCohort()
        }

        val publicKeyBytes = if (channel == TrustBuildSupport.RELEASE_CHANNEL) {
            val privateValue = requireNotNull(System.getenv(TrustBuildSupport.PRIVATE_KEY_ENV)) {
                "${TrustBuildSupport.PRIVATE_KEY_ENV} is required for a release build."
            }
            val publicValue = requireNotNull(System.getenv(TrustBuildSupport.PUBLIC_KEY_ENV)) {
                "${TrustBuildSupport.PUBLIC_KEY_ENV} is required for a release build."
            }
            val privateKey = TrustBuildSupport.decodePrivateKey(privateValue)
            val publicKey = TrustBuildSupport.decodePublicKey(publicValue)
            TrustBuildSupport.requireMatchingKeyPair(privateKey, publicKey)
            publicKey.encoded
        } else {
            val pair = TrustBuildSupport.generateEd25519KeyPair()
            TrustBuildSupport.atomicWrite(privateKeyPath, pair.private.encoded)
            TrustBuildSupport.restrictOwnerReadWrite(privateKeyPath)
            pair.public.encoded
        }

        TrustBuildSupport.atomicWrite(publicKeyPath, publicKeyBytes)
        TrustBuildSupport.atomicWrite(output.resolve("challenge.dat"), pairingBytes)
        TrustBuildSupport.atomicWrite(output.resolve("build-cohort.txt"), TrustBuildSupport.canonicalText(cohort))
        TrustBuildSupport.atomicWrite(output.resolve("channel.txt"), TrustBuildSupport.canonicalText(channel))
        logger.lifecycle("Generated $channel debug trust material and a pinned Ed25519 public key.")
    }
}
