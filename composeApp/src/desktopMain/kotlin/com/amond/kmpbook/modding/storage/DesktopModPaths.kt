package com.amond.kmpbook.modding.storage

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale

internal const val CURRENT_MOD_SCHEMA_VERSION: Int = 2
internal const val MAX_MANIFEST_BYTES: Long = 256L * 1024L
internal const val MAX_INSTRUMENT_CONTENT_BYTES: Long = 4L * 1024L * 1024L
internal const val MAX_INSTRUMENTS_PER_MOD: Int = 512
internal const val MAX_MOD_STATE_BYTES: Long = 256L * 1024L
internal const val MAX_DISCOVERED_MOD_ENTRIES: Int = 512
internal const val MAX_AUTOMATIC_COVER_SEARCH_ENTRIES: Int = 256
internal const val MAX_COVER_FILE_BYTES: Long = 8L * 1024L * 1024L
internal const val MAX_COVER_DIMENSION: Int = 4_096
internal const val MAX_COVER_PIXELS: Long = 16_777_216L

internal val MOD_ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{0,63}")
internal val SETTING_KEY_PATTERN: Regex = Regex("[a-z][A-Za-z0-9._-]{0,63}")
internal val ALLOWED_COVER_EXTENSIONS: Set<String> = setOf("png", "jpg", "jpeg", "webp")
internal val AUTOMATIC_COVER_FILE_NAMES: Set<String> = ALLOWED_COVER_EXTENSIONS.mapTo(mutableSetOf()) {
    "cover.$it"
}

internal fun defaultModAppDataDirectory(): Path {
    val userHome = requireNotNull(System.getProperty("user.home")?.takeIf(String::isNotBlank)) {
        "The JVM user.home property is unavailable."
    }
    val osName = System.getProperty("os.name").orEmpty()
    return if (osName.contains("Windows", ignoreCase = true)) {
        val roamingData = System.getenv("APPDATA")
            ?.takeIf(String::isNotBlank)
            ?.let(Paths::get)
            ?: Paths.get(userHome, "AppData", "Roaming")
        roamingData.resolve("MarketLedger2040")
    } else {
        Paths.get(userHome, ".market-ledger-2040")
    }.toAbsolutePath().normalize()
}

internal fun defaultModsDirectory(): Path = defaultModAppDataDirectory().resolve("mods")

internal fun Path.coverExtension(): String =
    fileName.toString().substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)

internal fun safeDirectoryName(path: Path): String = safeDirectoryName(path.fileName?.toString().orEmpty())

internal fun safeDirectoryName(value: String): String {
    val visible = buildString {
        value.take(80).forEach { character ->
            append(if (character.isISOControl()) '\uFFFD' else character)
        }
    }
    return visible.ifBlank { "이름 없는 항목" }
}
