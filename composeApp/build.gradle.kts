import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.gson)
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.amond.kmpbook.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "MarketLedger2040"
            vendor = "Market Ledger 2040"
            description = "Turn-based Korean and U.S. stock market simulator"

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/market-ledger.ico"))
                console = false
                dirChooser = true
                perUserInstall = true
                shortcut = true
                menu = true
                menuGroup = "Market Ledger 2040"
                upgradeUuid = "9D509036-9E61-4F1B-9D02-86F71FC2C184"
            }
        }
    }
}
