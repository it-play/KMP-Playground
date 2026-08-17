package com.amond.kmpbook.platform

import java.nio.file.Path

internal class DesktopGameDirectories private constructor(
    val userDataRoot: Path,
    val mods: Path,
    val resources: Path,
    val saves: Path,
    val localDataRoot: Path,
    val runtimeCache: Path,
) {
    companion object {
        fun discover(environment: Map<String, String> = System.getenv()): DesktopGameDirectories {
            val userHome = requireNotNull(System.getProperty("user.home")?.takeIf(String::isNotBlank)) {
                "The JVM user.home property is unavailable."
            }
            val home = Path.of(userHome).toAbsolutePath().normalize()
            val isWindows = System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
            val userRoot = environment[USER_DATA_OVERRIDE]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: if (isWindows) {
                    val roaming = environment["APPDATA"]?.takeIf(String::isNotBlank)?.let(Path::of)
                        ?: home.resolve("AppData/Roaming")
                    roaming.resolve(APP_DIRECTORY)
                } else {
                    home.resolve(NON_WINDOWS_DIRECTORY)
                }
            val localRoot = environment[LOCAL_DATA_OVERRIDE]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: if (isWindows) {
                    val local = environment["LOCALAPPDATA"]?.takeIf(String::isNotBlank)?.let(Path::of)
                        ?: home.resolve("AppData/Local")
                    local.resolve(APP_DIRECTORY)
                } else {
                    home.resolve(NON_WINDOWS_DIRECTORY)
                }
            val canonicalUserRoot = userRoot.toAbsolutePath().normalize()
            val canonicalLocalRoot = localRoot.toAbsolutePath().normalize()
            return DesktopGameDirectories(
                userDataRoot = canonicalUserRoot,
                mods = canonicalUserRoot.resolve("mods"),
                resources = canonicalUserRoot.resolve("resources"),
                saves = canonicalUserRoot.resolve("saves"),
                localDataRoot = canonicalLocalRoot,
                runtimeCache = canonicalLocalRoot.resolve("runtime-cache"),
            )
        }

        private const val USER_DATA_OVERRIDE = "MARKET_LEDGER_USER_DATA_DIR"
        private const val LOCAL_DATA_OVERRIDE = "MARKET_LEDGER_LOCAL_DATA_DIR"
        private const val APP_DIRECTORY = "MarketLedger2040"
        private const val NON_WINDOWS_DIRECTORY = ".market-ledger-2040"
    }
}
