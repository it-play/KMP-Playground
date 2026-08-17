package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.platform.DesktopGameDirectories
import java.nio.file.Path
import java.util.Locale

internal const val CURRENT_MOD_SCHEMA_VERSION: Int = 3
internal const val MAX_MANIFEST_BYTES: Long = 256L * 1024L
internal const val MAX_INSTRUMENT_CONTENT_BYTES: Long = 4L * 1024L * 1024L
internal const val MAX_RUNTIME_JAR_BYTES: Long = 32L * 1024L * 1024L
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

/**
 * 데스크톱 모드 저장소의 OS별 기본 앱 데이터 루트를 결정한다.
 *
 * 기본 경로 규칙은 [DesktopGameDirectories]가 단일 기준이다. 경로만 정규화하며 디렉토리 생성과
 * 심볼릭 링크 검증은 저장소를 여는 측에서 수행한다.
 */
internal fun defaultModAppDataDirectory(): Path {
    return DesktopGameDirectories.discover().userDataRoot
}

/**
 * 저장소와 커버 디코더가 공유하는 유일한 기본 mods 루트다.
 *
 * 호출자는 이 함수가 반환한 경로를 사용하고, 열 때 디렉토리가 심볼릭 링크가
 * 아닌지 별도로 검증해야 한다.
 */
internal fun defaultModsDirectory(): Path = DesktopGameDirectories.discover().mods

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
