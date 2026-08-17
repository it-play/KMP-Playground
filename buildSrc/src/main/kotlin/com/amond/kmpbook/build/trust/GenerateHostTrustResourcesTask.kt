package com.amond.kmpbook.build.trust

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Files

@DisableCachingByDefault(because = "The upstream trust material is deliberately ephemeral for development builds.")
abstract class GenerateHostTrustResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val trustMaterialDirectory: DirectoryProperty

    @get:Input
    abstract val hostVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        TrustBuildSupport.requireJava21()
        val material = trustMaterialDirectory.get().asFile.toPath()
        val output = outputDirectory.get().asFile.toPath()
            .resolve("market-ledger")
            .resolve("trust")
        val challenge = Files.readAllBytes(material.resolve("challenge.dat"))
        TrustBuildSupport.validatePairingDat(challenge)
        val cohort = TrustBuildSupport.readCanonicalSingleLine(material.resolve("build-cohort.txt"), "build cohort")
        TrustBuildSupport.validateCohort(cohort)
        val channel = TrustBuildSupport.readCanonicalSingleLine(material.resolve("channel.txt"), "build channel")
        require(channel == TrustBuildSupport.DEV_CHANNEL || channel == TrustBuildSupport.RELEASE_CHANNEL)
        val version = TrustBuildSupport.validateClaimValue("hostVersion", hostVersion.get())
        val publicKey = Files.readAllBytes(material.resolve("debug-signing-public-key.der"))
        TrustBuildSupport.decodePublicKey(publicKey)

        Files.createDirectories(output)
        TrustBuildSupport.atomicWrite(output.resolve("debug-bundle-public-key.der"), publicKey)
        TrustBuildSupport.atomicWrite(output.resolve("build-cohort.txt"), TrustBuildSupport.canonicalText(cohort))
        TrustBuildSupport.atomicWrite(output.resolve("challenge.dat"), challenge)
        TrustBuildSupport.atomicWrite(output.resolve("channel.txt"), TrustBuildSupport.canonicalText(channel))
        TrustBuildSupport.atomicWrite(output.resolve("host-version.txt"), TrustBuildSupport.canonicalText(version))
        logger.lifecycle("Generated host trust resources for debug bundle verification.")
    }
}
