import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            packageVersion = "1.0.0"
            vendor = "Market Ledger 2040"
            description = "Turn-based Korean and U.S. stock market simulator"

            windows {
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
