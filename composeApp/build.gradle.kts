import com.amond.kmpbook.build.distribution.ValidateHistoricalScenarioJarTask
import com.amond.kmpbook.build.distribution.ValidateWindowsGameImageTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

val appVersion = providers.gradleProperty("appVersion").get()
val appVersionMatch = Regex("""^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$""").matchEntire(appVersion)
    ?: error("appVersion must use the Windows MSI format MAJOR.MINOR.PATCH: $appVersion")
val (appVersionMajor, appVersionMinor, appVersionBuild) = appVersionMatch.destructured
require(appVersionMajor.toInt() <= 255 && appVersionMinor.toInt() <= 255 && appVersionBuild.toInt() <= 65_535) {
    "appVersion exceeds Windows MSI limits (255.255.65535): $appVersion"
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.nucleus)
}

version = appVersion

tasks.register("printAppVersion") {
    group = "help"
    description = "Prints the single source of truth used for the app and MSI version."
    inputs.property("appVersion", appVersion)
    doLast { println(inputs.properties.getValue("appVersion")) }
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.webview)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.lucide.icons)
        }
        val desktopMain by getting
        desktopMain.resources.exclude("bundled-mods/**")
        desktopMain.resources.srcDir(
            project(":debugModBundle").layout.buildDirectory.dir("generated/hostTrustResources"),
        )
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.gson)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.rodio)
        }
    }
}

nucleus.application {
    mainClass = "com.amond.kmpbook.MainKt"

    nativeDistributions {
        artifactName = "MarketLedger2040-\${version}.\${ext}"
        modules("java.instrument", "java.prefs", "java.sql", "jdk.unsupported")
        cleanupNativeLibs = true
        appResourcesRootDir.set(project.layout.projectDirectory.dir("src/desktopMain/appResources"))
        appName = "Market Ledger 2040"
        packageName = "MarketLedger2040"
        packageVersion = appVersion
        vendor = "Market Ledger 2040"
        description = "Turn-based Korean and U.S. stock market simulator"

        windows {
            iconFile.set(project.file("src/desktopMain/resources/icons/market-ledger.ico"))
            console = false
        }
    }
}

tasks.matching { task -> task.name == "desktopProcessResources" }.configureEach {
    dependsOn(":debugModBundle:generateHostTrustResources")
}

tasks.register("packageDebugBundle") {
    group = "distribution"
    description = "Assembles the signed debug extension distributed next to the game payload."
    dependsOn(":debugModBundle:packageDebugBundle")
}

val windowsGameImage = layout.buildDirectory.dir("compose/binaries/main/app/MarketLedger2040")
val validateHistoricalScenarioJar = tasks.register<ValidateHistoricalScenarioJarTask>(
    "validateHistoricalScenarioJar",
) {
    group = "verification"
    description = "Validates historical scenario and catalog hashes in the packaged Compose JAR."
    val desktopJar = tasks.named<Jar>("desktopJar")
    dependsOn(desktopJar)
    jarFile.set(desktopJar.flatMap(Jar::getArchiveFile))
}
val validateWindowsGameImage = tasks.register<ValidateWindowsGameImageTask>("validateWindowsGameImage") {
    group = "verification"
    description = "Validates the Nucleus Windows app-image before release ZIP creation."
    dependsOn("createDistributable", validateHistoricalScenarioJar)
    imageDirectory.set(windowsGameImage)
}

tasks.register<Zip>("packageGamePayload") {
    group = "distribution"
    description = "Packages the Windows game app-image consumed by the secure launcher."
    dependsOn(validateWindowsGameImage)
    from(windowsGameImage)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("market-ledger-game-$appVersion-windows-x64.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    includeEmptyDirs = false

}
