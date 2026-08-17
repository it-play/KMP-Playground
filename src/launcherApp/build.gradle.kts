import com.amond.kmpbook.build.distribution.GenerateLauncherReleaseResourcesTask
import com.amond.kmpbook.build.distribution.ValidateLauncherSigningConfigurationTask
import dev.nucleusframework.desktop.application.dsl.SigningAlgorithm
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.nucleus)
}

val appVersion = providers.gradleProperty("appVersion").get()
val releasePublicKeyBase64 = providers.environmentVariable("ML_FEED_SIGNING_PUBLIC_KEY_X509_B64")
val windowsSigningThumbprint = providers.environmentVariable("ML_WINDOWS_SIGNING_CERT_SHA1")
val releaseBuildChannel = providers.environmentVariable("ML_BUILD_CHANNEL").orElse("dev")
val generatedReleaseResources = layout.buildDirectory.dir("generated/release-resources")

version = appVersion

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.gson)
}

val generateLauncherReleaseResources = tasks.register<GenerateLauncherReleaseResourcesTask>(
    "generateLauncherReleaseResources",
) {
    description = "Embeds the stable-feed Ed25519 public key in the launcher."
    publicKeyBase64.set(releasePublicKeyBase64.orElse(""))
    buildChannel.set(releaseBuildChannel)
    minimumGameVersion.set(appVersion)
    outputDirectory.set(generatedReleaseResources)
}

sourceSets {
    main {
        resources.srcDir(generateLauncherReleaseResources)
    }
}

tasks.register("printLauncherVersion") {
    group = "help"
    description = "Prints the launcher/MSI version."
    doLast { println(appVersion) }
}

val validateLauncherSigningConfiguration = tasks.register<ValidateLauncherSigningConfigurationTask>(
    "validateLauncherSigningConfiguration",
) {
    buildChannel.set(releaseBuildChannel)
    certificateSha1.set(windowsSigningThumbprint.orElse(""))
}

tasks.matching { task -> task.name == "packageMsi" }.configureEach {
    dependsOn(validateLauncherSigningConfiguration)
}

nucleus.application {
    mainClass = "com.amond.kmpbook.launcher.MainKt"

    nativeDistributions {
        artifactName = "MarketLedger2040-Launcher-\${version}.\${ext}"
        targetFormats(TargetFormat.Msi)
        modules("java.desktop", "java.net.http", "jdk.crypto.ec")
        cleanupNativeLibs = true
        appResourcesRootDir.set(project.layout.projectDirectory.dir("src/main/appResources"))
        appName = "Market Ledger 2040 Launcher"
        packageName = "MarketLedger2040Launcher"
        packageVersion = appVersion
        vendor = "Market Ledger 2040"
        description = "Secure installer, updater, and launcher for Market Ledger 2040"

        windows {
            console = false
            upgradeUuid = "C5CB2FF3-338F-468F-817A-D55BBA54D7EB"
            signing {
                enabled = windowsSigningThumbprint.isPresent
                certificateSha1 = windowsSigningThumbprint.orNull
                algorithm = SigningAlgorithm.Sha256
            }
            msi {
                perMachine = false
            }
        }
    }
}
