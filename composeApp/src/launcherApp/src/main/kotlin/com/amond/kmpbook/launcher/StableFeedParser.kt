package com.amond.kmpbook.launcher

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.DateTimeException
import java.time.Instant

internal class StableFeedParser {
    fun parse(bytes: ByteArray): StableFeed {
        if (bytes.isEmpty() || bytes.size > MAX_FEED_BYTES || bytes.any { it == 0.toByte() }) {
            throw LauncherException("feed-size", "안정 채널 feed 크기가 올바르지 않습니다.")
        }
        val root = try {
            JsonParser.parseString(
                StrictTextDecoder.utf8(bytes, "feed-encoding", "안정 채널 feed가 올바른 UTF-8이 아닙니다."),
            ).asJsonObject
        } catch (error: Exception) {
            throw LauncherException("feed-json", "안정 채널 feed 형식이 올바르지 않습니다.", error)
        }
        requireKeys(
            root,
            setOf("schema", "channel", "version", "publishedAt", "buildCohort", "game", "debugBundle"),
            "feed",
        )
        if (root.requiredInt("schema") != SCHEMA || root.requiredString("channel") != CHANNEL) {
            throw LauncherException("feed-schema", "지원하지 않는 배포 feed입니다.")
        }
        val version = root.requiredString("version")
        if (!VERSION.matches(version)) {
            throw LauncherException("feed-version", "feed의 게임 버전 형식이 올바르지 않습니다.")
        }
        val publishedAt = try {
            Instant.parse(root.requiredString("publishedAt"))
        } catch (error: DateTimeException) {
            throw LauncherException("feed-date", "feed 게시 시각 형식이 올바르지 않습니다.", error)
        }
        if (publishedAt.isAfter(Instant.now().plusSeconds(MAX_CLOCK_SKEW_SECONDS))) {
            throw LauncherException("feed-future-date", "feed 게시 시각이 현재 시각보다 지나치게 미래입니다.")
        }
        val cohort = root.requiredString("buildCohort")
        if (!DigestUtils.isSha256(cohort)) {
            throw LauncherException("feed-cohort", "feed build cohort 형식이 올바르지 않습니다.")
        }
        val gameObject = root.requiredObject("game")
        requireKeys(gameObject, setOf("resource", "size", "sha256", "inventory", "entryPoint"), "game")
        val gameArchive = parseArtifact(gameObject, MAX_GAME_ARCHIVE_BYTES, "game")
        val inventoryObject = gameObject.requiredObject("inventory")
        requireKeys(inventoryObject, setOf("resource", "size", "sha256"), "inventory")
        val inventory = parseArtifact(inventoryObject, MAX_INVENTORY_BYTES.toLong(), "inventory")
        val entryPoint = SafePathPolicy.validateRelativePath(gameObject.requiredString("entryPoint"))
        if (!entryPoint.endsWith(".exe", ignoreCase = true)) {
            throw LauncherException("feed-entrypoint", "게임 실행 파일 경로가 .exe가 아닙니다.")
        }
        val debugObject = root.requiredObject("debugBundle")
        requireKeys(debugObject, setOf("resource", "size", "sha256"), "debugBundle")
        val debugBundle = parseArtifact(debugObject, MAX_DEBUG_ARCHIVE_BYTES, "debugBundle")
        return StableFeed(
            version = version,
            publishedAt = publishedAt,
            buildCohort = cohort,
            game = GameArtifactDescriptor(gameArchive, inventory, entryPoint),
            debugBundle = debugBundle,
        )
    }

    private fun parseArtifact(source: JsonObject, maximumSize: Long, label: String): ArtifactDescriptor {
        val resourcePath = parseArtifactResource(source.requiredString("resource"), label)
        val size = source.requiredLong("size")
        val sha256 = source.requiredString("sha256")
        if (size !in 1..maximumSize || !DigestUtils.isSha256(sha256)) {
            throw LauncherException("feed-artifact", "$label 배포 파일의 크기 또는 해시가 올바르지 않습니다.")
        }
        return ArtifactDescriptor(resourcePath, size, sha256)
    }

    private fun parseArtifactResource(resourcePath: String, label: String): String {
        if (!resourcePath.startsWith(BUNDLED_RELEASE_PREFIX) ||
            !BUNDLED_RESOURCE_NAME.matches(resourcePath.removePrefix(BUNDLED_RELEASE_PREFIX))
        ) {
            throw LauncherException(
                "feed-resource",
                "$label 배포 파일은 런처에 포함된 bundled-release 리소스여야 합니다.",
            )
        }
        return resourcePath
    }

    private fun requireKeys(source: JsonObject, expected: Set<String>, label: String) {
        if (source.keySet() != expected) {
            throw LauncherException("feed-fields", "$label 문서에 필수 필드가 없거나 알 수 없는 필드가 있습니다.")
        }
    }

    private fun JsonObject.requiredElement(name: String): JsonElement {
        val element = get(name)
        if (element == null || element.isJsonNull) {
            throw LauncherException("feed-field", "feed 필드 '$name'이 없습니다.")
        }
        return element
    }

    private fun JsonObject.requiredObject(name: String): JsonObject = try {
        requiredElement(name).asJsonObject
    } catch (error: Exception) {
        throw LauncherException("feed-field-type", "feed 필드 '$name'은 객체여야 합니다.", error)
    }

    private fun JsonObject.requiredString(name: String): String = try {
        requiredElement(name).asJsonPrimitive.let { primitive ->
            if (!primitive.isString || primitive.asString.isEmpty()) throw IllegalArgumentException("string")
            primitive.asString
        }
    } catch (error: Exception) {
        throw LauncherException("feed-field-type", "feed 필드 '$name'은 비어 있지 않은 문자열이어야 합니다.", error)
    }

    private fun JsonObject.requiredLong(name: String): Long = try {
        val primitive = requiredElement(name).asJsonPrimitive
        if (!primitive.isNumber || !INTEGER.matches(primitive.toString())) throw IllegalArgumentException("integer")
        primitive.asLong
    } catch (error: Exception) {
        throw LauncherException("feed-field-type", "feed 필드 '$name'은 정수여야 합니다.", error)
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = requiredLong(name)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw LauncherException("feed-field-range", "feed 필드 '$name'이 정수 범위를 벗어났습니다.")
        }
        return value.toInt()
    }

    companion object {
        const val MAX_FEED_BYTES = 4 * 1024 * 1024
        const val MAX_INVENTORY_BYTES = 16 * 1024 * 1024
        private const val SCHEMA = 1
        private const val CHANNEL = "stable"
        private const val MAX_CLOCK_SKEW_SECONDS = 86_400L
        private const val MAX_GAME_ARCHIVE_BYTES = 8L * 1024L * 1024L * 1024L
        private const val MAX_DEBUG_ARCHIVE_BYTES = 256L * 1024L * 1024L
        private const val BUNDLED_RELEASE_PREFIX = "/bundled-release/"
        private val BUNDLED_RESOURCE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,191}")
        private val VERSION = Regex("(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,4})")
        private val INTEGER = Regex("0|[1-9][0-9]*")
    }
}
