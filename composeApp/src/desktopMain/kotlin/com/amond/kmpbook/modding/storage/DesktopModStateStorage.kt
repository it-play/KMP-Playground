package com.amond.kmpbook.modding.storage

import com.amond.kmpbook.modding.model.ActiveModConfiguration
import com.amond.kmpbook.modding.model.MAX_ACTIVE_MODS
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal class DesktopModStateStorage(
    private val appDataDirectory: Path,
) {
    private val statePath: Path = appDataDirectory.resolve("mods-state.json")
    private val lockPath: Path = appDataDirectory.resolve("mods-state.lock")
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setStrictness(Strictness.STRICT)
        .create()

    fun read(): Pair<Map<String, StoredModState>, String?> {
        if (!Files.exists(statePath, LinkOption.NOFOLLOW_LINKS)) return emptyMap<String, StoredModState>() to null
        return try {
            if (Files.isSymbolicLink(statePath) || !Files.isRegularFile(statePath, LinkOption.NOFOLLOW_LINKS)) {
                return emptyMap<String, StoredModState>() to "저장된 모드 설정 파일이 안전한 일반 파일이 아닙니다."
            }
            if (Files.size(statePath) > MAX_MOD_STATE_BYTES) {
                return emptyMap<String, StoredModState>() to "저장된 모드 설정 파일이 너무 커 기본값을 사용합니다."
            }
            val bytes = Files.newInputStream(
                statePath,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS,
            ).use { stream ->
                stream.readNBytes((MAX_MOD_STATE_BYTES + 1L).toInt())
            }
            if (bytes.size.toLong() > MAX_MOD_STATE_BYTES) {
                return emptyMap<String, StoredModState>() to "저장된 모드 설정 파일이 너무 커 기본값을 사용합니다."
            }
            val json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            parse(json) to null
        } catch (_: Exception) {
            emptyMap<String, StoredModState>() to "저장된 모드 설정을 읽지 못해 기본값을 사용합니다."
        }
    }

    fun update(
        transform: (Map<String, StoredModState>) -> Pair<Map<String, StoredModState>?, String?>,
    ): String? = withExclusiveLock {
        val (states, readError) = read()
        if (readError != null) return@withExclusiveLock readError
        val (updated, transformError) = transform(states)
        transformError ?: write(requireNotNull(updated))
    }

    fun write(states: Map<String, StoredModState>): String? {
        val validationError = validate(states)
        if (validationError != null) return validationError
        var temporaryPath: Path? = null
        return try {
            if (Files.exists(appDataDirectory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(appDataDirectory)) {
                return "모드 설정 폴더가 심볼릭 링크여서 저장할 수 없습니다."
            }
            Files.createDirectories(appDataDirectory)
            if (Files.exists(statePath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(statePath)) {
                return "모드 설정 파일이 심볼릭 링크여서 저장할 수 없습니다."
            }
            val bytes = serialize(states).toByteArray(StandardCharsets.UTF_8)
            if (bytes.size.toLong() > MAX_MOD_STATE_BYTES) {
                return "저장할 모드 설정 데이터가 너무 큽니다."
            }
            temporaryPath = Files.createTempFile(appDataDirectory, ".mods-state-", ".tmp")
            FileChannel.open(
                temporaryPath,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(
                temporaryPath,
                statePath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            temporaryPath = null
            forceDirectoryBestEffort(appDataDirectory)
            null
        } catch (_: AtomicMoveNotSupportedException) {
            "이 파일 시스템은 모드 설정의 안전한 원자적 저장을 지원하지 않습니다."
        } catch (_: SecurityException) {
            "모드 설정을 저장할 권한이 없습니다."
        } catch (_: Exception) {
            "모드 설정을 안전하게 저장하지 못했습니다."
        } finally {
            temporaryPath?.let { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (_: Exception) {
                    // The abandoned temp file is ignored and never treated as persisted state.
                }
            }
        }
    }

    private fun parse(json: String): Map<String, StoredModState> {
        val reader = JsonReader(StringReader(json)).apply { strictness = Strictness.STRICT }
        val rootElement = reader.use { strictReader ->
            val parsed = JsonParser.parseReader(strictReader)
            check(strictReader.peek() == JsonToken.END_DOCUMENT)
            parsed
        }
        check(rootElement.isJsonObject)
        val root = rootElement.asJsonObject
        check(root.keySet() == ROOT_FIELDS)
        check(root.requiredPrimitive("schemaVersion").let { it.isNumber && it.asString == STATE_SCHEMA_VERSION.toString() })
        val mods = root.requiredObject("mods")
        check(mods.size() <= MAX_STORED_MODS)
        return buildMap {
            mods.entrySet().sortedBy { entry -> entry.key }.forEach { (id, value) ->
                check(MOD_ID_PATTERN.matches(id))
                check(value.isJsonObject)
                val state = value.asJsonObject
                check(state.keySet() == MOD_STATE_FIELDS)
                val version = state.requiredString("version")
                check(version.isNotBlank() && version.length <= ActiveModConfiguration.MAX_VERSION_LENGTH)
                check(version.none(Char::isISOControl))
                val enabled = state.requiredBoolean("enabled")
                val settingsObject = state.requiredObject("settings")
                check(settingsObject.size() <= ActiveModConfiguration.MAX_SETTINGS)
                val settings = buildMap {
                    settingsObject.entrySet().forEach { (key, settingValue) ->
                        check(SETTING_KEY_PATTERN.matches(key))
                        check(settingValue.isJsonPrimitive && settingValue.asJsonPrimitive.isString)
                        val text = settingValue.asString
                        check(text.length <= ActiveModConfiguration.MAX_SETTING_VALUE_LENGTH)
                        put(key, text)
                    }
                }
                put(id, StoredModState(version = version, enabled = enabled, settings = settings))
            }
        }
    }

    private fun serialize(states: Map<String, StoredModState>): String {
        val mods = JsonObject()
        states.toSortedMap().forEach { (id, state) ->
            val settings = JsonObject()
            state.settings.toSortedMap().forEach { (key, value) -> settings.addProperty(key, value) }
            val stateObject = JsonObject().apply {
                addProperty("version", state.version)
                addProperty("enabled", state.enabled)
                add("settings", settings)
            }
            mods.add(id, stateObject)
        }
        val root = JsonObject().apply {
            addProperty("schemaVersion", STATE_SCHEMA_VERSION)
            add("mods", mods)
        }
        return gson.toJson(root)
    }

    private fun validate(states: Map<String, StoredModState>): String? {
        if (states.size > MAX_STORED_MODS) return "저장할 모드 상태 항목이 너무 많습니다."
        if (states.values.count(StoredModState::enabled) > MAX_ACTIVE_MODS) {
            return "활성 모드는 ${MAX_ACTIVE_MODS}개까지 저장할 수 있습니다."
        }
        states.forEach { (id, state) ->
            val validation = ActiveModConfiguration(
                id = id,
                version = state.version,
                settings = state.settings,
            ).validate()
            if (validation != null) return validation
        }
        return null
    }

    private fun forceDirectoryBestEffort(directory: Path) {
        try {
            FileChannel.open(directory, StandardOpenOption.READ).use { channel -> channel.force(true) }
        } catch (_: Exception) {
            // Directory fsync is not supported by every desktop file system.
        }
    }

    private fun <T> withExclusiveLock(block: () -> T): T {
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(lockPath)) {
            throw SecurityException("The mod-state lock file must not be a symbolic link.")
        }
        return FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { channel ->
            channel.withTimedExclusiveLock(block = block)
        }
    }

    private fun JsonObject.requiredObject(name: String): JsonObject {
        val value = get(name)
        check(value != null && value.isJsonObject)
        return value.asJsonObject
    }

    private fun JsonObject.requiredPrimitive(name: String) = get(name).also { value ->
        check(value != null && value.isJsonPrimitive)
    }.asJsonPrimitive

    private fun JsonObject.requiredString(name: String): String {
        val value = requiredPrimitive(name)
        check(value.isString)
        return value.asString
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val value = requiredPrimitive(name)
        check(value.isBoolean)
        return value.asBoolean
    }

    private companion object {
        const val STATE_SCHEMA_VERSION: Int = 1
        const val MAX_STORED_MODS: Int = 512

        val ROOT_FIELDS: Set<String> = setOf("schemaVersion", "mods")
        val MOD_STATE_FIELDS: Set<String> = setOf("version", "enabled", "settings")
    }
}
