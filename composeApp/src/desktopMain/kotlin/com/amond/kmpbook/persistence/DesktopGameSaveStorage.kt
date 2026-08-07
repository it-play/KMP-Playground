package com.amond.kmpbook.persistence

import com.amond.kmpbook.presentation.SimulatorUiState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.channels.FileChannel
import kotlin.time.Instant

internal fun defaultGameSavePath(
    osName: String,
    userHome: String,
    appData: String?,
): Path {
    val saveDirectory = if (osName.contains("Windows", ignoreCase = true)) {
        val roamingAppData = appData
            ?.takeIf(String::isNotBlank)
            ?.let(Paths::get)
            ?: Paths.get(userHome, "AppData", "Roaming")
        roamingAppData.resolve("MarketLedger2040")
    } else {
        Paths.get(userHome, ".market-ledger-2040")
    }
    return saveDirectory.resolve("savegame.json").toAbsolutePath().normalize()
}

actual class GameSaveStorage actual constructor(
    savePathOverride: String?,
    private val maxFileSizeBytes: Long,
) {
    private val targetPath: Path = resolveSavePath(savePathOverride)
    private val gson: Gson = createSaveGson()

    init {
        require(maxFileSizeBytes in 1 until Int.MAX_VALUE.toLong()) {
            "maxFileSizeBytes must be between 1 and ${Int.MAX_VALUE - 1}."
        }
    }

    actual val savePath: String = targetPath.toString()

    actual suspend fun save(state: SimulatorUiState): GameSaveResult = withContext(Dispatchers.IO) {
        val validationError = validateState(state)
        if (validationError != null) {
            return@withContext GameSaveResult.Failure(
                path = savePath,
                error = GameSaveError(GameSaveErrorCode.INVALID_STATE, validationError),
            )
        }

        val savedAt = now()
        val envelope = GameSaveEnvelope(
            format = GAME_SAVE_FORMAT_ID,
            schemaVersion = CURRENT_GAME_SAVE_SCHEMA_VERSION,
            savedAt = savedAt,
            state = state,
        )
        val bytes = try {
            gson.toJson(envelope).toByteArray(StandardCharsets.UTF_8)
        } catch (error: RuntimeException) {
            return@withContext GameSaveResult.Failure(
                path = savePath,
                error = GameSaveError(
                    code = GameSaveErrorCode.SERIALIZATION_FAILED,
                    message = "게임 상태를 JSON으로 변환하지 못했습니다: ${safeMessage(error)}",
                    causeType = error::class.qualifiedName,
                ),
            )
        }
        if (bytes.size.toLong() > maxFileSizeBytes) {
            return@withContext GameSaveResult.Failure(
                path = savePath,
                error = tooLargeError(bytes.size.toLong()),
            )
        }

        var temporaryPath: Path? = null
        try {
            val parent = requireNotNull(targetPath.parent) { "The save path has no parent directory." }
            Files.createDirectories(parent)
            temporaryPath = Files.createTempFile(parent, ".savegame-", ".tmp")
            Files.newOutputStream(
                temporaryPath,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            FileChannel.open(temporaryPath, StandardOpenOption.WRITE).use { channel -> channel.force(true) }

            val usedAtomicMove = moveIntoPlace(temporaryPath, targetPath)
            temporaryPath = null
            GameSaveResult.Success(
                path = savePath,
                metadata = envelope.metadata(),
                bytesWritten = bytes.size.toLong(),
                usedAtomicMove = usedAtomicMove,
            )
        } catch (error: SecurityException) {
            GameSaveResult.Failure(
                path = savePath,
                error = accessError(error),
            )
        } catch (error: IOException) {
            GameSaveResult.Failure(
                path = savePath,
                error = ioError("저장 파일을 안전하게 쓰지 못했습니다", error),
            )
        } catch (error: RuntimeException) {
            GameSaveResult.Failure(
                path = savePath,
                error = ioError("저장 파일 처리 중 오류가 발생했습니다", error),
            )
        } finally {
            temporaryPath?.let { path -> runCatching { Files.deleteIfExists(path) } }
        }
    }

    actual suspend fun load(): GameLoadResult = withContext(Dispatchers.IO) {
        try {
            if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameLoadResult.NotFound(savePath)
            }
            if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameLoadResult.Failure(
                    path = savePath,
                    error = GameSaveError(
                        GameSaveErrorCode.IO_ERROR,
                        "저장 경로가 일반 파일이 아닙니다.",
                    ),
                )
            }
            val declaredSize = Files.size(targetPath)
            if (declaredSize > maxFileSizeBytes) {
                return@withContext GameLoadResult.Failure(savePath, tooLargeError(declaredSize))
            }
            val bytes = readBounded(targetPath)
            if (bytes.isEmpty()) {
                return@withContext corrupted("저장 파일이 비어 있습니다.")
            }
            val json = decodeUtf8Strict(bytes)
            val envelope = parseEnvelope(json)
            val validationError = validateState(envelope.state)
            if (validationError != null) {
                return@withContext GameLoadResult.Failure(
                    path = savePath,
                    error = GameSaveError(GameSaveErrorCode.INVALID_STATE, validationError),
                )
            }
            GameLoadResult.Success(
                path = savePath,
                state = envelope.state,
                metadata = envelope.metadata(),
                bytesRead = bytes.size.toLong(),
            )
        } catch (error: UnsupportedSchemaException) {
            GameLoadResult.Failure(
                path = savePath,
                error = GameSaveError(
                    code = GameSaveErrorCode.UNSUPPORTED_SCHEMA,
                    message = error.message ?: "지원하지 않는 저장 스키마입니다.",
                    causeType = error::class.qualifiedName,
                ),
            )
        } catch (error: CharacterCodingException) {
            corrupted("저장 파일이 올바른 UTF-8이 아닙니다.", error)
        } catch (error: JsonParseException) {
            corrupted("저장 JSON이 손상되었습니다: ${safeMessage(error)}", error)
        } catch (error: IllegalStateException) {
            corrupted("저장 파일 구조가 올바르지 않습니다: ${safeMessage(error)}", error)
        } catch (error: SecurityException) {
            GameLoadResult.Failure(savePath, accessError(error))
        } catch (error: IOException) {
            GameLoadResult.Failure(savePath, ioError("저장 파일을 읽지 못했습니다", error))
        } catch (error: RuntimeException) {
            corrupted("저장 상태를 복원하지 못했습니다: ${safeMessage(error)}", error)
        }
    }

    actual suspend fun exists(): GameSavePresenceResult = withContext(Dispatchers.IO) {
        try {
            if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSavePresenceResult.Missing(savePath)
            }
            if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSavePresenceResult.Failure(
                    path = savePath,
                    error = GameSaveError(
                        GameSaveErrorCode.IO_ERROR,
                        "저장 경로가 일반 파일이 아닙니다.",
                    ),
                )
            }
            val size = Files.size(targetPath)
            if (size > maxFileSizeBytes) {
                return@withContext GameSavePresenceResult.Failure(savePath, tooLargeError(size))
            }
            GameSavePresenceResult.Present(
                path = savePath,
                sizeBytes = size,
                lastModifiedAt = Instant.fromEpochMilliseconds(Files.getLastModifiedTime(targetPath).toMillis()),
            )
        } catch (error: SecurityException) {
            GameSavePresenceResult.Failure(savePath, accessError(error))
        } catch (error: IOException) {
            GameSavePresenceResult.Failure(savePath, ioError("저장 파일 정보를 읽지 못했습니다", error))
        }
    }

    actual suspend fun delete(): GameSaveDeleteResult = withContext(Dispatchers.IO) {
        try {
            if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSaveDeleteResult.NotFound(savePath)
            }
            if (Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSaveDeleteResult.Failure(
                    path = savePath,
                    error = GameSaveError(
                        GameSaveErrorCode.IO_ERROR,
                        "안전을 위해 저장 경로가 디렉터리이면 삭제하지 않습니다.",
                    ),
                )
            }
            if (Files.deleteIfExists(targetPath)) {
                GameSaveDeleteResult.Deleted(savePath)
            } else {
                GameSaveDeleteResult.NotFound(savePath)
            }
        } catch (error: SecurityException) {
            GameSaveDeleteResult.Failure(savePath, accessError(error))
        } catch (error: IOException) {
            GameSaveDeleteResult.Failure(savePath, ioError("저장 파일을 삭제하지 못했습니다", error))
        }
    }

    private fun readBounded(path: Path): ByteArray {
        val maximumRead = (maxFileSizeBytes + 1L).toInt()
        val bytes = Files.newInputStream(path, StandardOpenOption.READ).use { input ->
            input.readNBytes(maximumRead)
        }
        if (bytes.size.toLong() > maxFileSizeBytes) throw SaveFileTooLargeException(bytes.size.toLong())
        return bytes
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String = StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private fun parseEnvelope(json: String): GameSaveEnvelope {
        val reader = JsonReader(StringReader(json)).apply { strictness = Strictness.STRICT }
        val root = reader.use { strictReader ->
            val parsed = JsonParser.parseReader(strictReader)
            if (strictReader.peek() != JsonToken.END_DOCUMENT) {
                throw JsonParseException("JSON 뒤에 추가 데이터가 있습니다.")
            }
            parsed
        }
        if (!root.isJsonObject) throw JsonParseException("저장 파일 루트가 JSON 객체가 아닙니다.")
        val objectValue = root.asJsonObject
        val format = objectValue.requiredString("format")
        if (format != GAME_SAVE_FORMAT_ID) {
            throw JsonParseException("알 수 없는 저장 포맷 '$format'입니다.")
        }
        val schemaVersion = objectValue.requiredInt("schemaVersion")
        if (schemaVersion != CURRENT_GAME_SAVE_SCHEMA_VERSION) {
            throw UnsupportedSchemaException(schemaVersion)
        }
        val savedAt = gson.fromJson(objectValue.required("savedAt"), Instant::class.java)
            ?: throw JsonParseException("savedAt을 복원할 수 없습니다.")
        val state = gson.fromJson(objectValue.required("state"), SimulatorUiState::class.java)
            ?: throw JsonParseException("state를 복원할 수 없습니다.")
        return GameSaveEnvelope(
            format = format,
            schemaVersion = schemaVersion,
            savedAt = savedAt,
            state = state,
        )
    }

    private fun validateState(state: SimulatorUiState): String? {
        if (state.turn < 0L) return "턴 번호가 음수입니다."
        if (state.nextSequence < 0L) return "다음 원장 시퀀스가 음수입니다."
        if (state.eventEngineSnapshot.sequence < 0L) return "이벤트 엔진 시퀀스가 음수입니다."
        if (state.stocks.map { it.id }.distinct().size != state.stocks.size) return "종목 ID가 중복되었습니다."
        if (state.selectedStockId != null && state.stocks.none { it.id == state.selectedStockId }) {
            return "선택 종목이 종목 목록에 없습니다."
        }
        if (state.cashByCurrency.values.any { !it.isFinite() || it < 0.0 }) return "현금 잔액이 유효하지 않습니다."
        if (state.holdings.any { (id, holding) -> id != holding.stockId }) return "보유 종목 맵 키가 일치하지 않습니다."
        if (state.quotes.any { (id, quote) -> id != quote.stockId }) return "시세 맵 키가 일치하지 않습니다."
        if (state.priceHistory.any { (id, bars) -> bars.any { it.stockId != id } }) {
            return "가격 히스토리 종목 키가 일치하지 않습니다."
        }
        if (state.annualTaxLedgers.any { (year, ledger) -> year != ledger.taxYear }) {
            return "연간 세금 원장의 연도 키가 일치하지 않습니다."
        }
        val tradeIds = state.trades.map { it.id }.toSet()
        if (state.taxExchangeRatesByTradeId.orEmpty().any { (tradeId, rate) ->
                tradeId !in tradeIds || !rate.isFinite() || rate <= 0.0
            }
        ) {
            return "체결별 세무 환율 원장이 유효하지 않습니다."
        }
        if (state.pendingTaxSettlementTradeIds.orEmpty().any { it !in tradeIds }) {
            return "미결제 세무 환율 원장에 알 수 없는 체결이 있습니다."
        }
        if (state.activeEvents.map { it.id }.distinct().size != state.activeEvents.size) {
            return "활성 이벤트 ID가 중복되었습니다."
        }
        if (state.newsEvents.map { it.id }.distinct().size != state.newsEvents.size) {
            return "뉴스 이벤트 ID가 중복되었습니다."
        }
        return null
    }

    private fun corrupted(message: String, cause: Throwable? = null): GameLoadResult.Failure =
        GameLoadResult.Failure(
            path = savePath,
            error = GameSaveError(
                code = GameSaveErrorCode.CORRUPTED_FILE,
                message = message,
                causeType = cause?.let { it::class.qualifiedName },
            ),
        )

    private fun tooLargeError(actualSize: Long): GameSaveError = GameSaveError(
        code = GameSaveErrorCode.FILE_TOO_LARGE,
        message = "저장 파일이 허용 크기 ${maxFileSizeBytes}바이트를 초과했습니다: ${actualSize}바이트.",
    )

    private fun accessError(error: SecurityException): GameSaveError = GameSaveError(
        code = GameSaveErrorCode.SECURITY_ERROR,
        message = "저장 경로에 접근할 권한이 없습니다: ${safeMessage(error)}",
        causeType = error::class.qualifiedName,
    )

    private fun ioError(prefix: String, error: Throwable): GameSaveError = GameSaveError(
        code = if (error is SaveFileTooLargeException) {
            GameSaveErrorCode.FILE_TOO_LARGE
        } else {
            GameSaveErrorCode.IO_ERROR
        },
        message = if (error is SaveFileTooLargeException) {
            tooLargeError(error.actualSize).message
        } else {
            "$prefix: ${safeMessage(error)}"
        },
        causeType = error::class.qualifiedName,
    )

    private fun moveIntoPlace(temporaryPath: Path, destination: Path): Boolean = try {
        Files.move(
            temporaryPath,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
        true
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporaryPath, destination, StandardCopyOption.REPLACE_EXISTING)
        false
    }

    private class UnsupportedSchemaException(version: Int) : IllegalStateException(
        "저장 스키마 ${version}은 현재 지원 버전 ${CURRENT_GAME_SAVE_SCHEMA_VERSION}과 다릅니다.",
    )

    private class SaveFileTooLargeException(val actualSize: Long) : IOException()

    private companion object {
        fun resolveSavePath(override: String?): Path {
            if (override != null) {
                require(override.isNotBlank()) { "savePathOverride cannot be blank." }
                return Paths.get(override).toAbsolutePath().normalize()
            }
            val userHome = requireNotNull(System.getProperty("user.home")) {
                "The JVM user.home property is unavailable."
            }
            return defaultGameSavePath(
                osName = System.getProperty("os.name").orEmpty(),
                userHome = userHome,
                appData = System.getenv("APPDATA"),
            )
        }

        fun createSaveGson(): Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantTypeAdapter().nullSafe())
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter().nullSafe())
            .enableComplexMapKeySerialization()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .setStrictness(Strictness.STRICT)
            .create()

        fun now(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        fun safeMessage(error: Throwable): String = error.message?.takeIf(String::isNotBlank)
            ?: error::class.simpleName
            ?: "알 수 없는 오류"
    }
}

private class InstantTypeAdapter : TypeAdapter<Instant>() {
    override fun write(writer: JsonWriter, value: Instant) {
        writer.value(value.toString())
    }

    override fun read(reader: JsonReader): Instant {
        if (reader.peek() != JsonToken.STRING) throw JsonParseException("Instant는 ISO-8601 문자열이어야 합니다.")
        val value = reader.nextString()
        return try {
            Instant.parse(value)
        } catch (error: IllegalArgumentException) {
            throw JsonParseException("올바르지 않은 Instant '$value'입니다.", error)
        }
    }
}

private class LocalDateTypeAdapter : TypeAdapter<LocalDate>() {
    override fun write(writer: JsonWriter, value: LocalDate) {
        writer.value(value.toString())
    }

    override fun read(reader: JsonReader): LocalDate {
        if (reader.peek() != JsonToken.STRING) throw JsonParseException("LocalDate는 ISO-8601 문자열이어야 합니다.")
        val value = reader.nextString()
        return try {
            LocalDate.parse(value)
        } catch (error: IllegalArgumentException) {
            throw JsonParseException("올바르지 않은 LocalDate '$value'입니다.", error)
        }
    }
}

private fun JsonObject.required(name: String): JsonElement = get(name)
    ?.takeUnless(JsonElement::isJsonNull)
    ?: throw JsonParseException("필수 필드 '$name'이 없습니다.")

private fun JsonObject.requiredString(name: String): String = try {
    required(name).asString
} catch (error: RuntimeException) {
    throw JsonParseException("필드 '$name'은 문자열이어야 합니다.", error)
}

private fun JsonObject.requiredInt(name: String): Int = try {
    required(name).asInt
} catch (error: RuntimeException) {
    throw JsonParseException("필드 '$name'은 정수여야 합니다.", error)
}
