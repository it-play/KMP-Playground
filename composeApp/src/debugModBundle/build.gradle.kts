import com.amond.kmpbook.build.trust.AssembleSignedDebugBundleTask
import com.amond.kmpbook.build.trust.GenerateDebugTrustMaterialTask
import com.amond.kmpbook.build.trust.GenerateHostTrustResourcesTask
import com.amond.kmpbook.build.trust.ValidateDebugPairingDatTask
import org.gradle.jvm.tasks.Jar

plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "com.amond.kmpbook.mods"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(
        project(
            path = ":composeApp",
            configuration = "desktopApiElements",
        ),
    )
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

val debugBundleId = "market-ledger.debug"
val debugBundleVersion = project.version.toString()
val hostAppVersion = providers.gradleProperty("appVersion")
val runtimeEntrypoint = "com.amond.kmpbook.debug.bundle.DebugExecutableGameMod"
val runtimeBundlePath = "lib/market-ledger-debug.jar"
val pairingDat = layout.projectDirectory.file("src/main/trust/debug-bundle-challenge.dat")
val trustMaterial = layout.buildDirectory.dir("trust/material")

tasks.named<Jar>("jar") {
    archiveFileName.set("market-ledger-debug.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val validateDebugPairingDat = tasks.register<ValidateDebugPairingDatTask>("validateDebugPairingDat") {
    group = "verification"
    description = "Strictly validates the canonical 4x3 debug bundle pairing DAT."
    pairingDatFile.set(pairingDat)
}

val generateDebugTrustMaterial = tasks.register<GenerateDebugTrustMaterialTask>("generateDebugTrustMaterial") {
    group = "build"
    description = "Generates or validates build-scoped Ed25519 trust material and the CI cohort."
    dependsOn(validateDebugPairingDat)
    pairingDatFile.set(pairingDat)
    outputDirectory.set(trustMaterial)
}

val generateHostTrustResources = tasks.register<GenerateHostTrustResourcesTask>("generateHostTrustResources") {
    group = "build"
    description = "Generates the public-key, cohort, DAT, channel, and version resources pinned by the host."
    dependsOn(generateDebugTrustMaterial)
    trustMaterialDirectory.set(trustMaterial)
    hostVersion.set(hostAppVersion)
    outputDirectory.set(layout.buildDirectory.dir("generated/hostTrustResources"))
}

val packageDebugBundle = tasks.register<AssembleSignedDebugBundleTask>("packageDebugBundle") {
    group = "distribution"
    description = "Assembles and round-trip verifies the signed built-in debug bundle."
    dependsOn(tasks.named("jar"), generateDebugTrustMaterial, generateHostTrustResources)
    runtimeJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    manifestFile.set(layout.projectDirectory.file("src/main/bundle/manifest.xml"))
    coverFile.set(
        rootProject.layout.projectDirectory.file(
            "composeApp/src/desktopMain/resources/bundled-mods/market-ledger.debug/cover.png",
        ),
    )
    trustMaterialDirectory.set(trustMaterial)
    bundleId.set(debugBundleId)
    bundleVersion.set(debugBundleVersion)
    apiVersion.set(2)
    capabilities.set(
        listOf(
            "game.read",
            "game.playerCommands",
            "game.marketControl",
            "game.debugConsole",
        ),
    )
    hostVersion.set(providers.gradleProperty("appVersion"))
    entrypoint.set(runtimeEntrypoint)
    runtimeJarBundlePath.set(runtimeBundlePath)
    outputDirectory.set(layout.buildDirectory.dir("signedBundle/$debugBundleId"))
    outputZip.set(
        layout.buildDirectory.file(
            hostAppVersion.map { version -> "distributions/market-ledger-debug-$version-windows-x64.zip" },
        ),
    )
}

tasks.named("assemble") {
    dependsOn(packageDebugBundle)
}
