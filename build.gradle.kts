import com.amond.kmpbook.build.distribution.AssembleSignedStableReleaseTask

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.nucleus) apply false
}

val releaseAppVersion = providers.gradleProperty("appVersion")

val validateWindowsReleaseEnvironment = tasks.register("validateWindowsReleaseEnvironment") {
    group = "verification"
    description = "Fails before packaging when release-only signing inputs are absent."
    doLast {
        require(System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
            "buildWindowsRelease must run on Windows."
        }
        require(System.getenv("ML_BUILD_CHANNEL")?.trim() == "release") {
            "ML_BUILD_CHANNEL must be exactly 'release' for buildWindowsRelease."
        }
        require(System.getenv("ML_BUILD_COHORT")?.trim()?.matches(Regex("[0-9a-f]{64}")) == true) {
            "ML_BUILD_COHORT must be 64 lowercase hexadecimal characters."
        }
        listOf(
            "ML_DEBUG_BUNDLE_SIGNING_KEY_PKCS8_BASE64",
            "ML_DEBUG_BUNDLE_SIGNING_PUBLIC_KEY_X509_BASE64",
            "ML_FEED_SIGNING_KEY_PKCS8_B64",
            "ML_FEED_SIGNING_PUBLIC_KEY_X509_B64",
            "ML_WINDOWS_SIGNING_CERT_SHA1",
            "ML_RELEASE_PUBLISHED_AT",
        ).forEach { name ->
            require(!System.getenv(name).isNullOrBlank()) { "$name is required for buildWindowsRelease." }
        }
    }
}

project(":composeApp").tasks.matching { it.name == "packageGamePayload" }.configureEach {
    mustRunAfter(validateWindowsReleaseEnvironment)
}
project(":debugModBundle").tasks.matching { it.name == "packageDebugBundle" }.configureEach {
    mustRunAfter(validateWindowsReleaseEnvironment)
}
project(":launcherApp").tasks.matching { it.name == "packageMsi" }.configureEach {
    mustRunAfter(validateWindowsReleaseEnvironment)
}

val assembleSignedStableRelease = tasks.register<AssembleSignedStableReleaseTask>("assembleSignedStableRelease") {
    group = "distribution"
    description = "Verifies both payloads, creates the inventory, and signs the bundled offline feed."
    dependsOn(
        validateWindowsReleaseEnvironment,
        ":composeApp:packageGamePayload",
        ":debugModBundle:packageDebugBundle",
    )
    gameArchive.set(
        project(":composeApp").layout.buildDirectory.file(
            releaseAppVersion.map { version -> "distributions/market-ledger-game-$version-windows-x64.zip" },
        ),
    )
    debugBundleArchive.set(
        project(":debugModBundle").layout.buildDirectory.file(
            releaseAppVersion.map { version -> "distributions/market-ledger-debug-$version-windows-x64.zip" },
        ),
    )
    appVersion.set(releaseAppVersion)
    buildCohort.set(providers.environmentVariable("ML_BUILD_COHORT"))
    publishedAt.set(providers.environmentVariable(AssembleSignedStableReleaseTask.PUBLISHED_AT_ENV))
    allowedBuildDirectory.set(layout.buildDirectory)
    outputDirectory.set(layout.buildDirectory.dir("release"))
    mustRunAfter(validateWindowsReleaseEnvironment)
}

tasks.register("buildWindowsRelease") {
    group = "distribution"
    description = "Builds one self-contained launcher MSI with the signed, cohort-bound game release."
    dependsOn(
        validateWindowsReleaseEnvironment,
        assembleSignedStableRelease,
        ":launcherApp:packageMsi",
    )
}
