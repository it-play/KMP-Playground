package com.amond.kmpbook.audio

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

private const val APP_RESOURCES_DIRECTORY_PROPERTY: String = "compose.application.resources.dir"
private val SUPPORTED_AUDIO_EXTENSIONS: Set<String> = setOf("mp3", "ogg")
private val PLAYLIST_ORDER_PREFIX: Regex = Regex("^(\\d{2})-")
private val REQUIRED_TRACK_HASHES: Map<String, String> = mapOf(
    "01-growing-threat" to "cefacb4fd5478e5f7a7d9ab198ea186f031edb9afbc8907e319aefded7d1cedb",
    "02-americas-number-one-industry" to "bff4da2f806069dc6cd356d8c73a0ed0e233a2c57389ab266cbed6b9f220a9c9",
    "03-boring-old-banking" to "d31980b45c6e919ffb025b371ea34ca0f8dd308d7cd24d12fd69fcbaf981ae6f",
    "04-machina" to "72f479c583fd1cb894584c51d4fb9b33200b098bd1b13195bd41d29586d9d344",
    "05-solecism" to "7940c69805ea073c2d5d233cf60ba6d3be4ff2e2a47ced0b048321f2971fcd75",
    "06-resonance" to "264be9c44c32419b6de70f0ac24c92f81401419419c8fb111b8e19e62261331b",
    "07-glass-eye" to "d64a63df834fabfd32ec4ec29861f5309bd49ba0eef4c415fc68b90305434ae5",
)

internal fun resolveBackgroundMusicPlaylist(): List<Path> {
    val resourcesDirectory = resolveAppResourcesDirectory()
    val audioDirectory = Path.of(resourcesDirectory).resolve("audio").normalize()
    require(Files.isDirectory(audioDirectory)) {
        "배경음악 디렉터리를 찾을 수 없습니다: $audioDirectory"
    }

    val tracks = Files.list(audioDirectory).use { paths ->
        paths
            .filter { path ->
                Files.isRegularFile(path) &&
                    path.extension.lowercase() in SUPPORTED_AUDIO_EXTENSIONS &&
                    Files.size(path) > 0L
            }
            .sorted(compareBy { path -> path.name.lowercase() })
            .toList()
    }
    require(tracks.isNotEmpty()) {
        "재생할 MP3 또는 OGG 배경음악이 없습니다: $audioDirectory"
    }
    val tracksByOrder = tracks.groupBy { path ->
        PLAYLIST_ORDER_PREFIX.find(path.name)?.groupValues?.get(1)
            ?: error("배경음악 파일명은 두 자리 순번으로 시작해야 합니다: ${path.name}")
    }
    val duplicateOrder = tracksByOrder.entries.firstOrNull { (_, paths) -> paths.size > 1 }
    require(duplicateOrder == null) {
        "같은 트랙 ID의 배경음악은 하나만 둘 수 있습니다: ${duplicateOrder?.key}"
    }
    val tracksByStem = tracks.associateBy { path -> path.nameWithoutExtension.lowercase() }
    REQUIRED_TRACK_HASHES.forEach { (stem, expectedHash) ->
        val track = tracksByStem[stem]
        requireNotNull(track) { "필수 배경음악이 없습니다: $stem.mp3 또는 $stem.ogg" }
        require(track.sha256() == expectedHash) {
            "필수 배경음악 파일이 원본과 일치하지 않습니다: ${track.name}"
        }
    }
    return tracks
}

private fun resolveAppResourcesDirectory(): String {
    System.getProperty(APP_RESOURCES_DIRECTORY_PROPERTY)
        ?.takeIf(String::isNotBlank)
        ?.let { return it }

    val developmentCandidates = listOf(
        Path.of("src/desktopMain/appResources/common"),
        Path.of("composeApp/src/desktopMain/appResources/common"),
    )
    return developmentCandidates
        .firstOrNull(Files::isDirectory)
        ?.toAbsolutePath()
        ?.normalize()
        ?.toString()
        ?: error("배경음악 리소스 디렉터리를 확인할 수 없습니다.")
}

private fun Path.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(this).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
