package com.amond.kmpbook.launcher

import java.nio.file.Path
import java.util.Locale

internal object SafePathPolicy {
    fun validateRelativePath(value: String): String {
        if (value.isBlank() || value.length > MAX_PATH_LENGTH || value.indexOf('\u0000') >= 0 ||
            value.startsWith('/') || value.startsWith('\\') || value.contains('\\') ||
            DRIVE_PREFIX.containsMatchIn(value)
        ) {
            throw LauncherException("unsafe-path", "배포 파일에 안전하지 않은 경로가 있습니다.")
        }
        val segments = value.split('/')
        if (segments.size > MAX_SEGMENTS || segments.any(::isUnsafeSegment)) {
            throw LauncherException("unsafe-path", "배포 파일 경로가 허용 범위를 벗어났습니다.")
        }
        return segments.joinToString("/")
    }

    fun resolve(root: Path, relativePath: String): Path {
        val canonical = validateRelativePath(relativePath)
        val resolved = root.resolve(canonical.replace('/', root.fileSystem.separator.single())).normalize()
        if (!resolved.startsWith(root)) {
            throw LauncherException("path-escape", "배포 파일 경로가 설치 영역을 벗어납니다.")
        }
        return resolved
    }

    fun windowsIdentity(relativePath: String): String =
        validateRelativePath(relativePath).lowercase(Locale.ROOT)

    private fun isUnsafeSegment(segment: String): Boolean {
        if (segment.isBlank() || segment == "." || segment == ".." || segment.length > MAX_SEGMENT_LENGTH ||
            segment.endsWith(' ') || segment.endsWith('.') || segment.contains(':') ||
            segment.any { it.code < 0x20 }
        ) {
            return true
        }
        val baseName = segment.substringBefore('.').uppercase(Locale.ROOT)
        return baseName in WINDOWS_DEVICE_NAMES
    }

    private const val MAX_PATH_LENGTH = 1_024
    private const val MAX_SEGMENT_LENGTH = 255
    private const val MAX_SEGMENTS = 64
    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    private val WINDOWS_DEVICE_NAMES = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL", "CLOCK$"))
        (1..9).forEach { number ->
            add("COM$number")
            add("LPT$number")
        }
    }
}
