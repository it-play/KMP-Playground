package com.amond.kmpbook.launcher

internal object VersionNaming {
    fun directoryName(feed: StableFeed): String = "${feed.version}-${feed.game.archive.sha256.take(HASH_PREFIX_LENGTH)}"

    fun validateDirectoryName(value: String): String {
        if (!DIRECTORY_NAME.matches(value)) {
            throw LauncherException("version-name", "active 게임 버전 이름이 안전하지 않습니다.")
        }
        return value
    }

    private const val HASH_PREFIX_LENGTH = 16
    private val DIRECTORY_NAME = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})-[0-9a-f]{16}")
}
