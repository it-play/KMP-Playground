package com.amond.kmpbook.launcher

import java.nio.charset.StandardCharsets

internal class ReleaseFloor private constructor(val minimumGameVersion: String) {
    companion object {
        private const val RESOURCE = "/market-ledger/release/minimum-game-version.txt"
        private const val MAX_BYTES = 64
        private val VERSION = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")

        fun fromEmbeddedResource(): ReleaseFloor {
            val bytes = ReleaseFloor::class.java.getResourceAsStream(RESOURCE)?.use { input ->
                input.readNBytes(MAX_BYTES + 1)
            } ?: throw LauncherException("release-floor-missing", "런처의 최소 게임 버전 정보가 없습니다.")
            if (bytes.isEmpty() || bytes.size > MAX_BYTES || bytes.last() != '\n'.code.toByte() ||
                bytes.dropLast(1).any { (it.toInt() and 0xff) !in 0x21..0x7e }
            ) {
                throw LauncherException("release-floor-format", "런처의 최소 게임 버전 형식이 올바르지 않습니다.")
            }
            val version = bytes.dropLast(1).toByteArray().toString(StandardCharsets.US_ASCII)
            if (!VERSION.matches(version)) {
                throw LauncherException("release-floor-version", "런처의 최소 게임 버전이 올바르지 않습니다.")
            }
            return ReleaseFloor(version)
        }
    }
}
