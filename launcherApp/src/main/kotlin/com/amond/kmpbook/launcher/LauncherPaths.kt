package com.amond.kmpbook.launcher

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal data class LauncherPaths(
    val userRoot: Path,
    val mods: Path,
    val resources: Path,
    val saves: Path,
    val localRoot: Path,
    val versions: Path,
    val staging: Path,
    val downloads: Path,
    val quarantine: Path,
    val state: Path,
    val runtimeCache: Path,
) {
    fun createBeforeNetworkAccess() {
        listOf(userRoot, mods, resources, saves).forEach(::createSecureDirectory)
        listOf(localRoot, versions, staging, downloads, quarantine, state, runtimeCache)
            .forEach(::createSecureDirectory)
    }

    private fun createSecureDirectory(path: Path) {
        Files.createDirectories(path)
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw LauncherException("unsafe-directory", "런처 데이터 디렉터리가 안전하지 않습니다: ${path.fileName}")
        }
    }

    companion object {
        fun discover(environment: Map<String, String> = System.getenv()): LauncherPaths {
            val home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
            val roaming = environment["APPDATA"]?.takeIf(String::isNotBlank)?.let(Path::of)
                ?: home.resolve("AppData/Roaming")
            val local = environment["LOCALAPPDATA"]?.takeIf(String::isNotBlank)?.let(Path::of)
                ?: home.resolve("AppData/Local")
            val userRoot = roaming.toAbsolutePath().normalize().resolve(APP_DIRECTORY)
            val localRoot = local.toAbsolutePath().normalize().resolve(APP_DIRECTORY)
            return LauncherPaths(
                userRoot = userRoot,
                mods = userRoot.resolve("mods"),
                resources = userRoot.resolve("resources"),
                saves = userRoot.resolve("saves"),
                localRoot = localRoot,
                versions = localRoot.resolve("versions"),
                staging = localRoot.resolve("staging"),
                downloads = localRoot.resolve("downloads"),
                quarantine = localRoot.resolve("quarantine"),
                state = localRoot.resolve("state"),
                runtimeCache = localRoot.resolve("runtime-cache"),
            )
        }

        private const val APP_DIRECTORY = "MarketLedger2040"
    }
}
