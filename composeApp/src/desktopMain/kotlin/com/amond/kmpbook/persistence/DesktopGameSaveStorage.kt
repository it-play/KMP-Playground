package com.amond.kmpbook.persistence

import com.amond.kmpbook.domain.model.CorporateActionKind
import com.amond.kmpbook.domain.model.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.CorporateActionSource
import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalSignalDirection
import com.amond.kmpbook.domain.model.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventImpactHorizon
import com.amond.kmpbook.domain.model.EventImpactTargetKind
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltKind
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.IndustrySegment
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketActionKind
import com.amond.kmpbook.domain.model.MarketActionTransition
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.ScheduledEventKind
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingHaltStatus
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

private const val MAX_GAME_SAVE_FILE_BYTES: Long = 64L * 1024L * 1024L

private fun defaultGameSavePath(): Path {
    val osName = System.getProperty("os.name").orEmpty()
    val userHome = requireNotNull(System.getProperty("user.home")) {
        "The JVM user.home property is unavailable."
    }
    val appData = System.getenv("APPDATA")
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

actual class GameSaveStorage actual constructor() {
    private val targetPath: Path = defaultGameSavePath()
    private val gson: Gson = createSaveGson()

    actual val savePath: String = targetPath.toString()

    actual suspend fun save(state: SimulatorUiState): GameSaveResult = withContext(Dispatchers.IO) {
        val validationError = validateSimulatorUiState(state)
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
        if (bytes.size.toLong() > MAX_GAME_SAVE_FILE_BYTES) {
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
            if (declaredSize > MAX_GAME_SAVE_FILE_BYTES) {
                return@withContext GameLoadResult.Failure(savePath, tooLargeError(declaredSize))
            }
            val bytes = readBounded(targetPath)
            if (bytes.isEmpty()) {
                return@withContext corrupted("저장 파일이 비어 있습니다.")
            }
            val json = decodeUtf8Strict(bytes)
            val envelope = parseEnvelope(json)
            val validationError = validateSimulatorUiState(envelope.state)
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
            if (size > MAX_GAME_SAVE_FILE_BYTES) {
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
        val maximumRead = (MAX_GAME_SAVE_FILE_BYTES + 1L).toInt()
        val bytes = Files.newInputStream(path, StandardOpenOption.READ).use { input ->
            input.readNBytes(maximumRead)
        }
        if (bytes.size.toLong() > MAX_GAME_SAVE_FILE_BYTES) throw SaveFileTooLargeException(bytes.size.toLong())
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
        val stateJson = objectValue.requiredObject("state")
        CURRENT_STATE_FIELDS.forEach(stateJson::requireMember)
        val unexpectedStateFields = stateJson.keySet() - CURRENT_STATE_FIELDS
        if (unexpectedStateFields.isNotEmpty()) {
            throw JsonParseException("현재 스키마에 없는 상태 필드입니다: ${unexpectedStateFields.sorted()}")
        }
        CURRENT_STATE_FIELDS
            .filterNot(CURRENT_NULLABLE_STATE_FIELDS::contains)
            .forEach(stateJson::required)
        validateCurrentStateJson(stateJson)
        val state = gson.fromJson(stateJson, SimulatorUiState::class.java)
            ?: throw JsonParseException("state를 복원할 수 없습니다.")
        return GameSaveEnvelope(
            format = format,
            schemaVersion = schemaVersion,
            savedAt = savedAt,
            state = state,
        )
    }

    private fun validateCurrentStateJson(state: JsonObject) {
        state.requiredArray("stocks").forEachIndexed { index, element ->
            element.requireObject("state.stocks[$index]")
                .requiredEnumArray<IndustrySegment>("industrySegments", "state.stocks[$index].industrySegments")
        }
        state.requiredObject("macro").apply {
            requiredObject("fxRatesToKrw")
            requiredObject("previousFxRatesToKrw")
        }
        state.requiredArray("portfolioSnapshots").forEachIndexed { index, element ->
            element.requireObject("state.portfolioSnapshots[$index]")
                .requiredObject("holdingCostBasisKrw")
        }
        state.requiredArray("trades").forEachIndexed { index, element ->
            element.requireObject("state.trades[$index]").apply {
                required("settlementKind")
                required("accountingSequence")
            }
        }
        state.requiredArray("dividendLedger").forEachIndexed { index, element ->
            element.requireObject("state.dividendLedger[$index]").apply {
                required("taxableIncomeAmount")
                required("returnOfCapitalAmount")
                required("excessReturnOfCapitalGainKrw")
                required("accountingSequence")
            }
        }
        state.requiredArray("pendingCorporateActions").forEachIndexed { index, element ->
            element.requireObject("state.pendingCorporateActions[$index]").apply {
                required("id")
                required("stockId")
                requiredEnum<CorporateActionKind>("kind", "state.pendingCorporateActions[$index].kind")
                required("announcedAt")
                required("effectiveNotBefore")
                required("quantityMultiplier")
                requiredEnum<CorporateActionSource>("source", "state.pendingCorporateActions[$index].source")
                required("rationale")
            }
        }
        state.requiredArray("corporateActionLedger").forEachIndexed { index, element ->
            element.requireObject("state.corporateActionLedger[$index]").apply {
                required("id")
                required("stockId")
                requiredEnum<CorporateActionKind>("kind", "state.corporateActionLedger[$index].kind")
                required("announcedAt")
                required("effectiveNotBefore")
                required("effectiveAt")
                required("quantityMultiplier")
                required("preActionPrice")
                required("postActionPrice")
                requiredEnum<CorporateActionSource>("source", "state.corporateActionLedger[$index].source")
                required("rationale")
                required("accountingSequence")
            }
        }

        fun requireEventFields(array: com.google.gson.JsonArray, path: String) {
            array.forEachIndexed { index, element ->
                element.requireObject("$path[$index]").apply {
                    required("id")
                    requireMember("generatorTemplateId")
                    required("title")
                    required("description")
                    requiredEnum<EventScope>("scope", "$path[$index].scope")
                    requiredEnum<EventType>("type", "$path[$index].type")
                    requiredEnum<EventSeverity>("severity", "$path[$index].severity")
                    requiredObject("impact").apply {
                        requiredEnum<ImpactDirection>("direction", "$path[$index].impact.direction")
                        required("shockReturn")
                        required("hourlyDrift")
                        required("volatilityMultiplier")
                        required("volumeMultiplier")
                        required("liquidityMultiplier")
                        required("sentiment")
                    }
                    required("startsAt")
                    required("durationHours")
                    requiredEnum<EventRecordKind>("recordKind", "$path[$index].recordKind")
                    requireMember("scheduledEventReference")
                    get("scheduledEventReference").takeUnless(JsonElement::isJsonNull)?.let { referenceElement ->
                        referenceElement.requireObject("$path[$index].scheduledEventReference").apply {
                            required("occurrenceId")
                            requiredEnum<ScheduledEventKind>(
                                "kind",
                                "$path[$index].scheduledEventReference.kind",
                            )
                        }
                    }
                    requireMember("corporateActionReference")
                    get("corporateActionReference").takeUnless(JsonElement::isJsonNull)?.let { referenceElement ->
                        referenceElement.requireObject("$path[$index].corporateActionReference").apply {
                            required("occurrenceId")
                            requiredEnum<CorporateActionNewsTransition>(
                                "transition",
                                "$path[$index].corporateActionReference.transition",
                            )
                            required("stockId")
                            requiredEnum<CorporateActionKind>(
                                "kind",
                                "$path[$index].corporateActionReference.kind",
                            )
                            required("announcedAt")
                            required("effectiveNotBefore")
                            required("quantityMultiplier")
                            requiredEnum<CorporateActionSource>(
                                "source",
                                "$path[$index].corporateActionReference.source",
                            )
                            required("rationale")
                            requireMember("appliedAt")
                            requireMember("accountingSequence")
                            requireMember("cancelledAt")
                            requireMember("cancellingListingEventId")
                            requireMember("cancellingListingLedgerSequence")
                            nullableEnum<ListingLifecycleStatus>(
                                "cancellingListingStatus",
                                "$path[$index].corporateActionReference.cancellingListingStatus",
                            )
                        }
                    }
                    requiredEnum<EventImpactCoveragePolicy>(
                        "impactCoveragePolicy",
                        "$path[$index].impactCoveragePolicy",
                    )
                    required("effectStartsAt")
                    required("effectDurationHours")
                    requiredEnumArray<Market>("affectedMarkets", "$path[$index].affectedMarkets")
                    requiredEnumArray<Sector>("affectedSectors", "$path[$index].affectedSectors")
                    requiredArray("affectedStockIds")
                    required("sourceLabel")
                    requiredObject("marketRegimeSnapshot").apply {
                        val regimePath = "$path[$index].marketRegimeSnapshot"
                        requireExactFields(
                            expected = CAUSAL_MARKET_REGIME_SNAPSHOT_FIELDS,
                            path = regimePath,
                        )
                        val riskSentiment = requiredFiniteDouble(
                            "riskSentiment",
                            "$regimePath.riskSentiment",
                        )
                        if (riskSentiment !in -1.0..1.0) {
                            throw JsonParseException("필드 '$regimePath.riskSentiment'은 -1과 1 사이여야 합니다.")
                        }
                        val volatilityRegime = requiredFiniteDouble(
                            "volatilityRegime",
                            "$regimePath.volatilityRegime",
                        )
                        if (volatilityRegime !in 0.1..10.0) {
                            throw JsonParseException("필드 '$regimePath.volatilityRegime'은 0.1과 10 사이여야 합니다.")
                        }
                        val usdKrwChangeRate = requiredFiniteDouble(
                            "usdKrwChangeRate",
                            "$regimePath.usdKrwChangeRate",
                        )
                        if (usdKrwChangeRate !in -0.25..0.25) {
                            throw JsonParseException(
                                "필드 '$regimePath.usdKrwChangeRate'은 -0.25와 0.25 사이여야 합니다.",
                            )
                        }
                        requiredEnumFiniteDoubleMap<Market>(
                            "marketHourlyReturns",
                            "$regimePath.marketHourlyReturns",
                        )
                        requiredEnumFiniteDoubleMap<Market>(
                            "marketChangeFromPreviousClose",
                            "$regimePath.marketChangeFromPreviousClose",
                        )
                    }
                    requiredArray("causalSignals").forEachIndexed { signalIndex, signalElement ->
                        val signalPath = "$path[$index].causalSignals[$signalIndex]"
                        signalElement.requireObject(signalPath).apply {
                            requireExactFields(
                                expected = CAUSAL_SIGNAL_FIELDS,
                                path = signalPath,
                            )
                            requiredEnum<CausalEconomicFactor>("factor", "$signalPath.factor")
                            requiredEnum<CausalSignalDirection>("direction", "$signalPath.direction")
                            val strength = requiredFiniteDouble("strength", "$signalPath.strength")
                            if (strength !in MIN_CAUSAL_SIGNAL_STRENGTH..1.0) {
                                throw JsonParseException(
                                    "필드 '$signalPath.strength'은 $MIN_CAUSAL_SIGNAL_STRENGTH 이상 1 이하여야 합니다.",
                                )
                            }
                            val confidence = requiredFiniteDouble("confidence", "$signalPath.confidence")
                            if (confidence !in 0.0..1.0 || confidence == 0.0) {
                                throw JsonParseException("필드 '$signalPath.confidence'는 0보다 크고 1 이하여야 합니다.")
                            }
                            requiredEnum<CausalTransmissionProfile>(
                                "transmissionProfile",
                                "$signalPath.transmissionProfile",
                            )
                        }
                    }
                    required("listingRiskTags")
                    required("listingRecoveryConditions")
                    requireMember("listingFinalDispositionHint")
                    requiredArray("impactInsights").forEachIndexed { insightIndex, insightElement ->
                        insightElement.requireObject("$path[$index].impactInsights[$insightIndex]").apply {
                            requiredEnum<EventImpactTargetKind>(
                                "targetKind",
                                "$path[$index].impactInsights[$insightIndex].targetKind",
                            )
                            required("targetLabel")
                            requiredEnum<ImpactDirection>(
                                "direction",
                                "$path[$index].impactInsights[$insightIndex].direction",
                            )
                            required("rationale")
                            nullableEnum<Sector>("sector", "$path[$index].impactInsights[$insightIndex].sector")
                            nullableEnum<IndustrySegment>(
                                "industrySegment",
                                "$path[$index].impactInsights[$insightIndex].industrySegment",
                            )
                            requiredEnumArray<Market>(
                                "markets",
                                "$path[$index].impactInsights[$insightIndex].markets",
                            )
                            requireMember("stockId")
                            requiredEnum<EventImpactHorizon>(
                                "horizon",
                                "$path[$index].impactInsights[$insightIndex].horizon",
                            )
                            required("relativeSensitivity")
                        }
                    }
                    requiredArray("reportedFacts").forEachIndexed { factIndex, factElement ->
                        factElement.requireObject("$path[$index].reportedFacts[$factIndex]").apply {
                            required("label")
                            required("actual")
                            requireMember("comparison")
                        }
                    }
                    requireMember("marketAction")
                    get("marketAction").takeUnless(JsonElement::isJsonNull)?.let { actionElement ->
                        actionElement.requireObject("$path[$index].marketAction").apply {
                            requiredEnum<MarketActionKind>("kind", "$path[$index].marketAction.kind")
                            required("occurrenceId")
                            requiredEnum<MarketActionTransition>(
                                "transition",
                                "$path[$index].marketAction.transition",
                            )
                            required("announcedAt")
                            required("effectiveAt")
                            requireMember("endsAt")
                            requireMember("stockId")
                            requiredEnumArray<Market>("markets", "$path[$index].marketAction.markets")
                            requireMember("stage")
                            requireMember("triggerSequence")
                            nullableEnum<InvestmentAlertLevel>(
                                "alertLevel",
                                "$path[$index].marketAction.alertLevel",
                            )
                            requireMember("effectiveOn")
                            requireMember("listingLedgerSequence")
                            nullableEnum<ListingLifecycleStatus>(
                                "listingStatus",
                                "$path[$index].marketAction.listingStatus",
                            )
                        }
                    }
                    requireMember("instrumentTermination")
                    get("instrumentTermination").takeUnless(JsonElement::isJsonNull)?.let { termsElement ->
                        termsElement.requireObject("$path[$index].instrumentTermination").apply {
                            requiredEnum<InstrumentTerminationKind>(
                                "kind",
                                "$path[$index].instrumentTermination.kind",
                            )
                            requireMember("contractualDate")
                            requireMember("effectiveNotBefore")
                            requiredEnum<InstrumentTerminationValuationMethod>(
                                "valuationMethod",
                                "$path[$index].instrumentTermination.valuationMethod",
                            )
                            requireMember("accelerationRecoveryRate")
                        }
                    }
                    requireMember("tradingHaltDirective")
                    get("tradingHaltDirective").takeUnless(JsonElement::isJsonNull)?.let { directiveElement ->
                        directiveElement.requireObject("$path[$index].tradingHaltDirective").apply {
                            requiredEnum<EventTradingHaltKind>(
                                "kind",
                                "$path[$index].tradingHaltDirective.kind",
                            )
                            requiredEnum<TradingHaltReason>(
                                "reason",
                                "$path[$index].tradingHaltDirective.reason",
                            )
                            requiredEnumArray<Market>(
                                "eligibleMarkets",
                                "$path[$index].tradingHaltDirective.eligibleMarkets",
                            )
                            required("durationMinutes")
                            required("detail")
                        }
                    }
                }
            }
        }
        requireEventFields(state.requiredArray("activeEvents"), "state.activeEvents")
        requireEventFields(state.requiredArray("newsEvents"), "state.newsEvents")
        state.requiredObject("eventEngineSnapshot").apply {
            required("randomState")
            required("sequence")
            requiredObject("lastTriggeredEpochSeconds")
            requireEventFields(
                requiredArray("activeEvents"),
                "state.eventEngineSnapshot.activeEvents",
            )
        }

        state.requiredObject("listingLifecycleStates").entrySet().forEach { (stockId, element) ->
            element.requireObject("state.listingLifecycleStates[$stockId]").apply {
                requireMember("controllingTerminationOccurrenceId")
                requireMember("controllingTerminationNoticePriority")
                requireMember("controllingTerminationRawEffectiveOn")
            }
        }
        state.requiredArray("listingLifecycleLedger").forEachIndexed { index, element ->
            element.requireObject("state.listingLifecycleLedger[$index]").apply {
                requireMember("controllingTerminationOccurrenceId")
                requireMember("controllingTerminationNoticePriority")
                requireMember("controllingTerminationRawEffectiveOn")
            }
        }

        state.requiredObject("tradingProtectionSnapshot").apply {
            fun JsonElement.requireInstrumentHaltFields(path: String) {
                requireObject(path).apply {
                    required("occurrenceId")
                    required("stockId")
                    requiredEnum<TradingHaltReason>("reason", "$path.reason")
                    required("detail")
                    required("startedAt")
                    requiredObject("policy").apply {
                        required("acceptsNewOrders")
                        required("allowsCancellation")
                        required("allowsExecution")
                        required("allowsContinuousTrading")
                    }
                    requireMember("scheduledReleaseAt")
                    requiredEnum<TradingHaltStatus>("status", "$path.status")
                    requireMember("releasedAt")
                    requireMember("releaseNote")
                }
            }
            requiredObject("instrumentTradingHalts").entrySet().forEach { (stockId, element) ->
                element.requireInstrumentHaltFields(
                    "state.tradingProtectionSnapshot.instrumentTradingHalts[$stockId]",
                )
            }
            requiredObject("scheduledInstrumentTradingHalts").entrySet().forEach { (scheduleId, element) ->
                element.requireInstrumentHaltFields(
                    "state.tradingProtectionSnapshot.scheduledInstrumentTradingHalts[$scheduleId]",
                )
            }
            requiredObject("investmentAlerts").entrySet().forEach { (stockId, element) ->
                element.requireObject("state.tradingProtectionSnapshot.investmentAlerts[$stockId]")
                    .required("releaseRule")
            }
        }
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
        message = "저장 파일이 허용 크기 ${MAX_GAME_SAVE_FILE_BYTES}바이트를 초과했습니다: ${actualSize}바이트.",
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
        "저장 스키마 ${version}은 지원하지 않습니다. " +
            "현재 스키마 ${CURRENT_GAME_SAVE_SCHEMA_VERSION}만 사용할 수 있습니다.",
    )

    private class SaveFileTooLargeException(val actualSize: Long) : IOException()

    private companion object {
        val CURRENT_STATE_FIELDS: Set<String> = setOf(
            "options",
            "phase",
            "screen",
            "currentTime",
            "turn",
            "selectedTurnStep",
            "stocks",
            "selectedStockId",
            "quotes",
            "priceHistory",
            "cashByCurrency",
            "holdings",
            "orders",
            "trades",
            "selectedOrderBook",
            "marketSessions",
            "macro",
            "activeEvents",
            "newsEvents",
            "readEventIds",
            "portfolioSnapshots",
            "dailyStatistics",
            "benchmarkHistory",
            "transactionCosts",
            "realizedGains",
            "fifoCostBasisBook",
            "dividendLedger",
            "foreignExchangeLedger",
            "annualTaxLedgers",
            "taxPaymentNotices",
            "peakAssetsKrw",
            "maximumDrawdown",
            "rngState",
            "eventEngineSnapshot",
            "nextSequence",
            "isAdvancing",
            "lastMessage",
            "pendingEtfReferenceReturns",
            "pendingClosedEventLogReturns",
            "marketIndices",
            "marketIndexHistory",
            "taxExchangeRatesByTradeId",
            "pendingTaxSettlementTradeIds",
            "watchlistedStockIds",
            "pendingCorporateActions",
            "corporateActionLedger",
            "listingLifecycleStates",
            "listingLifecycleLedger",
            "tradingProtectionSnapshot",
            "dailyTradingSurveillance",
        )

        val CURRENT_NULLABLE_STATE_FIELDS: Set<String> = setOf(
            "selectedStockId",
            "selectedOrderBook",
            "lastMessage",
        )

        val CAUSAL_SIGNAL_FIELDS: Set<String> = setOf(
            "factor",
            "direction",
            "strength",
            "confidence",
            "transmissionProfile",
        )

        val CAUSAL_MARKET_REGIME_SNAPSHOT_FIELDS: Set<String> = setOf(
            "riskSentiment",
            "volatilityRegime",
            "usdKrwChangeRate",
            "marketHourlyReturns",
            "marketChangeFromPreviousClose",
        )

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

private fun JsonObject.requireMember(name: String) {
    if (!has(name)) throw JsonParseException("필수 필드 '$name'이 없습니다.")
}

private fun JsonObject.requireExactFields(expected: Set<String>, path: String) {
    expected.forEach(::requireMember)
    val unexpected = keySet() - expected
    if (unexpected.isNotEmpty()) {
        throw JsonParseException("필드 '$path'에 현재 스키마에 없는 값이 있습니다: ${unexpected.sorted()}")
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject = required(name)
    .takeIf(JsonElement::isJsonObject)
    ?.asJsonObject
    ?: throw JsonParseException("필드 '$name'은 객체여야 합니다.")

private fun JsonObject.requiredArray(name: String): com.google.gson.JsonArray = required(name)
    .takeIf(JsonElement::isJsonArray)
    ?.asJsonArray
    ?: throw JsonParseException("필드 '$name'은 배열이어야 합니다.")

private fun JsonElement.requireObject(path: String): JsonObject = takeIf(JsonElement::isJsonObject)
    ?.asJsonObject
    ?: throw JsonParseException("필드 '$path'은 객체여야 합니다.")

private fun JsonObject.requiredString(name: String): String = try {
    required(name).asString
} catch (error: RuntimeException) {
    throw JsonParseException("필드 '$name'은 문자열이어야 합니다.", error)
}

private inline fun <reified E : Enum<E>> JsonObject.requiredEnum(name: String, path: String): E {
    val element = required(name)
    val primitive = element.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
    if (primitive?.isString != true) {
        throw JsonParseException("필드 '$path'은 문자열 enum이어야 합니다.")
    }
    val value = primitive.asString
    return enumValues<E>().firstOrNull { it.name == value }
        ?: throw JsonParseException("필드 '$path'의 enum 값 '$value'이 유효하지 않습니다.")
}

private inline fun <reified E : Enum<E>> JsonObject.nullableEnum(name: String, path: String): E? {
    requireMember(name)
    val element = get(name)
    if (element.isJsonNull) return null
    val primitive = element.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
    if (primitive?.isString != true) {
        throw JsonParseException("필드 '$path'은 null 또는 문자열 enum이어야 합니다.")
    }
    val value = primitive.asString
    return enumValues<E>().firstOrNull { it.name == value }
        ?: throw JsonParseException("필드 '$path'의 enum 값 '$value'이 유효하지 않습니다.")
}

private inline fun <reified E : Enum<E>> JsonObject.requiredEnumArray(
    name: String,
    path: String,
): List<E> = requiredArray(name).mapIndexed { index, element ->
    val primitive = element.takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
    if (primitive?.isString != true) {
        throw JsonParseException("필드 '$path[$index]'은 문자열 enum이어야 합니다.")
    }
    val value = primitive.asString
    enumValues<E>().firstOrNull { it.name == value }
        ?: throw JsonParseException("필드 '$path[$index]'의 enum 값 '$value'이 유효하지 않습니다.")
}

private inline fun <reified E : Enum<E>> JsonObject.requiredEnumFiniteDoubleMap(
    name: String,
    path: String,
): Map<E, Double> {
    val validKeys = enumValues<E>().associateBy { it.name }
    val values = requiredObject(name)
    return values.entrySet().associate { (key, _) ->
        val enumKey = validKeys[key]
            ?: throw JsonParseException("필드 '$path'에 유효하지 않은 enum 키 '$key'가 있습니다.")
        enumKey to values.requiredFiniteDouble(key, "$path.$key")
    }
}

private fun JsonObject.requiredInt(name: String): Int = try {
    val primitive = required(name).takeIf { it.isJsonPrimitive }?.asJsonPrimitive
        ?: throw JsonParseException("필드 '$name'은 정수여야 합니다.")
    if (!primitive.isNumber) throw JsonParseException("필드 '$name'은 정수여야 합니다.")
    primitive.asString.toIntOrNull()
        ?: throw JsonParseException("필드 '$name'은 정수 범위의 10진 정수여야 합니다.")
} catch (error: RuntimeException) {
    if (error is JsonParseException) throw error
    throw JsonParseException("필드 '$name'은 정수여야 합니다.", error)
}

private fun JsonObject.requiredFiniteDouble(name: String, path: String): Double = try {
    val primitive = required(name).takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        ?: throw JsonParseException("필드 '$path'은 숫자여야 합니다.")
    if (!primitive.isNumber) throw JsonParseException("필드 '$path'은 숫자여야 합니다.")
    primitive.asDouble.takeIf(Double::isFinite)
        ?: throw JsonParseException("필드 '$path'은 유한한 숫자여야 합니다.")
} catch (error: RuntimeException) {
    if (error is JsonParseException) throw error
    throw JsonParseException("필드 '$path'은 유한한 숫자여야 합니다.", error)
}
