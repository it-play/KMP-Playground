package com.amond.kmpbook.persistence.storage

import com.amond.kmpbook.domain.data.InstrumentCatalogReference
import com.amond.kmpbook.domain.data.InstrumentCatalogSourceReference
import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import com.amond.kmpbook.domain.model.causal.CausalSignalDirection
import com.amond.kmpbook.domain.model.causal.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.causal.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionKind
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionCancellationReason
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionNewsTransition
import com.amond.kmpbook.domain.model.corporateaction.CorporateActionSource
import com.amond.kmpbook.domain.model.event.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.event.EventImpactHorizon
import com.amond.kmpbook.domain.model.event.EventImpactTargetKind
import com.amond.kmpbook.domain.model.event.EventRecordKind
import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventTradingHaltKind
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.AlternativeRiskPremiaStrategyFamily
import com.amond.kmpbook.domain.model.fund.CompositeSleeveDirection
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathEntry
import com.amond.kmpbook.domain.model.fund.EquityMethodologyPathState
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import com.amond.kmpbook.domain.model.fund.EquityReferenceRegion
import com.amond.kmpbook.domain.model.fund.FundLegalStructure
import com.amond.kmpbook.domain.model.fund.FundManagementStyle
import com.amond.kmpbook.domain.model.fund.FundOperationProfile
import com.amond.kmpbook.domain.model.fund.FundOperationProvenance
import com.amond.kmpbook.domain.model.fund.FundOfFundsCategory
import com.amond.kmpbook.domain.model.fund.FundOfFundsUniverse
import com.amond.kmpbook.domain.model.fund.FundReferenceExposure
import com.amond.kmpbook.domain.model.fund.FundReplicationMode
import com.amond.kmpbook.domain.model.fund.FundReturnTransform
import com.amond.kmpbook.domain.model.fund.FundReturnVariant
import com.amond.kmpbook.domain.model.fund.ActiveReturnModelSupport
import com.amond.kmpbook.domain.model.fund.MethodologyEquitySector
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionConsiderationKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioCorporateActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioState
import com.amond.kmpbook.domain.model.fund.SyntheticSwapFunding
import com.amond.kmpbook.domain.model.fundproduct.DailyResetCalendar
import com.amond.kmpbook.domain.model.fundproduct.DailyResetLifecycle
import com.amond.kmpbook.domain.model.fundproduct.DailyResetModelParameterOrigin
import com.amond.kmpbook.domain.model.fundproduct.DailyResetReferenceKind
import com.amond.kmpbook.domain.model.fundproduct.DailyResetState
import com.amond.kmpbook.domain.model.fundproduct.DailyResetTermsProvenance
import com.amond.kmpbook.domain.model.fundproduct.DirectReferenceTerminationPolicy
import com.amond.kmpbook.domain.model.fundproduct.DirectReferenceTerminationRuleProvenance
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadLifecycle
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadState
import com.amond.kmpbook.domain.model.fundproduct.OptionPremiumModelParameterOrigin
import com.amond.kmpbook.domain.model.fundproduct.OptionRollCalendar
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyLifecycle
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyKind
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyState
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyTermsProvenance
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundDistributionPolicy
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundCapitalActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundFinancingActionKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.EtnCouponKind
import com.amond.kmpbook.domain.model.fundstructure.EtnCreditEvent
import com.amond.kmpbook.domain.model.fundstructure.EtnLedgerKind
import com.amond.kmpbook.domain.model.fundstructure.EtnLifecycle
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.fundstructure.EtnSettlementValuationMethod
import com.amond.kmpbook.domain.model.fundstructure.FundStructureModelParameterOrigin
import com.amond.kmpbook.domain.model.fundstructure.FundStructureTermsProvenance
import com.amond.kmpbook.domain.model.instrument.EtfExposureRegion
import com.amond.kmpbook.domain.model.instrument.DistributionAmountBasis
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.MAX_FUND_REFERENCE_VALUE
import com.amond.kmpbook.domain.model.instrument.MIN_FUND_REFERENCE_VALUE
import com.amond.kmpbook.domain.model.listing.alert.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.listing.lifecycle.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.liability.StockGainTaxTreatment
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.reference.CreditQuality
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaState
import com.amond.kmpbook.domain.model.reference.AlternativeRiskPremiaActionKind
import com.amond.kmpbook.domain.model.reference.CommodityAssetClass
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState
import com.amond.kmpbook.domain.model.reference.CompositeReferenceActionKind
import com.amond.kmpbook.domain.model.reference.CompositeReferenceState
import com.amond.kmpbook.domain.model.reference.FixedIncomeInstrumentKind
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceState
import com.amond.kmpbook.domain.model.reference.EquityReferenceActionKind
import com.amond.kmpbook.domain.model.reference.EquityReferenceState
import com.amond.kmpbook.domain.model.reference.EquityReferenceStyleFactor
import com.amond.kmpbook.domain.model.reference.FuturesAllocationMode
import com.amond.kmpbook.domain.model.reference.FuturesPortfolioStyle
import com.amond.kmpbook.domain.model.reference.FuturesPriceReturnConvention
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesRollCalendar
import com.amond.kmpbook.domain.model.reference.FundOfFundsActionKind
import com.amond.kmpbook.domain.model.reference.FundOfFundsState
import com.amond.kmpbook.domain.model.reference.KofrIndexBook
import com.amond.kmpbook.domain.model.reference.KofrIndexState
import com.amond.kmpbook.domain.model.reference.YieldCurveTenor
import com.amond.kmpbook.domain.model.marketaction.MarketActionKind
import com.amond.kmpbook.domain.model.marketaction.MarketActionTransition
import com.amond.kmpbook.domain.model.protection.core.TradingHaltReason
import com.amond.kmpbook.domain.model.protection.core.TradingHaltStatus
import com.amond.kmpbook.domain.model.schedule.ScheduledEventKind
import com.amond.kmpbook.domain.tax.core.TaxCategory
import com.amond.kmpbook.domain.tax.core.TaxJurisdiction
import com.amond.kmpbook.domain.tax.fee.FeeCategory
import com.amond.kmpbook.domain.tax.fee.FeeJurisdiction
import com.amond.kmpbook.modding.model.ActiveModConfiguration
import com.amond.kmpbook.persistence.model.GameSaveEnvelope
import com.amond.kmpbook.persistence.model.GameSaveError
import com.amond.kmpbook.persistence.model.GameSaveErrorCode
import com.amond.kmpbook.persistence.model.GameSaveCatalog
import com.amond.kmpbook.persistence.model.GameSaveEntry
import com.amond.kmpbook.persistence.model.GameSaveMetadata
import com.amond.kmpbook.persistence.result.GameLoadFailure
import com.amond.kmpbook.persistence.result.GameLoadNotFound
import com.amond.kmpbook.persistence.result.GameLoadResult
import com.amond.kmpbook.persistence.result.GameLoadSuccess
import com.amond.kmpbook.persistence.result.GameSaveDeleteFailure
import com.amond.kmpbook.persistence.result.GameSaveDeleteNotFound
import com.amond.kmpbook.persistence.result.GameSaveDeleteResult
import com.amond.kmpbook.persistence.result.GameSaveDeleted
import com.amond.kmpbook.persistence.result.GameSaveFailure
import com.amond.kmpbook.persistence.result.GameSaveResult
import com.amond.kmpbook.persistence.result.GameSaveSuccess
import com.amond.kmpbook.persistence.validation.validateSimulatorUiStateIntrinsic
import com.amond.kmpbook.presentation.simulator.NewGameOptions
import com.amond.kmpbook.presentation.portfolio.roundCurrencyForAccounting
import com.amond.kmpbook.presentation.simulator.SimulatorUiState
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.awt.Desktop
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.channels.FileChannel
import java.nio.channels.Channels
import java.nio.charset.CodingErrorAction
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Arrays
import java.util.EnumSet
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

private const val MAX_GAME_SAVE_FILE_BYTES: Long = 128L * 1024L * 1024L
private const val MAX_UNCOMPRESSED_GAME_SAVE_BYTES: Long = 256L * 1024L * 1024L
private const val MAX_SAVED_FUND_FLOW_RATE: Double = 0.20
private const val MIN_SAVED_FUND_REFERENCE_FLOAT_MARKET_VALUE: Double = 1.0
private const val MAX_SAVED_FUND_REFERENCE_FLOAT_MARKET_VALUE: Double = 1e20

private const val GAME_SAVE_EXTENSION: String = ".ml2"
private const val MAX_GAME_SAVE_NAME_LENGTH: Int = 80

private fun defaultGameSaveDirectory(): Path {
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
    return saveDirectory.resolve("saves").toAbsolutePath().normalize()
}

actual class GameSaveStorage actual constructor() {
    private val targetDirectory: Path = defaultGameSaveDirectory()
    private val gson: Gson = createSaveGson()

    actual val saveDirectory: String = targetDirectory.toString()

    actual suspend fun openSaveDirectory(): String? = withContext(Dispatchers.IO) {
        try {
            Files.createDirectories(targetDirectory)
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                return@withContext "이 환경에서는 저장 폴더를 탐색기로 열 수 없습니다."
            }
            Desktop.getDesktop().open(targetDirectory.toFile())
            null
        } catch (error: IOException) {
            "저장 폴더를 열지 못했습니다: ${safeMessage(error)}"
        } catch (error: SecurityException) {
            "저장 폴더에 접근할 권한이 없습니다: ${safeMessage(error)}"
        } catch (error: UnsupportedOperationException) {
            "이 환경에서는 저장 폴더를 탐색기로 열 수 없습니다."
        }
    }

    actual suspend fun save(state: SimulatorUiState, name: String): GameSaveResult = withContext(Dispatchers.IO) {
        val targetPath = try {
            resolveSavePath(name)
        } catch (error: IllegalArgumentException) {
            return@withContext GameSaveFailure(
                path = saveDirectory,
                error = GameSaveError(
                    GameSaveErrorCode.INVALID_FILE_NAME,
                    error.message ?: "저장 파일 이름이 올바르지 않습니다.",
                ),
            )
        }
        val validationError = validateSimulatorUiStateIntrinsic(state)
        if (validationError != null) {
            return@withContext GameSaveFailure(
                path = targetPath.toString(),
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
        var temporaryPath: Path? = null
        try {
            Files.createDirectories(targetDirectory)
            temporaryPath = createPrivateTemporaryFile(
                targetDirectory,
                ".market-ledger-",
                ".tmp",
            )
            val frameHeader: GameSaveFrameHeader
            val physicalBytes: Long
            FileChannel.open(
                temporaryPath,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                writeFully(channel, java.nio.ByteBuffer.wrap(ByteArray(GameSaveFrameHeader.BYTE_SIZE)))
                val physicalPayload = LimitedCountingOutputStream(
                    BufferedOutputStream(Channels.newOutputStream(channel), SAVE_STREAM_BUFFER_BYTES),
                    MAX_GAME_SAVE_FILE_BYTES - GameSaveFrameHeader.BYTE_SIZE,
                )
                val gzip = LevelSixGzipOutputStream(physicalPayload)
                val raw = DigestingLimitedOutputStream(gzip, MAX_UNCOMPRESSED_GAME_SAVE_BYTES)
                val writer = OutputStreamWriter(raw, StandardCharsets.UTF_8)
                val jsonWriter = gson.newJsonWriter(writer).apply { strictness = Strictness.STRICT }
                gson.toJson(envelope, GameSaveEnvelope::class.java, jsonWriter)
                jsonWriter.flush()
                gzip.finish()
                gzip.flush()
                physicalPayload.flush()
                frameHeader = GameSaveFrameHeader(
                    schemaVersion = envelope.schemaVersion,
                    savedAt = envelope.savedAt,
                    gameTime = envelope.state.currentTime,
                    turn = envelope.state.turn,
                    compressedLength = physicalPayload.count,
                    rawLength = raw.count,
                    rawSha256 = raw.digest(),
                )
                validateFrameMetadata(frameHeader)
                physicalBytes = GameSaveFrameHeader.BYTE_SIZE + physicalPayload.count
                channel.position(0L)
                writeFully(channel, java.nio.ByteBuffer.wrap(frameHeader.encode()))
                channel.truncate(physicalBytes)
                channel.force(true)
            }

            val usedAtomicMove = moveIntoPlace(temporaryPath, targetPath)
            temporaryPath = null
            forceDirectoryBestEffort(targetDirectory)
            GameSaveSuccess(
                path = targetPath.toString(),
                metadata = envelope.metadata(),
                bytesWritten = physicalBytes,
                usedAtomicMove = usedAtomicMove,
            )
        } catch (error: UncompressedSaveTooLargeException) {
            GameSaveFailure(
                path = targetPath.toString(),
                error = uncompressedTooLargeError(error.actualSize),
            )
        } catch (error: JsonParseException) {
            GameSaveFailure(
                path = targetPath.toString(),
                error = sizeLimitError(error) ?: GameSaveError(
                    code = GameSaveErrorCode.SERIALIZATION_FAILED,
                    message = "게임 상태를 JSON으로 변환하지 못했습니다: ${safeMessage(error)}",
                    causeType = error::class.qualifiedName,
                ),
            )
        } catch (error: SecurityException) {
            GameSaveFailure(
                path = targetPath.toString(),
                error = accessError(error),
            )
        } catch (error: IOException) {
            GameSaveFailure(
                path = targetPath.toString(),
                error = sizeLimitError(error)
                    ?: ioError("저장 파일을 안전하게 쓰지 못했습니다", error),
            )
        } catch (error: RuntimeException) {
            GameSaveFailure(
                path = targetPath.toString(),
                error = sizeLimitError(error)
                    ?: ioError("저장 파일 처리 중 오류가 발생했습니다", error),
            )
        } finally {
            temporaryPath?.let { path -> runCatching { Files.deleteIfExists(path) } }
        }
    }

    actual suspend fun load(fileName: String): GameLoadResult = withContext(Dispatchers.IO) {
        val targetPath = try {
            resolveSavePath(fileName)
        } catch (error: IllegalArgumentException) {
            return@withContext GameLoadFailure(
                path = saveDirectory,
                error = GameSaveError(
                    GameSaveErrorCode.INVALID_FILE_NAME,
                    error.message ?: "저장 파일 이름이 올바르지 않습니다.",
                ),
            )
        }
        var rawTemporaryPath: Path? = null
        var typedStructureTemporaryPath: Path? = null
        try {
            if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameLoadNotFound(targetPath.toString())
            }
            if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameLoadFailure(
                    path = targetPath.toString(),
                    error = GameSaveError(
                        GameSaveErrorCode.IO_ERROR,
                        "저장 경로가 일반 파일이 아닙니다.",
                    ),
                )
            }
            val declaredSize = Files.size(targetPath)
            if (declaredSize > MAX_GAME_SAVE_FILE_BYTES) {
                return@withContext GameLoadFailure(targetPath.toString(), tooLargeError(declaredSize))
            }
            if (declaredSize <= GameSaveFrameHeader.BYTE_SIZE) {
                return@withContext corrupted(targetPath, "저장 프레임 또는 payload가 비어 있습니다.")
            }
            val rawPath = createPrivateTemporaryFile(
                targetDirectory,
                ".market-ledger-raw-",
                ".tmp",
            )
            rawTemporaryPath = rawPath
            val header = Files.newInputStream(targetPath, StandardOpenOption.READ).use { fileInput ->
                val buffered = BufferedInputStream(fileInput, SAVE_STREAM_BUFFER_BYTES)
                val header = readFrameHeader(buffered)
                validateFrameLengths(header, declaredSize)
                if (header.schemaVersion != CURRENT_GAME_SAVE_SCHEMA_VERSION) {
                    throw UnsupportedSchemaException(header.schemaVersion)
                }
                validateFrameMetadata(header)
                val payload = ExactLengthInputStream(buffered, header.compressedLength)
                val gzip = SingleMemberGzipInputStream(payload)
                val raw = BoundedDigestInputStream(gzip, MAX_UNCOMPRESSED_GAME_SAVE_BYTES)
                BufferedOutputStream(
                    Files.newOutputStream(
                        rawPath,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                    ),
                    SAVE_STREAM_BUFFER_BYTES,
                ).use { output ->
                    val buffer = ByteArray(SAVE_STREAM_BUFFER_BYTES)
                    while (true) {
                        val read = raw.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
                if (payload.remaining != 0L || buffered.read() != -1 ||
                    raw.count != header.rawLength ||
                    !MessageDigest.isEqual(raw.digest(), header.rawSha256)
                ) {
                    throw JsonParseException("저장 payload의 선언 길이 또는 SHA-256이 일치하지 않습니다.")
                }
                if (Files.size(rawPath) != header.rawLength) {
                    throw JsonParseException("임시 raw payload 길이가 저장 프레임과 다릅니다.")
                }
                FileChannel.open(rawPath, StandardOpenOption.WRITE).use { channel ->
                    channel.force(true)
                }
                header
            }
            val rawStructureDigest = readStrictRawJson(
                rawPath,
                GameSaveJsonStructureDigest::fromJsonReader,
            )
            val validatedMetadata = readStrictRawJson(rawPath, ::validateEnvelopeJson)
            if (!header.matches(validatedMetadata)) {
                throw JsonParseException("저장 프레임 metadata와 JSON envelope가 일치하지 않습니다.")
            }
            val secondPassRaw = BoundedDigestInputStream(
                BufferedInputStream(
                    Files.newInputStream(rawPath, StandardOpenOption.READ),
                    SAVE_STREAM_BUFFER_BYTES,
                ),
                MAX_UNCOMPRESSED_GAME_SAVE_BYTES,
            )
            val envelope = secondPassRaw.use { input ->
                val decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                JsonReader(InputStreamReader(input, decoder)).use(::parseTypedEnvelope)
            }
            val typedStructurePath = createPrivateTemporaryFile(
                targetDirectory,
                ".market-ledger-shape-",
                ".tmp",
            )
            typedStructureTemporaryPath = typedStructurePath
            writeTypedEnvelopeForStructure(envelope, typedStructurePath)
            val typedStructureDigest = readStrictRawJson(
                typedStructurePath,
                GameSaveJsonStructureDigest::fromJsonReader,
            )
            if (secondPassRaw.count != header.rawLength ||
                !MessageDigest.isEqual(secondPassRaw.digest(), header.rawSha256) ||
                !MessageDigest.isEqual(rawStructureDigest, typedStructureDigest) ||
                envelope.metadata() != validatedMetadata || !header.matches(envelope.metadata())
            ) {
                throw JsonParseException(
                    "검증·복원 pass 사이에 raw payload, JSON 구조 또는 metadata가 달라졌습니다.",
                )
            }
            val validationError = validateSimulatorUiStateIntrinsic(envelope.state)
            if (validationError != null) {
                return@withContext GameLoadFailure(
                    path = targetPath.toString(),
                    error = GameSaveError(GameSaveErrorCode.INVALID_STATE, validationError),
                )
            }
            GameLoadSuccess(
                path = targetPath.toString(),
                state = envelope.state,
                metadata = envelope.metadata(),
                bytesRead = declaredSize,
            )
        } catch (error: UnsupportedSchemaException) {
            GameLoadFailure(
                path = targetPath.toString(),
                error = GameSaveError(
                    code = GameSaveErrorCode.UNSUPPORTED_SCHEMA,
                    message = error.message ?: "지원하지 않는 저장 스키마입니다.",
                    causeType = error::class.qualifiedName,
                ),
            )
        } catch (error: UncompressedSaveTooLargeException) {
            GameLoadFailure(
                path = targetPath.toString(),
                error = uncompressedTooLargeError(error.actualSize),
            )
        } catch (error: CorruptSaveFrameException) {
            corrupted(targetPath, "저장 압축 프레임이 손상되었습니다: ${safeMessage(error)}", error)
        } catch (error: CharacterCodingException) {
            corrupted(targetPath, "저장 파일이 올바른 UTF-8이 아닙니다.", error)
        } catch (error: JsonParseException) {
            sizeLimitError(error)?.let { limitError ->
                GameLoadFailure(targetPath.toString(), limitError)
            } ?: corrupted(targetPath, "저장 데이터가 손상되었습니다: ${safeMessage(error)}", error)
        } catch (error: IllegalStateException) {
            corrupted(targetPath, "저장 파일 구조가 올바르지 않습니다: ${safeMessage(error)}", error)
        } catch (error: SecurityException) {
            GameLoadFailure(targetPath.toString(), accessError(error))
        } catch (error: IOException) {
            GameLoadFailure(
                targetPath.toString(),
                sizeLimitError(error) ?: ioError("저장 파일을 읽지 못했습니다", error),
            )
        } catch (error: RuntimeException) {
            sizeLimitError(error)?.let { limitError ->
                GameLoadFailure(targetPath.toString(), limitError)
            } ?: corrupted(targetPath, "저장 상태를 복원하지 못했습니다: ${safeMessage(error)}", error)
        } finally {
            rawTemporaryPath?.let { path -> runCatching { Files.deleteIfExists(path) } }
            typedStructureTemporaryPath?.let { path -> runCatching { Files.deleteIfExists(path) } }
        }
    }

    actual suspend fun list(): GameSaveCatalog = withContext(Dispatchers.IO) {
        try {
            if (!Files.exists(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSaveCatalog(emptyList())
            }
            if (!Files.isDirectory(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSaveCatalog(
                    entries = emptyList(),
                    error = GameSaveError(
                        GameSaveErrorCode.IO_ERROR,
                        "저장 경로가 디렉터리가 아닙니다.",
                    ),
                )
            }
            val entries = Files.list(targetDirectory).use { paths ->
                paths
                    .filter { path ->
                        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                            path.fileName.toString().endsWith(GAME_SAVE_EXTENSION, ignoreCase = true)
                    }
                    .map { path -> readCatalogEntry(path) }
                    .filter { entry -> entry != null }
                    .map { entry -> requireNotNull(entry) }
                    .toList()
            }
            GameSaveCatalog(entries.sortedByDescending { it.metadata.savedAt })
        } catch (error: SecurityException) {
            GameSaveCatalog(emptyList(), accessError(error))
        } catch (error: IOException) {
            GameSaveCatalog(emptyList(), ioError("저장 파일 목록을 읽지 못했습니다", error))
        }
    }

    actual suspend fun delete(fileName: String): GameSaveDeleteResult = withContext(Dispatchers.IO) {
        val targetPath = try {
            resolveSavePath(fileName)
        } catch (error: IllegalArgumentException) {
            return@withContext GameSaveDeleteFailure(
                path = saveDirectory,
                error = GameSaveError(
                    GameSaveErrorCode.INVALID_FILE_NAME,
                    error.message ?: "저장 파일 이름이 올바르지 않습니다.",
                ),
            )
        }
        try {
            if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSaveDeleteNotFound(targetPath.toString())
            }
            if (Files.isDirectory(targetPath, LinkOption.NOFOLLOW_LINKS)) {
                return@withContext GameSaveDeleteFailure(
                    path = targetPath.toString(),
                    error = GameSaveError(
                        GameSaveErrorCode.IO_ERROR,
                        "안전을 위해 저장 경로가 디렉터리이면 삭제하지 않습니다.",
                    ),
                )
            }
            if (Files.deleteIfExists(targetPath)) {
                GameSaveDeleted(targetPath.toString())
            } else {
                GameSaveDeleteNotFound(targetPath.toString())
            }
        } catch (error: SecurityException) {
            GameSaveDeleteFailure(targetPath.toString(), accessError(error))
        } catch (error: IOException) {
            GameSaveDeleteFailure(targetPath.toString(), ioError("저장 파일을 삭제하지 못했습니다", error))
        }
    }

    private fun resolveSavePath(name: String): Path {
        val trimmedName = name.trim()
        val stem = if (trimmedName.endsWith(GAME_SAVE_EXTENSION, ignoreCase = true)) {
            trimmedName.dropLast(GAME_SAVE_EXTENSION.length)
        } else {
            trimmedName
        }
        require(stem.isNotBlank()) { "저장 파일 이름을 입력하세요." }
        require(stem.length <= MAX_GAME_SAVE_NAME_LENGTH) {
            "저장 파일 이름은 ${MAX_GAME_SAVE_NAME_LENGTH}자 이하여야 합니다."
        }
        require(stem.none { it.code < 32 || it in INVALID_FILE_NAME_CHARACTERS }) {
            "저장 파일 이름에는 \\/ : * ? \" < > | 문자를 사용할 수 없습니다."
        }
        require(stem != "." && stem != ".." && !stem.endsWith('.') && !stem.endsWith(' ')) {
            "저장 파일 이름은 점이나 공백으로 끝날 수 없습니다."
        }
        require(!WINDOWS_RESERVED_NAMES.matches(stem)) { "운영체제가 예약한 파일 이름은 사용할 수 없습니다." }
        val resolved = targetDirectory.resolve("$stem$GAME_SAVE_EXTENSION").normalize()
        require(resolved.parent == targetDirectory) { "저장 디렉터리 밖의 경로는 사용할 수 없습니다." }
        return resolved
    }

    private fun readCatalogEntry(path: Path): GameSaveEntry? = try {
        val size = Files.size(path)
        if (size > MAX_GAME_SAVE_FILE_BYTES || size == 0L) return null
        if (size <= GameSaveFrameHeader.BYTE_SIZE) return null
        val header = Files.newInputStream(path, StandardOpenOption.READ).use { input ->
            readFrameHeader(BufferedInputStream(input, GameSaveFrameHeader.BYTE_SIZE))
        }
        validateFrameLengths(header, size)
        if (header.schemaVersion != CURRENT_GAME_SAVE_SCHEMA_VERSION) return null
        validateFrameMetadata(header)
        val fileName = path.fileName.toString()
        GameSaveEntry(
            name = fileName.dropLast(GAME_SAVE_EXTENSION.length),
            fileName = fileName,
            path = path.toString(),
            sizeBytes = size,
            metadata = GameSaveMetadata(
                format = GAME_SAVE_FORMAT_ID,
                schemaVersion = header.schemaVersion,
                savedAt = header.savedAt,
                gameTime = header.gameTime,
                turn = header.turn,
            ),
        )
    } catch (_: RuntimeException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun validateEnvelopeJson(reader: JsonReader): GameSaveMetadata {
        reader.strictness = Strictness.STRICT
        val root = JsonParser.parseReader(reader).also {
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw JsonParseException("JSON 뒤에 추가 데이터가 있습니다.")
            }
        }
        if (!root.isJsonObject) throw JsonParseException("저장 파일 루트가 JSON 객체가 아닙니다.")
        val objectValue = root.asJsonObject
        objectValue.requireExactFields(
            expected = CURRENT_ENVELOPE_FIELDS,
            path = "save",
        )
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
        return GameSaveMetadata(
            format = format,
            schemaVersion = schemaVersion,
            savedAt = savedAt,
            gameTime = stateJson.requiredInstant("currentTime", "state.currentTime"),
            turn = stateJson.requiredLong("turn", "state.turn"),
        )
    }

    private fun parseTypedEnvelope(reader: JsonReader): GameSaveEnvelope {
        reader.strictness = Strictness.STRICT
        val envelope: GameSaveEnvelope = gson.fromJson(reader, GameSaveEnvelope::class.java)
            ?: throw JsonParseException("저장 envelope를 복원할 수 없습니다.")
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw JsonParseException("JSON 뒤에 추가 데이터가 있습니다.")
        }
        return envelope
    }

    private fun writeTypedEnvelopeForStructure(
        envelope: GameSaveEnvelope,
        path: Path,
    ) {
        val limited = LimitedCountingOutputStream(
            BufferedOutputStream(
                Files.newOutputStream(
                    path,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ),
                SAVE_STREAM_BUFFER_BYTES,
            ),
            MAX_UNCOMPRESSED_GAME_SAVE_BYTES,
        )
        OutputStreamWriter(limited, StandardCharsets.UTF_8).use { writer ->
            gson.newJsonWriter(writer).use { jsonWriter ->
                jsonWriter.strictness = Strictness.STRICT
                gson.toJson(envelope, GameSaveEnvelope::class.java, jsonWriter)
            }
        }
    }

    private fun <T> readStrictRawJson(path: Path, block: (JsonReader) -> T): T =
        Files.newInputStream(path, StandardOpenOption.READ).use { input ->
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            JsonReader(InputStreamReader(BufferedInputStream(input), decoder)).use(block)
        }

    private fun validateCurrentStateJson(state: JsonObject) {
        val currentBenchmarkValue = state.requiredFiniteDouble(
            "currentBenchmarkValue",
            "state.currentBenchmarkValue",
        )
        if (currentBenchmarkValue <= 0.0) {
            throw JsonParseException("필드 'state.currentBenchmarkValue'는 양수여야 합니다.")
        }

        fun JsonObject.requireExternalMarketForces(path: String) {
            requireExactFields(EXTERNAL_MARKET_FORCES_FIELDS, path)
            EXTERNAL_MARKET_FORCES_FIELDS.forEach { field ->
                val value = requiredFiniteDouble(field, "$path.$field")
                if (value !in 0.0..1.0) {
                    throw JsonParseException("필드 '$path.$field'는 0과 1 사이여야 합니다.")
                }
            }
        }

        fun JsonObject.requireReferencePortfolioPosition(path: String): String {
            requireExactFields(REFERENCE_PORTFOLIO_POSITION_FIELDS, path)
            val assetId = requiredBoundedNonBlankString(
                "assetId",
                "$path.assetId",
                MAX_REFERENCE_ASSET_ID_LENGTH,
            )
            listOf("currentWeight", "targetWeight").forEach { field ->
                val weight = requiredFiniteDouble(field, "$path.$field")
                if (weight !in MIN_FUND_CONSTITUENT_WEIGHT..1.0) {
                    throw JsonParseException("필드 '$path.$field'가 허용 비중 범위를 벗어났습니다.")
                }
            }
            val referenceValue = requiredFiniteDouble(
                "referenceFloatMarketValue",
                "$path.referenceFloatMarketValue",
            )
            if (referenceValue !in
                MIN_SAVED_FUND_REFERENCE_FLOAT_MARKET_VALUE..MAX_SAVED_FUND_REFERENCE_FLOAT_MARKET_VALUE
            ) {
                throw JsonParseException(
                    "필드 '$path.referenceFloatMarketValue'가 허용 기준 시가가치 범위를 벗어났습니다.",
                )
            }
            requiredLocalDate("enteredOn", "$path.enteredOn")
            val rank = requiredInt("selectionRank")
            if (rank !in 1..MAX_FUND_SELECTION_RANK) {
                throw JsonParseException("필드 '$path.selectionRank'가 허용 범위를 벗어났습니다.")
            }
            return assetId
        }

        fun JsonObject.requireEquityMethodologyPathState(name: String, path: String) {
            val pathState = requiredObject(name)
            pathState.requireExactFields(EQUITY_METHODOLOGY_PATH_STATE_FIELDS, path)
            val entries = pathState.requiredArray("entries")
            if (entries.size() > EquityMethodologyPathState.MAX_ENTRIES) {
                throw JsonParseException("필드 '$path.entries'의 항목이 너무 많습니다.")
            }
            val assetIds = entries.mapIndexed { index, element ->
                val entryPath = "$path.entries[$index]"
                element.requireObject(entryPath).run {
                    requireExactFields(EQUITY_METHODOLOGY_PATH_ENTRY_FIELDS, entryPath)
                    val assetId = requiredBoundedNonBlankString(
                        "assetId",
                        "$entryPath.assetId",
                        EquityMethodologyPathEntry.MAX_ASSET_ID_LENGTH,
                    )
                    if (!EquityMethodologyPathEntry.isValidAssetId(assetId)) {
                        throw JsonParseException("필드 '$entryPath.assetId'의 자산 ID가 유효하지 않습니다.")
                    }
                    val decimalValues = requiredObject("decimalValues")
                    val booleanValues = requiredObject("booleanValues")
                    if (decimalValues.size() > EquityMethodologyPathEntry.MAX_DECIMAL_VALUES ||
                        booleanValues.size() > EquityMethodologyPathEntry.MAX_BOOLEAN_VALUES ||
                        decimalValues.size() + booleanValues.size() == 0
                    ) {
                        throw JsonParseException("필드 '$entryPath'의 방법론 경로 값 개수가 유효하지 않습니다.")
                    }
                    val decimalKeys = decimalValues.keySet().toList()
                    val booleanKeys = booleanValues.keySet().toList()
                    if (decimalKeys != decimalKeys.sorted() || booleanKeys != booleanKeys.sorted() ||
                        (decimalKeys + booleanKeys).any {
                            !EquityMethodologyPathEntry.isValidValueKey(it)
                        }
                    ) {
                        throw JsonParseException("필드 '$entryPath'의 방법론 경로 키가 정규화되지 않았습니다.")
                    }
                    decimalKeys.forEach { key ->
                        if (decimalValues.requiredFiniteDouble(key, "$entryPath.decimalValues.$key") !in
                            0.0..1.0
                        ) {
                            throw JsonParseException(
                                "필드 '$entryPath.decimalValues.$key'는 0과 1 사이여야 합니다.",
                            )
                        }
                    }
                    booleanKeys.forEach { key ->
                        booleanValues.requiredBoolean(key, "$entryPath.booleanValues.$key")
                    }
                    assetId
                }
            }
            if (assetIds != assetIds.distinct().sorted()) {
                throw JsonParseException("필드 '$path.entries'가 자산 ID 순서로 정규화되지 않았습니다.")
            }
        }

        fun JsonObject.requireReferencePortfolioPositions(field: String, path: String): List<String> {
            val positions = requiredArray(field)
            if (positions.size() == 0 || positions.size() > ReferencePortfolioLimits.MAX_CONSTITUENTS) {
                throw JsonParseException(
                    "필드 '$path'는 1~${ReferencePortfolioLimits.MAX_CONSTITUENTS}개 항목이어야 합니다.",
                )
            }
            return positions.mapIndexed { index, positionElement ->
                val positionPath = "$path[$index]"
                positionElement.requireObject(positionPath)
                    .requireReferencePortfolioPosition(positionPath)
            }.also { assetIds ->
                if (assetIds != assetIds.distinct().sorted()) {
                    throw JsonParseException("필드 '$path'는 assetId 순서의 고유 구성종목이어야 합니다.")
                }
            }
        }

        fun JsonObject.requireReferenceAssetIdArray(field: String, path: String): List<String> {
            val ids = requiredArray(field)
            if (ids.size() > ReferencePortfolioLimits.MAX_CONSTITUENTS) {
                throw JsonParseException("필드 '$path'의 항목이 너무 많습니다.")
            }
            return ids.mapIndexed { index, idElement ->
                val idPath = "$path[$index]"
                val id = idElement.requireStrictString(idPath)
                if (id.isBlank() || id.length > MAX_REFERENCE_ASSET_ID_LENGTH) {
                    throw JsonParseException("필드 '$idPath'의 길이가 올바르지 않습니다.")
                }
                id
            }
        }

        fun JsonObject.requireNullableReferenceAssetIdArray(
            field: String,
            path: String,
        ): List<String>? {
            requireMember(field)
            val element = get(field)
            if (element.isJsonNull) return null
            if (!element.isJsonArray) {
                throw JsonParseException("필드 '$path'는 배열 또는 null이어야 합니다.")
            }
            val ids = element.asJsonArray
            if (ids.size() > ReferencePortfolioLimits.MAX_CONSTITUENTS) {
                throw JsonParseException("필드 '$path'의 항목이 너무 많습니다.")
            }
            return ids.mapIndexed { index, idElement ->
                val idPath = "$path[$index]"
                idElement.requireStrictString(idPath).also { id ->
                    if (!REFERENCE_ASSET_ID.matches(id)) {
                        throw JsonParseException("필드 '$idPath'의 자산 ID 형식이 유효하지 않습니다.")
                    }
                }
            }.also { assetIds ->
                if (assetIds != assetIds.distinct().sorted()) {
                    throw JsonParseException("필드 '$path'는 정렬된 고유 자산 ID 목록이어야 합니다.")
                }
            }
        }

        fun JsonObject.requireWeightReferenceMarketValues(
            field: String,
            path: String,
            expectedAssetIds: List<String>,
            requiresUnitSum: Boolean = false,
        ): Boolean {
            requireMember(field)
            val element = get(field)
            if (element.isJsonNull) return false
            val marketValues = element.requireObject(path)
            if (marketValues.size() == 0 ||
                marketValues.size() > ReferencePortfolioLimits.MAX_CONSTITUENTS
            ) {
                throw JsonParseException(
                    "필드 '$path'는 1~${ReferencePortfolioLimits.MAX_CONSTITUENTS}개 항목이어야 합니다.",
                )
            }
            val assetIds = marketValues.keySet().toList()
            if (assetIds != assetIds.sorted() || assetIds != expectedAssetIds ||
                assetIds.any { assetId -> !REFERENCE_ASSET_ID.matches(assetId) }
            ) {
                throw JsonParseException(
                    "필드 '$path'는 positions와 같은 순서·ID의 시가가치 맵이어야 합니다.",
                )
            }
            val totalValue = assetIds.sumOf { assetId ->
                marketValues.requiredFiniteDouble(assetId, "$path.$assetId").also { value ->
                    if (value <= 0.0) {
                        throw JsonParseException("필드 '$path.$assetId'는 양수여야 합니다.")
                    }
                }
            }
            if (requiresUnitSum &&
                kotlin.math.abs(totalValue - 1.0) > ReferencePortfolioState.WEIGHT_EPSILON
            ) {
                throw JsonParseException("필드 '$path'의 제약 재조정 입력 합은 1이어야 합니다.")
            }
            return true
        }

        fun JsonObject.requireTransitionBaselineWeights(
            field: String,
            path: String,
            positionAssetIds: List<String>,
        ): Boolean {
            requireMember(field)
            val element = get(field)
            if (element.isJsonNull) return false
            val weights = element.requireObject(path)
            if (weights.size() == 0 ||
                weights.size() > ReferencePortfolioLimits.MAX_CONSTITUENTS
            ) {
                throw JsonParseException(
                    "필드 '$path'는 1~${ReferencePortfolioLimits.MAX_CONSTITUENTS}개 항목이어야 합니다.",
                )
            }
            val assetIds = weights.keySet().toList()
            if (assetIds != assetIds.sorted() ||
                assetIds.any { assetId ->
                    assetId !in positionAssetIds || !REFERENCE_ASSET_ID.matches(assetId)
                }
            ) {
                throw JsonParseException(
                    "필드 '$path'는 transition positions의 정렬된 baseline ID 맵이어야 합니다.",
                )
            }
            val totalWeight = assetIds.sumOf { assetId ->
                weights.requiredFiniteDouble(assetId, "$path.$assetId").also { weight ->
                    if (weight <= 0.0) {
                        throw JsonParseException("필드 '$path.$assetId'는 양수여야 합니다.")
                    }
                }
            }
            if (kotlin.math.abs(totalWeight - 1.0) > ReferencePortfolioState.WEIGHT_EPSILON) {
                throw JsonParseException("필드 '$path'의 baseline 비중 합은 1이어야 합니다.")
            }
            return true
        }

        fun JsonObject.requireReferencePortfolioCorporateAction(
            field: String,
            path: String,
        ): LocalDate? {
            requireMember(field)
            val element = get(field)
            if (element.isJsonNull) return null
            return element.requireObject(path).run {
                requireExactFields(REFERENCE_PORTFOLIO_CORPORATE_ACTION_FIELDS, path)
                val eventId = requiredStrictString("eventId", "$path.eventId")
                if (!REFERENCE_EVENT_ID.matches(eventId)) {
                    throw JsonParseException("필드 '$path.eventId'의 기업행동 ID 형식이 유효하지 않습니다.")
                }
                val kind = requiredEnum<ReferencePortfolioCorporateActionKind>(
                    "kind",
                    "$path.kind",
                )
                val announcementDate = requiredLocalDate(
                    "announcementDate",
                    "$path.announcementDate",
                )
                val effectiveDate = requiredLocalDate("effectiveDate", "$path.effectiveDate")
                if (announcementDate >= effectiveDate) {
                    throw JsonParseException("필드 '$path'의 발표일은 효력일보다 앞서야 합니다.")
                }
                val primaryAssetId = requiredStrictString(
                    "primaryAssetId",
                    "$path.primaryAssetId",
                )
                val secondaryAssetId = nullableStrictString(
                    "secondaryAssetId",
                    "$path.secondaryAssetId",
                )
                if (!REFERENCE_ASSET_ID.matches(primaryAssetId) ||
                    secondaryAssetId?.let(REFERENCE_ASSET_ID::matches) == false ||
                    secondaryAssetId == primaryAssetId
                ) {
                    throw JsonParseException("필드 '$path'의 기준자산 ID 구조가 유효하지 않습니다.")
                }
                val considerationKind =
                    requiredEnum<ReferencePortfolioCorporateActionConsiderationKind>(
                        "considerationKind",
                        "$path.considerationKind",
                    )
                val valueTransferFraction = requiredFiniteDouble(
                    "valueTransferFraction",
                    "$path.valueTransferFraction",
                )
                if (valueTransferFraction !in 0.0..1.0) {
                    throw JsonParseException("필드 '$path.valueTransferFraction'은 0과 1 사이여야 합니다.")
                }
                val followUpEffectiveDate = nullableLocalDate(
                    "followUpEffectiveDate",
                    "$path.followUpEffectiveDate",
                )
                if (followUpEffectiveDate?.let { date -> date <= effectiveDate } == true) {
                    throw JsonParseException("필드 '$path.followUpEffectiveDate'는 최초 효력일보다 늦어야 합니다.")
                }
                val validKindStructure = when (kind) {
                    ReferencePortfolioCorporateActionKind.MERGER ->
                        secondaryAssetId != null && followUpEffectiveDate == null &&
                            when (considerationKind) {
                                ReferencePortfolioCorporateActionConsiderationKind.CASH ->
                                    valueTransferFraction == 0.0
                                ReferencePortfolioCorporateActionConsiderationKind.STOCK ->
                                    valueTransferFraction == 1.0
                                ReferencePortfolioCorporateActionConsiderationKind.MIXED ->
                                    valueTransferFraction > 0.0 && valueTransferFraction < 1.0
                                ReferencePortfolioCorporateActionConsiderationKind.NONE -> false
                            }

                    ReferencePortfolioCorporateActionKind.SPIN_OFF ->
                        secondaryAssetId != null &&
                            considerationKind == ReferencePortfolioCorporateActionConsiderationKind.NONE &&
                            valueTransferFraction > 0.0 && valueTransferFraction < 1.0 &&
                            followUpEffectiveDate != null

                    ReferencePortfolioCorporateActionKind.TERMINAL_REMOVAL ->
                        secondaryAssetId == null &&
                            considerationKind == ReferencePortfolioCorporateActionConsiderationKind.NONE &&
                            valueTransferFraction == 0.0 && followUpEffectiveDate == null
                }
                if (!validKindStructure) {
                    throw JsonParseException("필드 '$path'의 기업행동 종류별 구조가 유효하지 않습니다.")
                }
                effectiveDate
            }
        }

        fun JsonObject.requireEquityReferenceAssetIds(field: String, path: String): List<String> {
            val ids = requiredArray(field)
            if (ids.size() > EquityReferenceState.MAX_REPRESENTATIVE_BASKET_SIZE) {
                throw JsonParseException("필드 '$path'의 항목이 너무 많습니다.")
            }
            return ids.mapIndexed { index, element ->
                val idPath = "$path[$index]"
                element.requireStrictString(idPath).also { id ->
                    if (!EQUITY_REFERENCE_ASSET_ID_PATTERN.matches(id)) {
                        throw JsonParseException("필드 '$idPath' 형식이 유효하지 않습니다.")
                    }
                }
            }.also { values ->
                if (values != values.sorted() || values.distinct() != values) {
                    throw JsonParseException("필드 '$path'는 정렬된 고유 ID 목록이어야 합니다.")
                }
            }
        }

        fun JsonObject.requireFundOfFundsCandidateIds(field: String, path: String): List<String> {
            val ids = requiredArray(field)
            if (ids.size() > FundOfFundsState.MAX_POSITIONS) {
                throw JsonParseException("필드 '$path'의 항목이 너무 많습니다.")
            }
            return ids.mapIndexed { index, element ->
                val idPath = "$path[$index]"
                element.requireStrictString(idPath).also { id ->
                    if (!FUND_OF_FUNDS_CANDIDATE_ID_PATTERN.matches(id)) {
                        throw JsonParseException("필드 '$idPath' 형식이 유효하지 않습니다.")
                    }
                }
            }.also { values ->
                if (values != values.sorted() || values.distinct() != values) {
                    throw JsonParseException("필드 '$path'는 정렬된 고유 ID 목록이어야 합니다.")
                }
            }
        }

        fun JsonObject.requireCompositeMemberIds(field: String, path: String): List<String> {
            val ids = requiredArray(field)
            if (ids.size() > CompositeReferenceState.MAX_POSITIONS) {
                throw JsonParseException("필드 '$path'의 항목이 너무 많습니다.")
            }
            return ids.mapIndexed { index, element ->
                val idPath = "$path[$index]"
                element.requireStrictString(idPath).also { id ->
                    if (!COMPOSITE_MEMBER_ID_PATTERN.matches(id)) {
                        throw JsonParseException("필드 '$idPath' 형식이 유효하지 않습니다.")
                    }
                }
            }.also { values ->
                if (values != values.sorted() || values.distinct() != values) {
                    throw JsonParseException("필드 '$path'는 정렬된 고유 ID 목록이어야 합니다.")
                }
            }
        }

        fun JsonObject.requireStructuredReferenceMeasures(path: String) {
            if (requiredFiniteDouble(
                    "estimatedAnnualIncomeYield",
                    "$path.estimatedAnnualIncomeYield",
                ) !in 0.0..1.0 ||
                requiredFiniteDouble("grossExposure", "$path.grossExposure") !in 0.0..10.0 ||
                requiredFiniteDouble("netExposure", "$path.netExposure") !in -10.0..10.0 ||
                requiredFiniteDouble(
                    "effectiveDurationYears",
                    "$path.effectiveDurationYears",
                ) !in -50.0..50.0
            ) {
                throw JsonParseException("필드 '$path'의 소득·총/순노출·듀레이션 범위가 유효하지 않습니다.")
            }
            listOf("bootstrapCompositionHash", "profileFingerprint", "compositionHash").forEach { field ->
                if (!REFERENCE_COMPOSITION_HASH.matches(requiredStrictString(field, "$path.$field"))) {
                    throw JsonParseException("필드 '$path.$field'는 소문자 16자리 16진 해시여야 합니다.")
                }
            }
        }

        fun JsonObject.requireStructuredReferenceRecordMeasures(path: String) {
            listOf("compositionHashBefore", "compositionHashAfter").forEach { field ->
                if (!REFERENCE_COMPOSITION_HASH.matches(requiredStrictString(field, "$path.$field"))) {
                    throw JsonParseException("필드 '$path.$field'는 소문자 16자리 16진 해시여야 합니다.")
                }
            }
            if (requiredFiniteDouble("turnoverRate", "$path.turnoverRate") !in 0.0..10.0 ||
                requiredFiniteDouble(
                    "resultingGrossExposure",
                    "$path.resultingGrossExposure",
                ) !in 0.0..10.0 ||
                requiredFiniteDouble(
                    "resultingNetExposure",
                    "$path.resultingNetExposure",
                ) !in -10.0..10.0 ||
                requiredFiniteDouble(
                    "resultingDurationYears",
                    "$path.resultingDurationYears",
                ) !in -50.0..50.0 ||
                requiredLong("revision", "$path.revision") <= 0L
            ) {
                throw JsonParseException("필드 '$path'의 회전율·노출·듀레이션·revision 범위가 유효하지 않습니다.")
            }
        }

        fun JsonObject.requireFixedIncomePosition(path: String) {
            requireExactFields(FIXED_INCOME_POSITION_FIELDS, path)
            requiredBoundedNonBlankString("assetId", "$path.assetId", MAX_REFERENCE_ASSET_ID_LENGTH)
            requiredEnum<FixedIncomeInstrumentKind>("kind", "$path.kind")
            requiredEnum<ReferenceCurrency>("currency", "$path.currency")
            requiredEnum<CreditQuality>("creditQuality", "$path.creditQuality")
            listOf("currentWeight", "targetWeight").forEach { field ->
                val weight = requiredFiniteDouble(field, "$path.$field")
                if (weight !in 0.0..1.0) {
                    throw JsonParseException("필드 '$path.$field'가 허용 비중 범위를 벗어났습니다.")
                }
            }
            val boundedFields = mapOf(
                "dirtyMarketValue" to (MIN_FIXED_INCOME_MARKET_VALUE..MAX_FIXED_INCOME_MARKET_VALUE),
                "remainingMaturityYears" to (0.0..MAX_FIXED_INCOME_YEARS),
                "modifiedDurationYears" to (0.0..MAX_FIXED_INCOME_YEARS),
                "convexityYearsSquared" to (0.0..MAX_FIXED_INCOME_CONVEXITY),
                "spreadDurationYears" to (0.0..MAX_FIXED_INCOME_YEARS),
                "couponRateAnnual" to (MIN_FIXED_INCOME_RATE..MAX_FIXED_INCOME_POSITION_RATE),
                "floatingSpreadAnnual" to (MIN_FIXED_INCOME_RATE..MAX_FIXED_INCOME_POSITION_RATE),
                "floatingRateFloorAnnual" to (MIN_FIXED_INCOME_RATE..MAX_FIXED_INCOME_POSITION_RATE),
                "inflationIndexRatio" to
                    (MIN_FIXED_INCOME_INDEX_RATIO..MAX_FIXED_INCOME_INDEX_RATIO),
            )
            boundedFields.forEach { (field, range) ->
                if (requiredFiniteDouble(field, "$path.$field") !in range) {
                    throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                }
            }
        }

        fun JsonObject.requireYieldCurve(path: String): ReferenceCurrency {
            requireExactFields(YIELD_CURVE_SNAPSHOT_FIELDS, path)
            val currency = requiredEnum<ReferenceCurrency>("currency", "$path.currency")
            val rates = requiredEnumFiniteDoubleMap<YieldCurveTenor>(
                "annualZeroRates",
                "$path.annualZeroRates",
            )
            if (rates.keys != YieldCurveTenor.entries.toSet() ||
                rates.values.any { rate -> rate !in MIN_FIXED_INCOME_RATE..MAX_YIELD_CURVE_RATE }
            ) {
                throw JsonParseException("필드 '$path.annualZeroRates'의 만기점·금리 범위가 유효하지 않습니다.")
            }
            requiredInstant("asOf", "$path.asOf")
            return currency
        }

        fun JsonObject.requireCreditSpreads(path: String): ReferenceCurrency {
            requireExactFields(CREDIT_SPREAD_SNAPSHOT_FIELDS, path)
            val currency = requiredEnum<ReferenceCurrency>("currency", "$path.currency")
            val spreads = requiredEnumFiniteDoubleMap<CreditQuality>(
                "annualSpreads",
                "$path.annualSpreads",
            )
            if (spreads.keys != CreditQuality.entries.toSet() ||
                spreads.values.any { spread -> spread !in 0.0..MAX_FIXED_INCOME_CREDIT_SPREAD } ||
                spreads[CreditQuality.SOVEREIGN] != 0.0
            ) {
                throw JsonParseException("필드 '$path.annualSpreads'의 등급·스프레드 범위가 유효하지 않습니다.")
            }
            requiredInstant("asOf", "$path.asOf")
            return currency
        }

        fun JsonObject.requireFixedIncomeAssetIds(field: String, path: String): List<String> {
            val ids = requiredArray(field)
            if (ids.size() == 0 || ids.size() > FixedIncomeReferenceState.MAX_POSITIONS) {
                throw JsonParseException("필드 '$path'의 항목 수가 유효하지 않습니다.")
            }
            return ids.mapIndexed { index, element ->
                val idPath = "$path[$index]"
                element.requireStrictString(idPath).also { id ->
                    if (id.isBlank() || id.length > MAX_REFERENCE_ASSET_ID_LENGTH) {
                        throw JsonParseException("필드 '$idPath'의 길이가 올바르지 않습니다.")
                    }
                }
            }
        }

        state.requiredObject("catalogReference").apply {
            val path = "state.catalogReference"
            requireExactFields(CATALOG_REFERENCE_FIELDS, path)
            val schemaVersion = requiredInt("schemaVersion")
            val orderedSources = requiredArray("orderedSources")
            if (orderedSources.size() > InstrumentCatalogReference.MAX_SOURCES) {
                throw JsonParseException("필드 '$path.orderedSources'의 항목이 너무 많습니다.")
            }
            try {
                val decodedSources = orderedSources.mapIndexed { index, element ->
                    val sourcePath = "$path.orderedSources[$index]"
                    element.requireObject(sourcePath).run {
                        requireExactFields(CATALOG_SOURCE_REFERENCE_FIELDS, sourcePath)
                        InstrumentCatalogSourceReference(
                            sourceId = requiredStrictString("sourceId", "$sourcePath.sourceId"),
                            contentSha256 = requiredStrictString("contentSha256", "$sourcePath.contentSha256"),
                        )
                    }
                }
                InstrumentCatalogReference(schemaVersion, decodedSources)
            } catch (error: IllegalArgumentException) {
                throw JsonParseException("필드 '$path'이 유효하지 않습니다: ${error.message}", error)
            }
        }

        state.requiredObject("options").apply {
            requireExactFields(NEW_GAME_OPTIONS_FIELDS, "state.options")
            val initialCapitalKrw = requiredFiniteDouble(
                "initialCapitalKrw",
                "state.options.initialCapitalKrw",
            )
            if (initialCapitalKrw < NewGameOptions.MIN_INITIAL_CAPITAL_KRW ||
                initialCapitalKrw.toBits() !=
                roundCurrencyForAccounting(initialCapitalKrw, Currency.KRW).toBits()
            ) {
                throw JsonParseException(
                    "필드 'state.options.initialCapitalKrw'는 최소 자금 이상의 원 단위여야 합니다.",
                )
            }
            val scenarioName = requiredString("scenarioName")
            if (scenarioName.isBlank() || scenarioName.length > NewGameOptions.MAX_GAME_LABEL_LENGTH) {
                throw JsonParseException("필드 'state.options.scenarioName'의 길이가 올바르지 않습니다.")
            }
            val difficultyName = requiredString("difficultyName")
            if (difficultyName.isBlank() || difficultyName.length > NewGameOptions.MAX_GAME_LABEL_LENGTH) {
                throw JsonParseException("필드 'state.options.difficultyName'의 길이가 올바르지 않습니다.")
            }
            requiredObject("initialExternalMarketForces")
                .requireExternalMarketForces("state.options.initialExternalMarketForces")
            requiredArray("activeMods").also { activeMods ->
                if (activeMods.size() > NewGameOptions.MAX_ACTIVE_MODS) {
                    throw JsonParseException("필드 'state.options.activeMods'의 항목이 너무 많습니다.")
                }
                activeMods.forEachIndexed { index, element ->
                    val path = "state.options.activeMods[$index]"
                    val activeMod = element.requireObject(path).apply {
                        requireExactFields(ACTIVE_MOD_FIELDS, path)
                    }
                    val settings = activeMod.requiredObject("settings")
                    if (settings.size() > ActiveModConfiguration.MAX_SETTINGS) {
                        throw JsonParseException("필드 '$path.settings'의 항목이 너무 많습니다.")
                    }
                    val decoded = ActiveModConfiguration(
                        id = activeMod.requiredStrictString("id", "$path.id"),
                        version = activeMod.requiredStrictString("version", "$path.version"),
                        settings = settings.entrySet().associate { (key, value) ->
                            key to value.requireStrictString("$path.settings.$key")
                        },
                        contentFingerprint = activeMod.nullableStrictString(
                            "contentFingerprint",
                            "$path.contentFingerprint",
                        ),
                    )
                    decoded.validate()?.let { message ->
                        throw JsonParseException("필드 '$path'이 유효하지 않습니다: $message")
                    }
                }
            }
        }
        state.requiredObject("externalMarketForcesTarget")
            .requireExternalMarketForces("state.externalMarketForcesTarget")
        state.requiredObject("marketDynamicsSnapshot").apply {
            requireExactFields(MARKET_DYNAMICS_SNAPSHOT_FIELDS, "state.marketDynamicsSnapshot")
            requiredObject("effectiveForces")
                .requireExternalMarketForces("state.marketDynamicsSnapshot.effectiveForces")
            requiredObject("regimeProbabilities").apply {
                requireExactFields(MARKET_REGIME_PROBABILITY_FIELDS, "state.marketDynamicsSnapshot.regimeProbabilities")
                MARKET_REGIME_PROBABILITY_FIELDS.forEach { field ->
                    val value = requiredFiniteDouble(
                        field,
                        "state.marketDynamicsSnapshot.regimeProbabilities.$field",
                    )
                    if (value !in 0.0..1.0) {
                        throw JsonParseException(
                            "필드 'state.marketDynamicsSnapshot.regimeProbabilities.$field'는 0과 1 사이여야 합니다.",
                        )
                    }
                }
            }
            listOf(
                "conditionalVariance",
                "newsExcitation",
                "newsIntensity",
                "eventSentimentMemory",
                "liquidityStress",
                "retailFlow",
                "institutionalFlow",
                "downsideMemory",
            ).forEach { field -> requiredFiniteDouble(field, "state.marketDynamicsSnapshot.$field") }
            nullableFiniteDouble(
                "previousObservedReturn",
                "state.marketDynamicsSnapshot.previousObservedReturn",
            )
            requiredLong("randomState", "state.marketDynamicsSnapshot.randomState")
        }
        state.requiredArray("stocks").forEachIndexed { index, element ->
            val path = "state.stocks[$index]"
            element.requireObject(path).apply {
                requireExactFields(STOCK_DEFINITION_FIELDS, path)
                requiredStrictString("symbol", "$path.symbol")
                requiredStrictString("name", "$path.name")
                requiredStrictString("englishName", "$path.englishName")
                requiredEnum<Market>("market", "$path.market")
                requiredEnum<Sector>("sector", "$path.sector")
                listOf(
                    "initialPrice",
                    "volatility",
                    "dividendYield",
                    "marketCap",
                    "beta",
                    "quantityStep",
                    "lotSize",
                ).forEach { field -> requiredFiniteDouble(field, "$path.$field") }
                requiredLong("sharesOutstanding", "$path.sharesOutstanding")
                requiredStrictString("description", "$path.description")
                requireMember("etfProfile")
                requireMember("fundProductProfile")
                get("fundProductProfile").takeUnless(JsonElement::isJsonNull)?.let { product ->
                    product.requireObject("$path.fundProductProfile")
                        .requireFundProductProfile("$path.fundProductProfile")
                }
                nullableEnum<InstrumentType>("instrumentTypeOverride", "$path.instrumentTypeOverride")
                requireMember("behaviorProfile")
                requireMember("identityProfile")
                requiredEnumArray<IndustrySegment>("industrySegments", "$path.industrySegments")
            }
        }
        state.requiredObject("corporateFundamentals").entrySet().forEach { (stockId, element) ->
            val path = "state.corporateFundamentals[$stockId]"
            element.requireObject(path).apply {
                requireExactFields(CORPORATE_FUNDAMENTAL_FIELDS, path)
                requiredStrictString("stockId", "$path.stockId")
                requiredArray("quarters").forEachIndexed { index, reportElement ->
                    val reportPath = "$path.quarters[$index]"
                    reportElement.requireObject(reportPath).apply {
                        requireExactFields(QUARTERLY_FINANCIAL_REPORT_FIELDS, reportPath)
                        requiredStrictString("periodId", "$reportPath.periodId")
                        requiredStrictString("reportedAt", "$reportPath.reportedAt")
                        val revenue = requiredFiniteDouble("revenue", "$reportPath.revenue")
                        if (revenue < 0.0) {
                            throw JsonParseException("필드 '$reportPath.revenue'는 0 이상이어야 합니다.")
                        }
                        requiredFiniteDouble("netIncome", "$reportPath.netIncome")
                        val dilutedShares = requiredFiniteDouble(
                            "dilutedShares",
                            "$reportPath.dilutedShares",
                        )
                        if (dilutedShares <= 0.0) {
                            throw JsonParseException("필드 '$reportPath.dilutedShares'는 0보다 커야 합니다.")
                        }
                        nullableStrictString("sourceOccurrenceId", "$reportPath.sourceOccurrenceId")
                    }
                }
                val bookEquity = requiredFiniteDouble("bookEquity", "$path.bookEquity")
                if (bookEquity <= 0.0) {
                    throw JsonParseException("필드 '$path.bookEquity'는 0보다 커야 합니다.")
                }
                val openingEquity = requiredFiniteDouble("equityAtTtmStart", "$path.equityAtTtmStart")
                if (openingEquity <= 0.0) {
                    throw JsonParseException("필드 '$path.equityAtTtmStart'는 0보다 커야 합니다.")
                }
                requiredArray("appliedEarningsOccurrenceIds").forEachIndexed { index, idElement ->
                    idElement.requireStrictString("$path.appliedEarningsOccurrenceIds[$index]")
                }
                requiredStrictString("asOf", "$path.asOf")
            }
        }
        state.requiredObject("fundFinancialStates").entrySet().forEach { (stockId, element) ->
            val path = "state.fundFinancialStates[$stockId]"
            element.requireObject(path).apply {
                requireExactFields(FUND_FINANCIAL_STATE_FIELDS, path)
                requiredStrictString("stockId", "$path.stockId")
                listOf(
                    "navPerUnit",
                    "indicativeValuePerUnit",
                ).forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE) {
                        throw JsonParseException(
                            "필드 '$path.$field'는 ${MIN_FUND_REFERENCE_VALUE}와 " +
                                "${MAX_FUND_REFERENCE_VALUE} 사이여야 합니다.",
                        )
                    }
                }
                if (requiredFiniteDouble("unitsOrNotesOutstanding", "$path.unitsOrNotesOutstanding") <= 0.0) {
                    throw JsonParseException("필드 '$path.unitsOrNotesOutstanding'는 0보다 커야 합니다.")
                }
                requiredFiniteDouble("lastNetFlow", "$path.lastNetFlow")
                val accruedDistribution = requiredFiniteDouble(
                    "accruedDistributionPerUnit",
                    "$path.accruedDistributionPerUnit",
                )
                if (accruedDistribution !in 0.0..MAX_FUND_REFERENCE_VALUE) {
                    throw JsonParseException(
                        "필드 '$path.accruedDistributionPerUnit'는 0과 " +
                            "${MAX_FUND_REFERENCE_VALUE} 사이여야 합니다.",
                    )
                }
                requireUnitAdjustmentMarker(path)
                requiredStrictString("asOf", "$path.asOf")
            }
        }
        state.requiredObject("referencePortfolioStates").entrySet().forEach { (portfolioId, element) ->
            val path = "state.referencePortfolioStates[$portfolioId]"
            element.requireObject(path).apply {
                requireExactFields(REFERENCE_PORTFOLIO_STATE_FIELDS, path)
                requiredBoundedNonBlankString(
                    "portfolioId",
                    "$path.portfolioId",
                    MAX_REFERENCE_PORTFOLIO_ID_LENGTH,
                )
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                requireReferencePortfolioPositions("positions", "$path.positions")
                requireEquityMethodologyPathState(
                    "methodologyPathState",
                    "$path.methodologyPathState",
                )
                if (requiredLong("revision", "$path.revision") < 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0 이상이어야 합니다.")
                }
                val lastReconstitutionDate = requiredLocalDate(
                    "lastReconstitutionDate",
                    "$path.lastReconstitutionDate",
                )
                requiredLocalDate("lastRebalanceDate", "$path.lastRebalanceDate")
                val nextReconstitutionDate = nullableLocalDate(
                    "nextReconstitutionDate",
                    "$path.nextReconstitutionDate",
                )
                requiredLocalDate("nextRebalanceDate", "$path.nextRebalanceDate")
                val pendingSelectionDate = nullableLocalDate(
                    "pendingSelectionDate",
                    "$path.pendingSelectionDate",
                )
                val pendingSelectionIncumbentAssetIds = requireNullableReferenceAssetIdArray(
                    "pendingSelectionIncumbentAssetIds",
                    "$path.pendingSelectionIncumbentAssetIds",
                )
                val hasInvalidPendingSelection = if (nextReconstitutionDate == null) {
                    pendingSelectionDate != null || pendingSelectionIncumbentAssetIds != null
                } else {
                    (pendingSelectionDate == null) !=
                        (pendingSelectionIncumbentAssetIds == null) ||
                        pendingSelectionIncumbentAssetIds?.isEmpty() == true ||
                        pendingSelectionDate?.let { date ->
                            date <= lastReconstitutionDate || date >= nextReconstitutionDate
                        } == true
                }
                if (hasInvalidPendingSelection) {
                    throw JsonParseException(
                        "필드 '$path'의 대기 중 선택일 구성 스냅샷이 재구성 일정과 맞지 않습니다.",
                    )
                }
                val pendingPlans = requiredArray("pendingPlans")
                if (pendingPlans.size() > ReferencePortfolioState.MAX_PENDING_PLANS) {
                    throw JsonParseException(
                        "필드 '$path.pendingPlans'의 항목이 너무 많습니다.",
                    )
                }
                pendingPlans.forEachIndexed { index, planElement ->
                    val planPath = "$path.pendingPlans[$index]"
                    planElement.requireObject(planPath).apply {
                        requireExactFields(REFERENCE_PORTFOLIO_PLAN_FIELDS, planPath)
                        requiredBoundedNonBlankString(
                            "id",
                            "$planPath.id",
                            MAX_REFERENCE_LEDGER_ID_LENGTH,
                        )
                        requiredBoundedNonBlankString(
                            "portfolioId",
                            "$planPath.portfolioId",
                            MAX_REFERENCE_PORTFOLIO_ID_LENGTH,
                        )
                        requiredObject("benchmarkRef")
                            .requireBenchmarkRef("$planPath.benchmarkRef")
                        val kind = requiredEnum<ReferencePortfolioActionKind>("kind", "$planPath.kind")
                        val selectionDate = requiredLocalDate(
                            "selectionDate",
                            "$planPath.selectionDate",
                        )
                        requiredLocalDate("weightReferenceDate", "$planPath.weightReferenceDate")
                        val effectiveDate = requiredLocalDate(
                            "effectiveDate",
                            "$planPath.effectiveDate",
                        )
                        val selectionIncumbentAssetIds = requireNullableReferenceAssetIdArray(
                            "selectionIncumbentAssetIds",
                            "$planPath.selectionIncumbentAssetIds",
                        )
                        val selectionAvailabilityDate = nullableLocalDate(
                            "selectionAvailabilityDate",
                            "$planPath.selectionAvailabilityDate",
                        )
                        val hasSelectionBasis = selectionIncumbentAssetIds != null &&
                            selectionAvailabilityDate != null
                        if ((selectionIncumbentAssetIds == null) !=
                            (selectionAvailabilityDate == null) ||
                            hasSelectionBasis !=
                            (kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) ||
                            selectionAvailabilityDate?.let { date ->
                                date !in selectionDate..effectiveDate
                            } == true
                        ) {
                            throw JsonParseException(
                                "필드 '$planPath'의 정기 재구성 선택 근거가 행동 종류·일정과 맞지 않습니다.",
                            )
                        }
                        val positionAssetIds = requireReferencePortfolioPositions(
                            "positions",
                            "$planPath.positions",
                        )
                        val hasTransitionBaselineWeights = requireTransitionBaselineWeights(
                            "transitionBaselineWeights",
                            "$planPath.transitionBaselineWeights",
                            positionAssetIds,
                        )
                        if (hasTransitionBaselineWeights !=
                            (kind == ReferencePortfolioActionKind
                                .SCHEDULED_RECONSTITUTION_TRANSITION ||
                                kind == ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION)
                        ) {
                            throw JsonParseException(
                                "필드 '$planPath.transitionBaselineWeights'가 행동 종류와 맞지 않습니다.",
                            )
                        }
                        requireEquityMethodologyPathState(
                            "methodologyPathState",
                            "$planPath.methodologyPathState",
                        )
                        val addedAssetIds = requireReferenceAssetIdArray(
                            "addedAssetIds",
                            "$planPath.addedAssetIds",
                        )
                        requireReferenceAssetIdArray("removedAssetIds", "$planPath.removedAssetIds")
                        val corporateEventEffectiveDate = requireReferencePortfolioCorporateAction(
                            "corporateAction",
                            "$planPath.corporateAction",
                        )
                        val hasWeightReferenceMarketValues = requireWeightReferenceMarketValues(
                            "weightReferenceMarketValues",
                            "$planPath.weightReferenceMarketValues",
                            positionAssetIds,
                            requiresUnitSum =
                                kind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                        )
                        val requiresWeightReferenceMarketValues = when (kind) {
                            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION,
                            ReferencePortfolioActionKind.SCHEDULED_REWEIGHT,
                            ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT,
                            -> true
                            ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION_TRANSITION,
                            ReferencePortfolioActionKind.CORPORATE_ACTION_TRANSITION,
                            -> false
                            ReferencePortfolioActionKind.CONSTITUENT_MERGER,
                            ReferencePortfolioActionKind.SPIN_OFF_ADDITION,
                            ReferencePortfolioActionKind.TERMINAL_REMOVAL,
                            -> addedAssetIds.isNotEmpty() ||
                                corporateEventEffectiveDate?.let { eventDate ->
                                    effectiveDate > eventDate
                                } == true
                            ReferencePortfolioActionKind.EXTRAORDINARY_REMOVAL,
                            ReferencePortfolioActionKind.SPIN_OFF_REMOVAL,
                            -> false
                        }
                        if (hasWeightReferenceMarketValues != requiresWeightReferenceMarketValues) {
                            throw JsonParseException(
                                "필드 '$planPath.weightReferenceMarketValues'가 행동 종류와 맞지 않습니다.",
                            )
                        }
                    }
                }
                listOf("lastTurnoverRate", "estimatedAnnualIncomeYield").forEach { field ->
                    val rate = requiredFiniteDouble(field, "$path.$field")
                    if (rate !in 0.0..1.0) {
                        throw JsonParseException("필드 '$path.$field'는 0과 1 사이여야 합니다.")
                    }
                }
                requiredInstant("asOf", "$path.asOf")
                requiredEnum<ReferencePortfolioActionKind>(
                    "lastAppliedActionKind",
                    "$path.lastAppliedActionKind",
                )
            }
        }
        state.requiredArray("referencePortfolioLedger").forEachIndexed { index, element ->
            val path = "state.referencePortfolioLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(REFERENCE_PORTFOLIO_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredBoundedNonBlankString(
                    "portfolioId",
                    "$path.portfolioId",
                    MAX_REFERENCE_PORTFOLIO_ID_LENGTH,
                )
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                requiredEnum<ReferencePortfolioActionKind>("kind", "$path.kind")
                listOf("selectionDate", "weightReferenceDate", "effectiveDate").forEach { field ->
                    requiredLocalDate(field, "$path.$field")
                }
                listOf("addedAssetIds", "removedAssetIds").forEach { field ->
                    requireReferenceAssetIdArray(field, "$path.$field")
                }
                listOf("beforeCompositionHash", "afterCompositionHash").forEach { field ->
                    val hash = requiredStrictString(field, "$path.$field")
                    if (!REFERENCE_COMPOSITION_HASH.matches(hash)) {
                        throw JsonParseException("필드 '$path.$field'는 소문자 16자리 16진 해시여야 합니다.")
                    }
                }
                val turnoverRate = requiredFiniteDouble("turnoverRate", "$path.turnoverRate")
                if (turnoverRate !in 0.0..1.0) {
                    throw JsonParseException("필드 '$path.turnoverRate'는 0과 1 사이여야 합니다.")
                }
                val count = requiredInt("resultingConstituentCount")
                if (count !in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS) {
                    throw JsonParseException(
                        "필드 '$path.resultingConstituentCount'가 허용 범위를 벗어났습니다.",
                    )
                }
                if (requiredLong("revision", "$path.revision") <= 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0보다 커야 합니다.")
                }
                requireReferencePortfolioCorporateAction("corporateAction", "$path.corporateAction")
            }
        }
        state.requiredObject("dailyResetStates").entrySet().forEach { (productId, element) ->
            val path = "state.dailyResetStates[$productId]"
            element.requireObject(path).apply {
                requireExactFields(DAILY_RESET_STATE_FIELDS, path)
                requiredBoundedNonBlankString(
                    "productId",
                    "$path.productId",
                    MAX_DAILY_RESET_PRODUCT_ID_LENGTH,
                )
                requiredLocalDate("resetTradingDate", "$path.resetTradingDate")
                listOf(
                    "referenceLevelAtReset",
                    "navAtReset",
                    "currentReferenceLevel",
                    "currentNav",
                ).forEach { field ->
                    if (requiredFiniteDouble(field, "$path.$field") <= 0.0) {
                        throw JsonParseException("필드 '$path.$field'는 0보다 커야 합니다.")
                    }
                }
                requiredFiniteDouble(
                    "cumulativeCarryLogReturn",
                    "$path.cumulativeCarryLogReturn",
                )
                val exposure = requiredFiniteDouble("exposureNotional", "$path.exposureNotional")
                val collateral = requiredFiniteDouble("collateralBalance", "$path.collateralBalance")
                if (collateral < 0.0) {
                    throw JsonParseException("필드 '$path.collateralBalance'는 0 이상이어야 합니다.")
                }
                val lifecycle = requiredEnum<DailyResetLifecycle>("lifecycle", "$path.lifecycle")
                requireUnitAdjustmentMarker(path)
                requiredInstant("asOf", "$path.asOf")
                if (requiredLong("revision", "$path.revision") < 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0 이상이어야 합니다.")
                }
                if (lifecycle == DailyResetLifecycle.VALUE_EXHAUSTED &&
                    (requiredFiniteDouble("currentNav", "$path.currentNav") != DailyResetState.MIN_NAV ||
                        exposure != 0.0)
                ) {
                    throw JsonParseException("필드 '$path'의 가치소진 종단 상태가 유효하지 않습니다.")
                }
            }
        }
        state.requiredObject("optionStrategyStates").entrySet().forEach { (productId, element) ->
            val path = "state.optionStrategyStates[$productId]"
            element.requireObject(path).apply {
                requireExactFields(OPTION_STRATEGY_STATE_FIELDS, path)
                requiredBoundedNonBlankString(
                    "productId",
                    "$path.productId",
                    MAX_DAILY_RESET_PRODUCT_ID_LENGTH,
                )
                requiredEnum<OptionStrategyKind>("strategyKind", "$path.strategyKind")
                requiredEnum<OptionRollCalendar>("rollCalendar", "$path.rollCalendar")
                requireUnitAdjustmentMarker(path)
                listOf("currentReferenceLevel", "currentNav", "cycleReferenceLevel").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in OptionStrategyState.MIN_NAV..OptionStrategyState.MAX_NAV) {
                        throw JsonParseException("필드 '$path.$field'가 허용 양수 범위를 벗어났습니다.")
                    }
                }
                listOf(
                    "underlyingUnits",
                    "optionNotionalAtRoll",
                    "longCallUnits",
                    "shortCallUnits",
                    "longPutUnits",
                    "shortPutUnits",
                    "cycleGrossPremiumReceived",
                    "cycleGrossPremiumPaid",
                    "cycleImplementationCost",
                    "cumulativePremiumReceived",
                    "cumulativePremiumPaid",
                    "cumulativeImplementationCost",
                ).forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in 0.0..OptionStrategyState.MAX_NAV) {
                        throw JsonParseException("필드 '$path.$field'가 허용 비음수 범위를 벗어났습니다.")
                    }
                }
                listOf("cashBalance", "netOptionMark", "cumulativeSettlementCashFlow").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (kotlin.math.abs(value) > OptionStrategyState.MAX_NAV) {
                        throw JsonParseException("필드 '$path.$field'의 절댓값이 너무 큽니다.")
                    }
                }
                requiredLocalDate("cycleStartedOn", "$path.cycleStartedOn")
                val remainingDays = requiredInt("remainingTradingDays")
                if (remainingDays !in 0..MAX_OPTION_TENOR_TRADING_DAYS) {
                    throw JsonParseException("필드 '$path.remainingTradingDays'가 허용 범위를 벗어났습니다.")
                }
                val remainingYears = requiredFiniteDouble("remainingTimeYears", "$path.remainingTimeYears")
                if (remainingYears !in 0.0..MAX_OPTION_TIME_YEARS) {
                    throw JsonParseException("필드 '$path.remainingTimeYears'가 허용 범위를 벗어났습니다.")
                }
                nullableLocalDate("lastProcessedTradingDate", "$path.lastProcessedTradingDate")
                listOf(
                    "longCallUnits" to "longCallStrike",
                    "shortCallUnits" to "shortCallStrike",
                    "longPutUnits" to "longPutStrike",
                    "shortPutUnits" to "shortPutStrike",
                ).forEach { (unitsField, strikeField) ->
                    val units = requiredFiniteDouble(unitsField, "$path.$unitsField")
                    val strike = nullableFiniteDouble(strikeField, "$path.$strikeField")
                    if (units == 0.0 && strike != null ||
                        units > 0.0 && (strike == null || strike <= 0.0)
                    ) {
                        throw JsonParseException("필드 '$path.$unitsField/$strikeField'의 leg가 유효하지 않습니다.")
                    }
                }
                val lifecycle = requiredEnum<OptionStrategyLifecycle>("lifecycle", "$path.lifecycle")
                requiredInstant("asOf", "$path.asOf")
                if (requiredLong("revision", "$path.revision") < 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0 이상이어야 합니다.")
                }
                val hasInvalidLifecycleAccounts = when (lifecycle) {
                    OptionStrategyLifecycle.ACTIVE -> false
                    OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION ->
                        requiredFiniteDouble("optionNotionalAtRoll", "$path.optionNotionalAtRoll") != 0.0 ||
                            requiredFiniteDouble("netOptionMark", "$path.netOptionMark") != 0.0 ||
                            listOf("longCallUnits", "shortCallUnits", "longPutUnits", "shortPutUnits")
                                .any { field -> requiredFiniteDouble(field, "$path.$field") != 0.0 } ||
                            listOf(
                                "cycleGrossPremiumReceived",
                                "cycleGrossPremiumPaid",
                                "cycleImplementationCost",
                            ).any { field -> requiredFiniteDouble(field, "$path.$field") != 0.0 } ||
                            remainingDays != 0 || remainingYears != 0.0
                    OptionStrategyLifecycle.VALUE_EXHAUSTED ->
                        requiredFiniteDouble("currentNav", "$path.currentNav") != OptionStrategyState.MIN_NAV ||
                            requiredFiniteDouble("underlyingUnits", "$path.underlyingUnits") != 0.0 ||
                            requiredFiniteDouble("cashBalance", "$path.cashBalance") != OptionStrategyState.MIN_NAV ||
                            requiredFiniteDouble("optionNotionalAtRoll", "$path.optionNotionalAtRoll") != 0.0 ||
                            requiredFiniteDouble("netOptionMark", "$path.netOptionMark") != 0.0 ||
                            remainingDays != 0 || remainingYears != 0.0
                }
                if (hasInvalidLifecycleAccounts) {
                    throw JsonParseException("필드 '$path'의 옵션 가치소진 종단 상태가 유효하지 않습니다.")
                }
            }
        }
        state.requiredObject("cashCollateralizedPutSpreadStates").entrySet()
            .forEach { (productId, element) ->
                val path = "state.cashCollateralizedPutSpreadStates[$productId]"
                element.requireObject(path).apply {
                    requireExactFields(CASH_COLLATERALIZED_PUT_SPREAD_STATE_FIELDS, path)
                    val storedProductId = requiredBoundedNonBlankString(
                        "productId",
                        "$path.productId",
                        MAX_DAILY_RESET_PRODUCT_ID_LENGTH,
                    )
                    if (storedProductId != productId) {
                        throw JsonParseException("필드 '$path.productId'가 map 키와 다릅니다.")
                    }
                    requiredObject("cashBenchmarkRef").requireBenchmarkRef("$path.cashBenchmarkRef")
                    requireUnitAdjustmentMarker(path)
                    requiredObject("optionReference").apply {
                        val referencePath = "$path.optionReference"
                        requireExactFields(DAILY_RESET_REFERENCE_FIELDS, referencePath)
                        val kind = requiredEnum<DailyResetReferenceKind>("kind", "$referencePath.kind")
                        requireMember("benchmarkRef")
                        requireMember("instrumentId")
                        when (kind) {
                            DailyResetReferenceKind.BENCHMARK -> {
                                get("benchmarkRef").requireObject("$referencePath.benchmarkRef")
                                    .requireBenchmarkRef("$referencePath.benchmarkRef")
                                if (!get("instrumentId").isJsonNull) {
                                    throw JsonParseException(
                                        "필드 '$referencePath.instrumentId'는 null이어야 합니다.",
                                    )
                                }
                            }
                            DailyResetReferenceKind.INSTRUMENT -> {
                                if (!get("benchmarkRef").isJsonNull) {
                                    throw JsonParseException(
                                        "필드 '$referencePath.benchmarkRef'는 null이어야 합니다.",
                                    )
                                }
                                val instrumentId = requiredStrictString(
                                    "instrumentId",
                                    "$referencePath.instrumentId",
                                )
                                if (!DAILY_RESET_INSTRUMENT_ID.matches(instrumentId)) {
                                    throw JsonParseException(
                                        "필드 '$referencePath.instrumentId' 형식이 유효하지 않습니다.",
                                    )
                                }
                            }
                        }
                    }
                    requiredEnum<OptionRollCalendar>("rollCalendar", "$path.rollCalendar")
                    listOf(
                        "currentCashReferenceLevel",
                        "currentOptionReferenceLevel",
                        "cycleOptionReferenceLevel",
                    ).forEach { field ->
                        if (requiredFiniteDouble(field, "$path.$field") !in
                            CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..
                            CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL
                        ) {
                            throw JsonParseException("필드 '$path.$field'가 기준값 범위를 벗어났습니다.")
                        }
                    }
                    if (requiredFiniteDouble("currentNav", "$path.currentNav") !in
                        CashCollateralizedPutSpreadState.MIN_NAV..
                        CashCollateralizedPutSpreadState.MAX_NAV
                    ) {
                        throw JsonParseException("필드 '$path.currentNav'가 NAV 범위를 벗어났습니다.")
                    }
                    listOf(
                        "cashBalance",
                        "navAtRoll",
                        "optionNotionalAtRoll",
                        "maximumSettlementLossAtRoll",
                        "longPutUnits",
                        "shortPutUnits",
                        "cycleGrossPremiumReceived",
                        "cycleGrossPremiumPaid",
                        "cycleImplementationCost",
                        "cumulativePremiumReceived",
                        "cumulativePremiumPaid",
                        "cumulativeImplementationCost",
                    ).forEach { field ->
                        if (requiredFiniteDouble(field, "$path.$field") !in
                            0.0..MAX_CASH_PUT_SPREAD_AMOUNT
                        ) {
                            throw JsonParseException("필드 '$path.$field'가 비음수 금액 범위를 벗어났습니다.")
                        }
                    }
                    listOf("netOptionMark", "cumulativeSettlementCashFlow").forEach { field ->
                        if (kotlin.math.abs(requiredFiniteDouble(field, "$path.$field")) >
                            MAX_CASH_PUT_SPREAD_AMOUNT
                        ) {
                            throw JsonParseException("필드 '$path.$field'의 절댓값이 너무 큽니다.")
                        }
                    }
                    requiredLocalDate("cycleStartedOn", "$path.cycleStartedOn")
                    val remainingDays = requiredInt("remainingTradingDays")
                    val remainingYears = requiredFiniteDouble(
                        "remainingTimeYears",
                        "$path.remainingTimeYears",
                    )
                    if (remainingDays !in 0..MAX_OPTION_TENOR_TRADING_DAYS ||
                        remainingYears !in 0.0..MAX_OPTION_TIME_YEARS
                    ) {
                        throw JsonParseException("필드 '$path'의 잔존 tenor가 유효하지 않습니다.")
                    }
                    nullableLocalDate("lastProcessedTradingDate", "$path.lastProcessedTradingDate")
                    val longUnits = requiredFiniteDouble("longPutUnits", "$path.longPutUnits")
                    val shortUnits = requiredFiniteDouble("shortPutUnits", "$path.shortPutUnits")
                    val longStrike = nullableFiniteDouble("longPutStrike", "$path.longPutStrike")
                    val shortStrike = nullableFiniteDouble("shortPutStrike", "$path.shortPutStrike")
                    val lifecycle = requiredEnum<CashCollateralizedPutSpreadLifecycle>(
                        "lifecycle",
                        "$path.lifecycle",
                    )
                    requiredInstant("asOf", "$path.asOf")
                    val hasInvalidLifecycleAccounts = when (lifecycle) {
                        CashCollateralizedPutSpreadLifecycle.ACTIVE ->
                            longUnits <= 0.0 || shortUnits <= 0.0 || longStrike == null ||
                                shortStrike == null || longStrike <= 0.0 || shortStrike <= longStrike ||
                                remainingDays == 0
                        CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION ->
                            requiredFiniteDouble("navAtRoll", "$path.navAtRoll") != 0.0 ||
                                requiredFiniteDouble("optionNotionalAtRoll", "$path.optionNotionalAtRoll") != 0.0 ||
                                requiredFiniteDouble(
                                    "maximumSettlementLossAtRoll",
                                    "$path.maximumSettlementLossAtRoll",
                                ) != 0.0 ||
                                longUnits != 0.0 || shortUnits != 0.0 ||
                                longStrike != null || shortStrike != null || remainingDays != 0 ||
                                remainingYears != 0.0 ||
                                requiredFiniteDouble("netOptionMark", "$path.netOptionMark") != 0.0 ||
                                listOf(
                                    "cycleGrossPremiumReceived",
                                    "cycleGrossPremiumPaid",
                                    "cycleImplementationCost",
                                ).any { field -> requiredFiniteDouble(field, "$path.$field") != 0.0 }
                        CashCollateralizedPutSpreadLifecycle.VALUE_EXHAUSTED ->
                            requiredFiniteDouble("currentNav", "$path.currentNav") !=
                                CashCollateralizedPutSpreadState.MIN_NAV ||
                                requiredFiniteDouble("cashBalance", "$path.cashBalance") !=
                                CashCollateralizedPutSpreadState.MIN_NAV ||
                                longUnits != 0.0 || shortUnits != 0.0 ||
                                longStrike != null || shortStrike != null || remainingDays != 0 ||
                                remainingYears != 0.0 ||
                                requiredFiniteDouble("netOptionMark", "$path.netOptionMark") != 0.0
                    }
                    if (requiredLong("revision", "$path.revision") < 0L || hasInvalidLifecycleAccounts) {
                        throw JsonParseException("필드 '$path'의 lifecycle·leg·revision이 유효하지 않습니다.")
                    }
                }
            }
        state.requiredObject("etnStates").entrySet().forEach { (productId, element) ->
            val path = "state.etnStates[$productId]"
            element.requireObject(path).apply {
                requireExactFields(ETN_STATE_FIELDS, path)
                requiredBoundedNonBlankString("productId", "$path.productId", MAX_FUND_STRUCTURE_ID_LENGTH)
                if (requiredFiniteDouble("referenceLevel", "$path.referenceLevel") <= 0.0) {
                    throw JsonParseException("필드 '$path.referenceLevel'은 0보다 커야 합니다.")
                }
                listOf("feeAdjustedIndicativeValuePerNote", "accruedCouponPerNote").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in 0.0..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                val notes = requiredLong("notesOutstanding", "$path.notesOutstanding")
                if (notes !in 0L..MAX_FUND_STRUCTURE_EXACT_QUANTITY) {
                    throw JsonParseException("필드 '$path.notesOutstanding'가 허용 범위를 벗어났습니다.")
                }
                val creditSpread = requiredFiniteDouble("issuerCreditSpread", "$path.issuerCreditSpread")
                val hazard = requiredFiniteDouble("issuerHazardRate", "$path.issuerHazardRate")
                val recovery = requiredFiniteDouble("issuerRecoveryRate", "$path.issuerRecoveryRate")
                if (creditSpread !in 0.0..MAX_FUND_STRUCTURE_RATE ||
                    hazard !in 0.0..1.0 || recovery !in 0.0..1.0
                ) {
                    throw JsonParseException("필드 '$path'의 발행자 신용 상태가 유효하지 않습니다.")
                }
                val observations = requiredArray("indicativeValueObservationWindow")
                if (observations.size() > EtnState.MAX_OBSERVATIONS) {
                    throw JsonParseException("필드 '$path.indicativeValueObservationWindow'가 너무 깁니다.")
                }
                var previousDate: LocalDate? = null
                observations.forEachIndexed { index, observationElement ->
                    val observationPath = "$path.indicativeValueObservationWindow[$index]"
                    observationElement.requireObject(observationPath).apply {
                        requireExactFields(ETN_INDICATIVE_VALUE_OBSERVATION_FIELDS, observationPath)
                        val date = requiredLocalDate("observationDate", "$observationPath.observationDate")
                        val value = requiredFiniteDouble(
                            "indicativeValuePerNote",
                            "$observationPath.indicativeValuePerNote",
                        )
                        if (previousDate?.let { date <= it } == true ||
                            value !in 0.0..MAX_FUND_STRUCTURE_VALUE
                        ) {
                            throw JsonParseException("필드 '$observationPath'의 날짜·지표가치가 유효하지 않습니다.")
                        }
                        previousDate = date
                    }
                }
                val lifecycle = requiredEnum<EtnLifecycle>("lifecycle", "$path.lifecycle")
                val terminalEvent = nullableEnum<EtnCreditEvent>(
                    "terminalCreditEvent",
                    "$path.terminalCreditEvent",
                )
                requiredInstant("asOf", "$path.asOf")
                if (requiredLong("revision", "$path.revision") < 0L ||
                    lifecycle == EtnLifecycle.ACTIVE && terminalEvent != null ||
                    lifecycle == EtnLifecycle.SETTLED &&
                    (notes != 0L || terminalEvent == null ||
                        terminalEvent in setOf(EtnCreditEvent.NONE, EtnCreditEvent.HOLDER_REDEMPTION))
                ) {
                    throw JsonParseException("필드 '$path'의 ETN 생명주기·revision이 유효하지 않습니다.")
                }
            }
        }
        state.requiredArray("etnLedger").forEachIndexed { index, element ->
            val path = "state.etnLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(ETN_LEDGER_ENTRY_FIELDS, path)
                listOf("id", "productId").forEach { field ->
                    requiredBoundedNonBlankString(field, "$path.$field", MAX_FUND_STRUCTURE_ID_LENGTH)
                }
                requiredEnum<EtnLedgerKind>("kind", "$path.kind")
                requiredInstant("effectiveAt", "$path.effectiveAt")
                if (requiredLong("revision", "$path.revision") <= 0L ||
                    requiredInt("sequenceInBatch") !in 0..MAX_FUND_STRUCTURE_BATCH_ENTRIES
                ) {
                    throw JsonParseException("필드 '$path'의 revision·batch sequence가 유효하지 않습니다.")
                }
                requiredEnum<ReferenceCurrency>("settlementCurrency", "$path.settlementCurrency")
                listOf("referenceLevelBefore", "referenceLevelAfter").forEach { field ->
                    if (requiredFiniteDouble(field, "$path.$field") <= 0.0) {
                        throw JsonParseException("필드 '$path.$field'은 0보다 커야 합니다.")
                    }
                }
                listOf("indicativeValueBefore", "indicativeValueAfter").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in 0.0..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                listOf(
                    "notesOutstandingBefore",
                    "notesOutstandingAfter",
                    "notesIssued",
                    "notesCancelled",
                    "notesSettled",
                ).forEach { field ->
                    if (requiredLong(field, "$path.$field") !in
                        0L..MAX_FUND_STRUCTURE_EXACT_QUANTITY
                    ) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                if (requiredLong("notesDelta", "$path.notesDelta") !in
                    -MAX_FUND_STRUCTURE_EXACT_QUANTITY..MAX_FUND_STRUCTURE_EXACT_QUANTITY
                ) {
                    throw JsonParseException("필드 '$path.notesDelta'가 허용 범위를 벗어났습니다.")
                }
                listOf("cashPaidToNoteholders", "cashReceivedFromNoteholders").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in 0.0..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                requiredEnum<EtnCreditEvent>("contractEvent", "$path.contractEvent")
                val observations = requiredArray("settlementIndicativeValueObservations")
                if (observations.size() > EtnState.MAX_OBSERVATIONS) {
                    throw JsonParseException("필드 '$path.settlementIndicativeValueObservations'가 너무 깁니다.")
                }
                observations.forEachIndexed { observationIndex, observationElement ->
                    val observationPath =
                        "$path.settlementIndicativeValueObservations[$observationIndex]"
                    observationElement.requireObject(observationPath).apply {
                        requireExactFields(ETN_INDICATIVE_VALUE_OBSERVATION_FIELDS, observationPath)
                        requiredLocalDate("observationDate", "$observationPath.observationDate")
                        val value = requiredFiniteDouble(
                            "indicativeValuePerNote",
                            "$observationPath.indicativeValuePerNote",
                        )
                        if (value !in 0.0..MAX_FUND_STRUCTURE_VALUE) {
                            throw JsonParseException("필드 '$observationPath'의 지표가치가 유효하지 않습니다.")
                        }
                    }
                }
            }
        }
        state.requiredObject("closedEndFundStates").entrySet().forEach { (fundId, element) ->
            val path = "state.closedEndFundStates[$fundId]"
            element.requireObject(path).apply {
                requireExactFields(CLOSED_END_FUND_STATE_FIELDS, path)
                requiredBoundedNonBlankString("fundId", "$path.fundId", MAX_FUND_STRUCTURE_ID_LENGTH)
                listOf("grossAssets", "commonSharesOutstanding", "navPerCommonShare").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                listOf("debtLiability", "preferredShareLiability", "distributionReserve").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in 0.0..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                if (kotlin.math.abs(
                        requiredFiniteDouble(
                            "undistributedNetInvestmentIncome",
                            "$path.undistributedNetInvestmentIncome",
                        ),
                    ) > MAX_FUND_STRUCTURE_VALUE ||
                    requiredFiniteDouble("marketDiscountRate", "$path.marketDiscountRate") !in
                    -0.99..MAX_FUND_STRUCTURE_RATE
                ) {
                    throw JsonParseException("필드 '$path'의 UNII·시장 할인율이 유효하지 않습니다.")
                }
                requireUnitAdjustmentMarker(path)
                requiredInstant("asOf", "$path.asOf")
                if (requiredLong("revision", "$path.revision") < 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0 이상이어야 합니다.")
                }
            }
        }
        state.requiredArray("closedEndFundLedger").forEachIndexed { index, element ->
            val path = "state.closedEndFundLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(CLOSED_END_FUND_LEDGER_ENTRY_FIELDS, path)
                listOf("id", "fundId").forEach { field ->
                    requiredBoundedNonBlankString(field, "$path.$field", MAX_FUND_STRUCTURE_ID_LENGTH)
                }
                requiredEnum<ClosedEndFundLedgerKind>("kind", "$path.kind")
                requiredInstant("effectiveAt", "$path.effectiveAt")
                if (requiredLong("revision", "$path.revision") <= 0L ||
                    requiredInt("sequenceInBatch") !in 0..MAX_FUND_STRUCTURE_BATCH_ENTRIES
                ) {
                    throw JsonParseException("필드 '$path'의 revision·batch sequence가 유효하지 않습니다.")
                }
                requiredEnum<ReferenceCurrency>("settlementCurrency", "$path.settlementCurrency")
                requiredEnum<ClosedEndFundCapitalActionKind>(
                    "capitalActionKind",
                    "$path.capitalActionKind",
                )
                requiredEnum<ClosedEndFundFinancingActionKind>(
                    "financingActionKind",
                    "$path.financingActionKind",
                )
                listOf(
                    "grossAssetsDelta",
                    "commonSharesDelta",
                    "debtLiabilityDelta",
                    "preferredShareLiabilityDelta",
                    "externalCashFlow",
                ).forEach { field ->
                    if (kotlin.math.abs(requiredFiniteDouble(field, "$path.$field")) >
                        MAX_FUND_STRUCTURE_VALUE
                    ) {
                        throw JsonParseException("필드 '$path.$field'의 절댓값이 너무 큽니다.")
                    }
                }
                listOf(
                    "cashToCommonShareholders",
                    "netInvestmentIncomeDistribution",
                    "realizedGainDistribution",
                    "returnOfCapitalDistribution",
                ).forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in 0.0..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
                listOf("navPerShareBefore", "navPerShareAfter").forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value !in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE) {
                        throw JsonParseException("필드 '$path.$field'가 허용 범위를 벗어났습니다.")
                    }
                }
            }
        }
        state.requiredObject("fixedIncomeReferenceStates").entrySet().forEach { (referenceId, element) ->
            val path = "state.fixedIncomeReferenceStates[$referenceId]"
            element.requireObject(path).apply {
                requireExactFields(FIXED_INCOME_REFERENCE_STATE_FIELDS, path)
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                val positions = requiredArray("positions")
                if (positions.size() == 0 || positions.size() > FixedIncomeReferenceState.MAX_POSITIONS) {
                    throw JsonParseException("필드 '$path.positions'의 항목 수가 유효하지 않습니다.")
                }
                positions.forEachIndexed { index, position ->
                    val positionPath = "$path.positions[$index]"
                    position.requireObject(positionPath).requireFixedIncomePosition(positionPath)
                }
                listOf("nominalCurves", "realCurves").forEach { field ->
                    val curvesPath = "$path.$field"
                    val curves = requiredObject(field)
                    if (curves.size() > ReferenceCurrency.entries.size) {
                        throw JsonParseException("필드 '$curvesPath'의 통화 수가 너무 많습니다.")
                    }
                    curves.entrySet().forEach { (currencyName, curveElement) ->
                        val currency = ReferenceCurrency.entries.firstOrNull { it.name == currencyName }
                            ?: throw JsonParseException(
                                "필드 '$curvesPath'에 유효하지 않은 통화 '$currencyName'가 있습니다.",
                            )
                        val curvePath = "$curvesPath.$currencyName"
                        if (curveElement.requireObject(curvePath).requireYieldCurve(curvePath) != currency) {
                            throw JsonParseException("필드 '$curvePath.currency'가 map 키와 다릅니다.")
                        }
                    }
                }
                val spreadsPath = "$path.creditSpreads"
                val spreads = requiredObject("creditSpreads")
                if (spreads.size() > ReferenceCurrency.entries.size) {
                    throw JsonParseException("필드 '$spreadsPath'의 통화 수가 너무 많습니다.")
                }
                spreads.entrySet().forEach { (currencyName, spreadElement) ->
                    val currency = ReferenceCurrency.entries.firstOrNull { it.name == currencyName }
                        ?: throw JsonParseException(
                            "필드 '$spreadsPath'에 유효하지 않은 통화 '$currencyName'가 있습니다.",
                        )
                    val spreadPath = "$spreadsPath.$currencyName"
                    if (spreadElement.requireObject(spreadPath).requireCreditSpreads(spreadPath) != currency) {
                        throw JsonParseException("필드 '$spreadPath.currency'가 map 키와 다릅니다.")
                    }
                }
                if (requiredLong("revision", "$path.revision") < 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0 이상이어야 합니다.")
                }
                requiredInstant("asOf", "$path.asOf")
            }
        }
        val kofrIndexKeys = linkedSetOf<BenchmarkRef>()
        val kofrIndexEntries = state.requiredArray("kofrIndexStates")
        if (kofrIndexEntries.size() > KofrIndexBook.MAX_REFERENCES) {
            throw JsonParseException("필드 'state.kofrIndexStates'의 항목 수가 너무 많습니다.")
        }
        kofrIndexEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.kofrIndexStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!kofrIndexKeys.add(key)) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되었습니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(KOFR_INDEX_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                val publishedRate = requiredFiniteDouble(
                    "publishedRateAnnual",
                    "$valuePath.publishedRateAnnual",
                )
                if (publishedRate !in KofrIndexState.MIN_RATE..KofrIndexState.MAX_RATE) {
                    throw JsonParseException("필드 '$valuePath.publishedRateAnnual'가 허용 범위를 벗어났습니다.")
                }
                val publishedObservationDate = requiredLocalDate(
                    "publishedRateObservationDate",
                    "$valuePath.publishedRateObservationDate",
                )
                val indexLevel = requiredFiniteDouble("indexLevel", "$valuePath.indexLevel")
                if (indexLevel !in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE) {
                    throw JsonParseException("필드 '$valuePath.indexLevel'가 허용 양수 범위를 벗어났습니다.")
                }
                val indexPublicationDate = requiredLocalDate(
                    "indexPublicationDate",
                    "$valuePath.indexPublicationDate",
                )
                if (publishedObservationDate >= indexPublicationDate) {
                    throw JsonParseException("필드 '$valuePath'의 공표 관측일은 지수 공표일보다 앞서야 합니다.")
                }
                val pendingRate = nullableFiniteDouble(
                    "pendingRateAnnual",
                    "$valuePath.pendingRateAnnual",
                )
                val pendingObservationDate = nullableLocalDate(
                    "pendingRateObservationDate",
                    "$valuePath.pendingRateObservationDate",
                )
                if ((pendingRate == null) != (pendingObservationDate == null)) {
                    throw JsonParseException("필드 '$valuePath'의 대기 금리·관측일은 함께 존재하거나 null이어야 합니다.")
                }
                if (pendingRate != null && pendingRate !in KofrIndexState.MIN_RATE..KofrIndexState.MAX_RATE) {
                    throw JsonParseException("필드 '$valuePath.pendingRateAnnual'가 허용 범위를 벗어났습니다.")
                }
                if (pendingObservationDate != null && pendingObservationDate <= publishedObservationDate) {
                    throw JsonParseException("필드 '$valuePath.pendingRateObservationDate'는 공표 관측일보다 늦어야 합니다.")
                }
                if (requiredLong("revision", "$valuePath.revision") < 0L) {
                    throw JsonParseException("필드 '$valuePath.revision'은 0 이상이어야 합니다.")
                }
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }
        state.requiredArray("fixedIncomeRollLedger").forEachIndexed { index, element ->
            val path = "state.fixedIncomeRollLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(FIXED_INCOME_ROLL_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                val removed = requireFixedIncomeAssetIds("removedAssetIds", "$path.removedAssetIds")
                val added = requireFixedIncomeAssetIds("addedAssetIds", "$path.addedAssetIds")
                if (removed.size != added.size) {
                    throw JsonParseException("필드 '$path'의 편출·편입 항목 수가 다릅니다.")
                }
                requiredInstant("effectiveAt", "$path.effectiveAt")
                if (requiredLong("revision", "$path.revision") <= 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0보다 커야 합니다.")
                }
            }
        }
        val spotReferenceKeys = linkedSetOf<BenchmarkRef>()
        val spotStateEntries = state.requiredArray("commoditySpotReferenceStates")
        if (spotStateEntries.size() > MAX_COMMODITY_REFERENCE_COUNT) {
            throw JsonParseException("필드 'state.commoditySpotReferenceStates'의 항목 수가 너무 많습니다.")
        }
        spotStateEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.commoditySpotReferenceStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!spotReferenceKeys.add(key)) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되었습니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(COMMODITY_SPOT_REFERENCE_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                requiredEnum<CommodityAssetClass>("assetClass", "$valuePath.assetClass")
                requiredEnum<ReferenceCurrency>("baseCurrency", "$valuePath.baseCurrency")
                listOf("currentSpotLevel", "currentReferenceLevel").forEach { field ->
                    val value = requiredFiniteDouble(field, "$valuePath.$field")
                    if (value !in MIN_COMMODITY_REFERENCE_LEVEL..MAX_COMMODITY_REFERENCE_LEVEL) {
                        throw JsonParseException("필드 '$valuePath.$field'가 허용 양수 범위를 벗어났습니다.")
                    }
                }
                val spotWeight = requiredFiniteDouble("currentSpotWeight", "$valuePath.currentSpotWeight")
                val collateralWeight = requiredFiniteDouble(
                    "currentCollateralWeight",
                    "$valuePath.currentCollateralWeight",
                )
                if (spotWeight !in 0.0..1.0 || collateralWeight !in 0.0..1.0 ||
                    kotlin.math.abs(spotWeight + collateralWeight - 1.0) > COMMODITY_WEIGHT_EPSILON
                ) {
                    throw JsonParseException("필드 '$valuePath'의 현물·담보 비중이 유효하지 않습니다.")
                }
                val carry = requiredFiniteDouble("annualizedNetCarryRate", "$valuePath.annualizedNetCarryRate")
                if (carry !in MIN_COMMODITY_CARRY_RATE..MAX_COMMODITY_CARRY_RATE) {
                    throw JsonParseException("필드 '$valuePath.annualizedNetCarryRate'가 허용 범위를 벗어났습니다.")
                }
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }

        val futuresReferenceKeys = linkedSetOf<BenchmarkRef>()
        val futuresStateEntries = state.requiredArray("futuresReferenceStates")
        if (futuresStateEntries.size() > MAX_COMMODITY_REFERENCE_COUNT) {
            throw JsonParseException("필드 'state.futuresReferenceStates'의 항목 수가 너무 많습니다.")
        }
        futuresStateEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.futuresReferenceStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!futuresReferenceKeys.add(key) || key in spotReferenceKeys) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되거나 현물 상태와 겹칩니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(FUTURES_REFERENCE_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                requiredEnum<ReferenceCurrency>("baseCurrency", "$valuePath.baseCurrency")
                requiredEnum<FuturesPortfolioStyle>("portfolioStyle", "$valuePath.portfolioStyle")
                requiredEnum<FuturesAllocationMode>("allocationMode", "$valuePath.allocationMode")
                val level = requiredFiniteDouble("currentReferenceLevel", "$valuePath.currentReferenceLevel")
                if (level !in MIN_COMMODITY_REFERENCE_LEVEL..MAX_COMMODITY_REFERENCE_LEVEL) {
                    throw JsonParseException("필드 '$valuePath.currentReferenceLevel'가 허용 양수 범위를 벗어났습니다.")
                }
                val sleeves = requiredArray("sleeves")
                if (sleeves.size() !in 1..MAX_FUTURES_SLEEVES) {
                    throw JsonParseException("필드 '$valuePath.sleeves'의 항목 수가 유효하지 않습니다.")
                }
                sleeves.forEachIndexed { sleeveIndex, sleeveElement ->
                    val sleevePath = "$valuePath.sleeves[$sleeveIndex]"
                    sleeveElement.requireObject(sleevePath).apply {
                        requireExactFields(FUTURES_SLEEVE_STATE_FIELDS, sleevePath)
                        listOf("sleeveId", "curveId", "frontContractId", "nextContractId").forEach { field ->
                            requiredBoundedNonBlankString(field, "$sleevePath.$field", MAX_REFERENCE_ASSET_ID_LENGTH)
                        }
                        requiredEnum<CommodityAssetClass>("assetClass", "$sleevePath.assetClass")
                        requiredEnum<FuturesRollCalendar>("rollCalendar", "$sleevePath.rollCalendar")
                        requiredEnum<FuturesPriceReturnConvention>(
                            "priceReturnConvention",
                            "$sleevePath.priceReturnConvention",
                        )
                        nullableFiniteDouble("fixedPriceReturnNotional", "$sleevePath.fixedPriceReturnNotional")
                            ?.let { value ->
                                if (value <= 0.0 || value > MAX_COMMODITY_REFERENCE_LEVEL) {
                                    throw JsonParseException(
                                        "필드 '$sleevePath.fixedPriceReturnNotional'가 허용 범위를 벗어났습니다.",
                                    )
                                }
                            }
                        listOf("currentWeight", "targetWeight", "frontContractWeight", "nextContractWeight")
                            .forEach { field ->
                                val value = requiredFiniteDouble(field, "$sleevePath.$field")
                                if (value !in 0.0..1.0) {
                                    throw JsonParseException("필드 '$sleevePath.$field'가 비중 범위를 벗어났습니다.")
                                }
                            }
                        val spotLevel = requiredFiniteDouble("currentSpotLevel", "$sleevePath.currentSpotLevel")
                        if (spotLevel !in MIN_COMMODITY_REFERENCE_LEVEL..MAX_COMMODITY_REFERENCE_LEVEL) {
                            throw JsonParseException("필드 '$sleevePath.currentSpotLevel'가 허용 범위를 벗어났습니다.")
                        }
                        requiredLocalDate("frontExpiryDate", "$sleevePath.frontExpiryDate")
                        requiredLocalDate("nextExpiryDate", "$sleevePath.nextExpiryDate")
                        listOf("frontPrice", "nextPrice").forEach { field ->
                            val value = requiredFiniteDouble(field, "$sleevePath.$field")
                            if (value !in MIN_FUTURES_PRICE..MAX_FUTURES_PRICE) {
                                throw JsonParseException("필드 '$sleevePath.$field'가 가격 범위를 벗어났습니다.")
                            }
                        }
                        nullableLocalDate("lastRollTradingDate", "$sleevePath.lastRollTradingDate")
                    }
                }
                if (requiredLong("revision", "$valuePath.revision") < 0L) {
                    throw JsonParseException("필드 '$valuePath.revision'은 0 이상이어야 합니다.")
                }
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }

        state.requiredArray("futuresRollLedger").forEachIndexed { index, element ->
            val path = "state.futuresRollLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(FUTURES_ROLL_RECORD_FIELDS, path)
                listOf("id", "sleeveId", "fromContractId", "toContractId").forEach { field ->
                    requiredBoundedNonBlankString(field, "$path.$field", MAX_REFERENCE_LEDGER_ID_LENGTH)
                }
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                requiredLocalDate("rollTradingDate", "$path.rollTradingDate")
                listOf("transferredContractWeight", "frontWeightBefore", "frontWeightAfter")
                    .forEach { field ->
                        val value = requiredFiniteDouble(field, "$path.$field")
                        if (value !in 0.0..1.0) {
                            throw JsonParseException("필드 '$path.$field'가 비중 범위를 벗어났습니다.")
                        }
                    }
                requiredFiniteDouble("normalizedCurveBasis", "$path.normalizedCurveBasis")
                requiredBoolean("promotedDeferredToFront", "$path.promotedDeferredToFront")
                nullableStrictString("successorContractId", "$path.successorContractId")?.let { value ->
                    if (value.isBlank() || value.length > MAX_REFERENCE_ASSET_ID_LENGTH) {
                        throw JsonParseException("필드 '$path.successorContractId'의 길이가 유효하지 않습니다.")
                    }
                }
                requiredInstant("effectiveAt", "$path.effectiveAt")
                if (requiredLong("revision", "$path.revision") <= 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0보다 커야 합니다.")
                }
            }
        }
        state.requiredArray("futuresAllocationLedger").forEachIndexed { index, element ->
            val path = "state.futuresAllocationLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(FUTURES_ALLOCATION_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                listOf("weightsBefore", "weightsAfter").forEach { field ->
                    val weightsPath = "$path.$field"
                    val weights = requiredObject(field)
                    if (weights.size() !in 1..MAX_FUTURES_SLEEVES) {
                        throw JsonParseException("필드 '$weightsPath'의 항목 수가 유효하지 않습니다.")
                    }
                    weights.entrySet().forEach { (sleeveId, _) ->
                        if (sleeveId.isBlank() || sleeveId.length > MAX_REFERENCE_ASSET_ID_LENGTH) {
                            throw JsonParseException("필드 '$weightsPath'의 sleeve ID가 유효하지 않습니다.")
                        }
                        val weight = weights.requiredFiniteDouble(sleeveId, "$weightsPath.$sleeveId")
                        if (weight !in 0.0..1.0) {
                            throw JsonParseException("필드 '$weightsPath.$sleeveId'가 비중 범위를 벗어났습니다.")
                        }
                    }
                }
                requiredInstant("effectiveAt", "$path.effectiveAt")
                if (requiredLong("revision", "$path.revision") <= 0L) {
                    throw JsonParseException("필드 '$path.revision'은 0보다 커야 합니다.")
                }
            }
        }
        val equityReferenceKeys = linkedSetOf<BenchmarkRef>()
        val equityReferenceEntries = state.requiredArray("equityReferenceStates")
        if (equityReferenceEntries.size() > MAX_EQUITY_REFERENCE_COUNT) {
            throw JsonParseException("필드 'state.equityReferenceStates'의 항목 수가 너무 많습니다.")
        }
        equityReferenceEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.equityReferenceStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!equityReferenceKeys.add(key)) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되었습니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(EQUITY_REFERENCE_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                requiredEnum<EquityReferenceRegion>("region", "$valuePath.region")
                val countries = requiredArray("resolvedCountryCodes").mapIndexed { countryIndex, element ->
                    val countryPath = "$valuePath.resolvedCountryCodes[$countryIndex]"
                    element.requireStrictString(countryPath).also { country ->
                        if (!EQUITY_COUNTRY_CODE_PATTERN.matches(country)) {
                            throw JsonParseException("필드 '$countryPath'가 ISO alpha-2 형식이 아닙니다.")
                        }
                    }
                }
                if (countries.isEmpty() || countries.size > MAX_EQUITY_COUNTRY_CODES ||
                    countries != countries.sorted() || countries.distinct() != countries
                ) {
                    throw JsonParseException("필드 '$valuePath.resolvedCountryCodes'가 정렬 고유 집합이 아닙니다.")
                }
                nullableStrictString("themeId", "$valuePath.themeId")?.let { themeId ->
                    if (!EQUITY_THEME_ID_PATTERN.matches(themeId)) {
                        throw JsonParseException("필드 '$valuePath.themeId' 형식이 유효하지 않습니다.")
                    }
                }
                val positions = requiredArray("positions")
                if (positions.size() !in 1..EquityReferenceState.MAX_REPRESENTATIVE_BASKET_SIZE) {
                    throw JsonParseException("필드 '$valuePath.positions'의 항목 수가 유효하지 않습니다.")
                }
                positions.forEachIndexed { positionIndex, positionElement ->
                    val positionPath = "$valuePath.positions[$positionIndex]"
                    positionElement.requireObject(positionPath).apply {
                        requireExactFields(EQUITY_REFERENCE_POSITION_FIELDS, positionPath)
                        val assetId = requiredStrictString("assetId", "$positionPath.assetId")
                        if (!EQUITY_REFERENCE_ASSET_ID_PATTERN.matches(assetId)) {
                            throw JsonParseException("필드 '$positionPath.assetId' 형식이 유효하지 않습니다.")
                        }
                        requiredEnum<EquityReferenceRegion>("region", "$positionPath.region")
                        val countryCode = requiredStrictString("countryCode", "$positionPath.countryCode")
                        if (!EQUITY_COUNTRY_CODE_PATTERN.matches(countryCode)) {
                            throw JsonParseException("필드 '$positionPath.countryCode' 형식이 유효하지 않습니다.")
                        }
                        requiredEnum<MethodologyEquitySector>("sector", "$positionPath.sector")
                        listOf("weight", "targetWeight").forEach { field ->
                            val weight = requiredFiniteDouble(field, "$positionPath.$field")
                            if (weight !in MIN_EQUITY_REFERENCE_WEIGHT..1.0) {
                                throw JsonParseException("필드 '$positionPath.$field'가 비중 범위를 벗어났습니다.")
                            }
                        }
                        if (requiredInt("representedConstituentCount") !in 1..MAX_EQUITY_REPRESENTED_COUNT) {
                            throw JsonParseException(
                                "필드 '$positionPath.representedConstituentCount'가 범위를 벗어났습니다.",
                            )
                        }
                        val score = requiredFiniteDouble("selectionScore", "$positionPath.selectionScore")
                        val income = requiredFiniteDouble(
                            "indicatedAnnualDividendYield",
                            "$positionPath.indicatedAnnualDividendYield",
                        )
                        if (score !in -100.0..100.0 || income !in 0.0..1.0) {
                            throw JsonParseException("필드 '$positionPath'의 점수·배당수익률이 유효하지 않습니다.")
                        }
                        requiredLocalDate("enteredOn", "$positionPath.enteredOn")
                    }
                }
                requiredObject("factorExposure").apply {
                    requireExactFields(EQUITY_REFERENCE_FACTOR_EXPOSURE_FIELDS, "$valuePath.factorExposure")
                    val countryWeights = requiredObject("countryWeights")
                    if (countryWeights.size() !in 1..MAX_EQUITY_COUNTRY_CODES) {
                        throw JsonParseException("필드 '$valuePath.factorExposure.countryWeights'가 유효하지 않습니다.")
                    }
                    countryWeights.entrySet().forEach { (countryCode, _) ->
                        val path = "$valuePath.factorExposure.countryWeights.$countryCode"
                        val weight = countryWeights.requiredFiniteDouble(countryCode, path)
                        if (!EQUITY_COUNTRY_CODE_PATTERN.matches(countryCode) || weight !in 0.0..1.0) {
                            throw JsonParseException("필드 '$path'가 유효하지 않습니다.")
                        }
                    }
                    val sectorWeights = requiredObject("sectorWeights")
                    if (sectorWeights.size() !in 1..MethodologyEquitySector.entries.size) {
                        throw JsonParseException("필드 '$valuePath.factorExposure.sectorWeights'가 유효하지 않습니다.")
                    }
                    sectorWeights.entrySet().forEach { (sectorName, _) ->
                        if (MethodologyEquitySector.entries.none { it.name == sectorName } ||
                            sectorWeights.requiredFiniteDouble(
                                sectorName,
                                "$valuePath.factorExposure.sectorWeights.$sectorName",
                            ) !in 0.0..1.0
                        ) {
                            throw JsonParseException("필드 '$valuePath.factorExposure.sectorWeights.$sectorName'가 유효하지 않습니다.")
                        }
                    }
                    val styles = requiredEnumFiniteDoubleMap<EquityReferenceStyleFactor>(
                        "styleExposures",
                        "$valuePath.factorExposure.styleExposures",
                    )
                    if (styles.keys != EquityReferenceStyleFactor.entries.toSet() ||
                        styles.values.any { it !in -MAX_EQUITY_FACTOR_EXPOSURE..MAX_EQUITY_FACTOR_EXPOSURE }
                    ) {
                        throw JsonParseException("필드 '$valuePath.factorExposure.styleExposures'가 유효하지 않습니다.")
                    }
                    val idiosyncratic = requiredArray("idiosyncraticVolatilityWeights")
                    if (idiosyncratic.size() != EQUITY_IDIOSYNCRATIC_BUCKET_COUNT) {
                        throw JsonParseException(
                            "필드 '$valuePath.factorExposure.idiosyncraticVolatilityWeights'의 길이가 유효하지 않습니다.",
                        )
                    }
                    idiosyncratic.forEachIndexed { bucket, element ->
                        val path = "$valuePath.factorExposure.idiosyncraticVolatilityWeights[$bucket]"
                        val value = element.requireFiniteDouble(path)
                        if (value !in -MAX_EQUITY_IDIOSYNCRATIC_WEIGHT..MAX_EQUITY_IDIOSYNCRATIC_WEIGHT) {
                            throw JsonParseException("필드 '$path'가 허용 범위를 벗어났습니다.")
                        }
                    }
                    listOf("thematicExposure", "activeManagementExposure").forEach { field ->
                        if (requiredFiniteDouble(field, "$valuePath.factorExposure.$field") !in -1.0..1.0) {
                            throw JsonParseException("필드 '$valuePath.factorExposure.$field'가 범위를 벗어났습니다.")
                        }
                    }
                }
                if (requiredLong("revision", "$valuePath.revision") < 0L) {
                    throw JsonParseException("필드 '$valuePath.revision'은 0 이상이어야 합니다.")
                }
                listOf(
                    "lastSelectionDate",
                    "nextSelectionDate",
                    "lastReweightDate",
                    "nextReweightDate",
                ).forEach { field -> requiredLocalDate(field, "$valuePath.$field") }
                if (requiredFiniteDouble(
                        "estimatedAnnualIncomeYield",
                        "$valuePath.estimatedAnnualIncomeYield",
                    ) !in 0.0..1.0
                ) {
                    throw JsonParseException("필드 '$valuePath.estimatedAnnualIncomeYield'가 범위를 벗어났습니다.")
                }
                nullableLong("declaredTargetConstituentCount", "$valuePath.declaredTargetConstituentCount")
                    ?.let { count ->
                        if (count !in 1L..Int.MAX_VALUE.toLong()) {
                            throw JsonParseException(
                                "필드 '$valuePath.declaredTargetConstituentCount'가 범위를 벗어났습니다.",
                            )
                        }
                    }
                if (requiredInt("eligibleCandidateCount") < positions.size() ||
                    requiredInt("representativeBasketLimit") !in
                    1..EquityReferenceState.MAX_REPRESENTATIVE_BASKET_SIZE
                ) {
                    throw JsonParseException("필드 '$valuePath'의 후보·대표 basket 수가 유효하지 않습니다.")
                }
                listOf("profileFingerprint", "universeFingerprint", "compositionHash").forEach { field ->
                    if (!REFERENCE_COMPOSITION_HASH.matches(requiredStrictString(field, "$valuePath.$field"))) {
                        throw JsonParseException("필드 '$valuePath.$field'는 소문자 16자리 16진 해시여야 합니다.")
                    }
                }
                val modelVersion = requiredStrictString("universeModelVersion", "$valuePath.universeModelVersion")
                if (!EQUITY_UNIVERSE_VERSION_PATTERN.matches(modelVersion)) {
                    throw JsonParseException("필드 '$valuePath.universeModelVersion' 형식이 유효하지 않습니다.")
                }
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }
        state.requiredArray("equityReferenceLedger").forEachIndexed { index, element ->
            val path = "state.equityReferenceLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(EQUITY_REFERENCE_REBALANCE_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                val kind = requiredEnum<EquityReferenceActionKind>("kind", "$path.kind")
                requiredLocalDate("selectionDate", "$path.selectionDate")
                requiredInstant("effectiveAt", "$path.effectiveAt")
                val added = requireEquityReferenceAssetIds("addedAssetIds", "$path.addedAssetIds")
                val removed = requireEquityReferenceAssetIds("removedAssetIds", "$path.removedAssetIds")
                if (added.intersect(removed.toSet()).isNotEmpty() ||
                    kind == EquityReferenceActionKind.REWEIGHT && (added.isNotEmpty() || removed.isNotEmpty())
                ) {
                    throw JsonParseException("필드 '$path'의 편입·편출 집합이 action kind와 다릅니다.")
                }
                listOf("compositionHashBefore", "compositionHashAfter").forEach { field ->
                    if (!REFERENCE_COMPOSITION_HASH.matches(requiredStrictString(field, "$path.$field"))) {
                        throw JsonParseException("필드 '$path.$field'는 소문자 16자리 16진 해시여야 합니다.")
                    }
                }
                if (requiredFiniteDouble("turnoverRate", "$path.turnoverRate") !in 0.0..1.0 ||
                    requiredInt("resultingPositionCount") !in
                    1..EquityReferenceState.MAX_REPRESENTATIVE_BASKET_SIZE ||
                    requiredInt("representedConstituentCount") < requiredInt("resultingPositionCount") ||
                    requiredLong("revision", "$path.revision") <= 0L
                ) {
                    throw JsonParseException("필드 '$path'의 회전율·구성원 수·revision이 유효하지 않습니다.")
                }
            }
        }
        val fundOfFundsKeys = linkedSetOf<BenchmarkRef>()
        val fundOfFundsEntries = state.requiredArray("fundOfFundsStates")
        if (fundOfFundsEntries.size() > MAX_FUND_OF_FUNDS_REFERENCE_COUNT) {
            throw JsonParseException("필드 'state.fundOfFundsStates'의 항목 수가 너무 많습니다.")
        }
        fundOfFundsEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.fundOfFundsStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!fundOfFundsKeys.add(key)) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되었습니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(FUND_OF_FUNDS_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                requiredEnum<FundOfFundsUniverse>("universe", "$valuePath.universe")
                val positions = requiredArray("positions")
                if (positions.size() !in 1..FundOfFundsState.MAX_POSITIONS) {
                    throw JsonParseException("필드 '$valuePath.positions'의 항목 수가 유효하지 않습니다.")
                }
                positions.forEachIndexed { positionIndex, positionElement ->
                    val positionPath = "$valuePath.positions[$positionIndex]"
                    positionElement.requireObject(positionPath).apply {
                        requireExactFields(FUND_OF_FUNDS_POSITION_FIELDS, positionPath)
                        val candidateId = requiredStrictString("candidateFundId", "$positionPath.candidateFundId")
                        if (!FUND_OF_FUNDS_CANDIDATE_ID_PATTERN.matches(candidateId)) {
                            throw JsonParseException("필드 '$positionPath.candidateFundId' 형식이 유효하지 않습니다.")
                        }
                        requiredEnum<FundOfFundsCategory>("category", "$positionPath.category")
                        requiredObject("underlyingBenchmarkRef")
                            .requireBenchmarkRef("$positionPath.underlyingBenchmarkRef")
                        listOf("currentWeight", "targetWeight").forEach { field ->
                            if (requiredFiniteDouble(field, "$positionPath.$field") !in
                                MIN_FUND_OF_FUNDS_WEIGHT..1.0
                            ) {
                                throw JsonParseException("필드 '$positionPath.$field'가 비중 범위를 벗어났습니다.")
                            }
                        }
                        val ranges = mapOf(
                            "marketDiscountRate" to
                                (MIN_FUND_OF_FUNDS_DISCOUNT..MAX_FUND_OF_FUNDS_PREMIUM),
                            "indicatedAnnualDistributionYield" to (0.0..1.0),
                            "leverageRatio" to (0.0..MAX_FUND_OF_FUNDS_LEVERAGE),
                            "annualExpenseRate" to (0.0..MAX_FUND_OF_FUNDS_EXPENSE_RATE),
                            "annualResidualVolatility" to (0.0..MAX_FUND_OF_FUNDS_RESIDUAL_VOLATILITY),
                            "liquidityScore" to (0.0..1.0),
                            "selectionScore" to (-100.0..100.0),
                        )
                        ranges.forEach { (field, range) ->
                            if (requiredFiniteDouble(field, "$positionPath.$field") !in range) {
                                throw JsonParseException("필드 '$positionPath.$field'가 범위를 벗어났습니다.")
                            }
                        }
                        requiredLocalDate("enteredOn", "$positionPath.enteredOn")
                        requiredInstant("asOf", "$positionPath.asOf")
                    }
                }
                if (requiredLong("revision", "$valuePath.revision") < 0L) {
                    throw JsonParseException("필드 '$valuePath.revision'은 0 이상이어야 합니다.")
                }
                requiredLocalDate("bootstrapDate", "$valuePath.bootstrapDate")
                nullableLocalDate("lastSelectionDate", "$valuePath.lastSelectionDate")
                requiredLocalDate("nextSelectionDate", "$valuePath.nextSelectionDate")
                nullableLocalDate("lastReweightDate", "$valuePath.lastReweightDate")
                requiredLocalDate("nextReweightDate", "$valuePath.nextReweightDate")
                if (requiredFiniteDouble(
                        "estimatedAnnualIncomeYield",
                        "$valuePath.estimatedAnnualIncomeYield",
                    ) !in 0.0..1.0 ||
                    requiredInt("eligibleCandidateCount") < positions.size()
                ) {
                    throw JsonParseException("필드 '$valuePath'의 소득수익률·후보 수가 유효하지 않습니다.")
                }
                listOf("profileFingerprint", "universeFingerprint", "compositionHash").forEach { field ->
                    if (!REFERENCE_COMPOSITION_HASH.matches(requiredStrictString(field, "$valuePath.$field"))) {
                        throw JsonParseException("필드 '$valuePath.$field'는 소문자 16자리 16진 해시여야 합니다.")
                    }
                }
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }
        state.requiredArray("fundOfFundsRebalanceLedger").forEachIndexed { index, element ->
            val path = "state.fundOfFundsRebalanceLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(FUND_OF_FUNDS_REBALANCE_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                val kind = requiredEnum<FundOfFundsActionKind>("kind", "$path.kind")
                requiredLocalDate("effectiveDate", "$path.effectiveDate")
                requiredInstant("effectiveAt", "$path.effectiveAt")
                val added = requireFundOfFundsCandidateIds(
                    "addedCandidateFundIds",
                    "$path.addedCandidateFundIds",
                )
                val removed = requireFundOfFundsCandidateIds(
                    "removedCandidateFundIds",
                    "$path.removedCandidateFundIds",
                )
                if (added.intersect(removed.toSet()).isNotEmpty() ||
                    kind == FundOfFundsActionKind.REWEIGHT && (added.isNotEmpty() || removed.isNotEmpty())
                ) {
                    throw JsonParseException("필드 '$path'의 편입·편출 집합이 action kind와 다릅니다.")
                }
                listOf("compositionHashBefore", "compositionHashAfter").forEach { field ->
                    if (!REFERENCE_COMPOSITION_HASH.matches(requiredStrictString(field, "$path.$field"))) {
                        throw JsonParseException("필드 '$path.$field'는 소문자 16자리 16진 해시여야 합니다.")
                    }
                }
                if (requiredFiniteDouble("oneWayTurnoverRate", "$path.oneWayTurnoverRate") !in 0.0..1.0 ||
                    requiredInt("resultingFundCount") !in 1..FundOfFundsState.MAX_POSITIONS ||
                    requiredLong("revision", "$path.revision") <= 0L
                ) {
                    throw JsonParseException("필드 '$path'의 회전율·펀드 수·revision이 유효하지 않습니다.")
                }
            }
        }
        val alternativeRiskPremiaKeys = linkedSetOf<BenchmarkRef>()
        val alternativeRiskPremiaEntries = state.requiredArray("alternativeRiskPremiaStates")
        if (alternativeRiskPremiaEntries.size() > MAX_STRUCTURED_REFERENCE_COUNT) {
            throw JsonParseException("필드 'state.alternativeRiskPremiaStates'의 항목 수가 너무 많습니다.")
        }
        alternativeRiskPremiaEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.alternativeRiskPremiaStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!alternativeRiskPremiaKeys.add(key)) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되었습니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(ALTERNATIVE_RISK_PREMIA_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                val positions = requiredArray("positions")
                if (positions.size() !in 1..AlternativeRiskPremiaState.MAX_POSITIONS) {
                    throw JsonParseException("필드 '$valuePath.positions'의 항목 수가 유효하지 않습니다.")
                }
                positions.forEachIndexed { positionIndex, positionElement ->
                    val positionPath = "$valuePath.positions[$positionIndex]"
                    positionElement.requireObject(positionPath).apply {
                        requireExactFields(ALTERNATIVE_RISK_PREMIA_POSITION_FIELDS, positionPath)
                        val driverId = requiredStrictString("driverId", "$positionPath.driverId")
                        if (!COMPOSITE_MEMBER_ID_PATTERN.matches(driverId)) {
                            throw JsonParseException("필드 '$positionPath.driverId' 형식이 유효하지 않습니다.")
                        }
                        requiredEnum<AlternativeRiskPremiaStrategyFamily>(
                            "strategyFamily",
                            "$positionPath.strategyFamily",
                        )
                        listOf("currentSignedWeight", "targetSignedWeight").forEach { field ->
                            if (requiredFiniteDouble(field, "$positionPath.$field") !in -10.0..10.0) {
                                throw JsonParseException("필드 '$positionPath.$field'의 노출 범위가 유효하지 않습니다.")
                            }
                        }
                        if (requiredFiniteDouble("annualizedVariance", "$positionPath.annualizedVariance") !in
                            1e-8..25.0 ||
                            requiredFiniteDouble("trendSignal", "$positionPath.trendSignal") !in -100.0..100.0 ||
                            requiredFiniteDouble(
                                "sourceAnnualIncomeYield",
                                "$positionPath.sourceAnnualIncomeYield",
                            ) !in 0.0..1.0 ||
                            requiredFiniteDouble(
                                "sourceDurationYears",
                                "$positionPath.sourceDurationYears",
                            ) !in -50.0..50.0
                        ) {
                            throw JsonParseException("필드 '$positionPath'의 위험·소득·듀레이션 범위가 유효하지 않습니다.")
                        }
                        requiredFiniteDouble("lastSourceLogReturn", "$positionPath.lastSourceLogReturn")
                        requiredBoolean("sourceAvailable", "$positionPath.sourceAvailable")
                    }
                }
                if (requiredLong("revision", "$valuePath.revision") < 0L) {
                    throw JsonParseException("필드 '$valuePath.revision'은 0 이상이어야 합니다.")
                }
                nullableLocalDate("lastReweightDate", "$valuePath.lastReweightDate")
                nullableLocalDate("nextReweightDate", "$valuePath.nextReweightDate")
                requireStructuredReferenceMeasures(valuePath)
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }
        state.requiredArray("alternativeRiskPremiaRebalanceLedger")
            .forEachIndexed { index, element ->
                val path = "state.alternativeRiskPremiaRebalanceLedger[$index]"
                element.requireObject(path).apply {
                    requireExactFields(ALTERNATIVE_RISK_PREMIA_REBALANCE_RECORD_FIELDS, path)
                    requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                    requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                    val kind = requiredEnum<AlternativeRiskPremiaActionKind>("kind", "$path.kind")
                    requiredLocalDate("effectiveDate", "$path.effectiveDate")
                    requiredInstant("effectiveAt", "$path.effectiveAt")
                    val cashSubstituted = requireCompositeMemberIds(
                        "cashSubstitutedDriverIds",
                        "$path.cashSubstitutedDriverIds",
                    )
                    requireStructuredReferenceRecordMeasures(path)
                    if (kind == AlternativeRiskPremiaActionKind.REWEIGHT && cashSubstituted.isNotEmpty() ||
                        kind == AlternativeRiskPremiaActionKind.EXTRAORDINARY_SOURCE_TO_CASH &&
                        (cashSubstituted.isEmpty() ||
                            requiredFiniteDouble("turnoverRate", "$path.turnoverRate") != 0.0)
                    ) {
                        throw JsonParseException("필드 '$path'의 현금 대체 집합이 action kind와 다릅니다.")
                    }
                }
            }
        val compositeReferenceKeys = linkedSetOf<BenchmarkRef>()
        val compositeReferenceEntries = state.requiredArray("compositeReferenceStates")
        if (compositeReferenceEntries.size() > MAX_STRUCTURED_REFERENCE_COUNT) {
            throw JsonParseException("필드 'state.compositeReferenceStates'의 항목 수가 너무 많습니다.")
        }
        compositeReferenceEntries.forEachIndexed { index, entryElement ->
            val entryPath = "state.compositeReferenceStates[$index]"
            val entry = entryElement.takeIf(JsonElement::isJsonArray)?.asJsonArray
                ?: throw JsonParseException("필드 '$entryPath'은 [benchmarkRef, state] 쌍이어야 합니다.")
            if (entry.size() != 2) {
                throw JsonParseException("필드 '$entryPath'은 정확히 두 항목이어야 합니다.")
            }
            val key = entry[0].requireObject("$entryPath[0]").requireBenchmarkRef("$entryPath[0]")
            if (!compositeReferenceKeys.add(key)) {
                throw JsonParseException("필드 '$entryPath'의 benchmarkRef 키가 중복되었습니다.")
            }
            val valuePath = "$entryPath[1]"
            entry[1].requireObject(valuePath).apply {
                requireExactFields(COMPOSITE_REFERENCE_STATE_FIELDS, valuePath)
                if (requiredObject("benchmarkRef").requireBenchmarkRef("$valuePath.benchmarkRef") != key) {
                    throw JsonParseException("필드 '$valuePath.benchmarkRef'가 map 키와 다릅니다.")
                }
                val positions = requiredArray("positions")
                if (positions.size() !in 1..CompositeReferenceState.MAX_POSITIONS) {
                    throw JsonParseException("필드 '$valuePath.positions'의 항목 수가 유효하지 않습니다.")
                }
                positions.forEachIndexed { positionIndex, positionElement ->
                    val positionPath = "$valuePath.positions[$positionIndex]"
                    positionElement.requireObject(positionPath).apply {
                        requireExactFields(COMPOSITE_REFERENCE_POSITION_FIELDS, positionPath)
                        val sleeveId = requiredStrictString("sleeveId", "$positionPath.sleeveId")
                        if (!COMPOSITE_MEMBER_ID_PATTERN.matches(sleeveId)) {
                            throw JsonParseException("필드 '$positionPath.sleeveId' 형식이 유효하지 않습니다.")
                        }
                        requiredEnum<CompositeSleeveDirection>("direction", "$positionPath.direction")
                        listOf("currentWeightMagnitude", "targetWeightMagnitude").forEach { field ->
                            if (requiredFiniteDouble(field, "$positionPath.$field") !in 0.0..10.0) {
                                throw JsonParseException("필드 '$positionPath.$field'의 노출 범위가 유효하지 않습니다.")
                            }
                        }
                        if (requiredFiniteDouble("annualizedVariance", "$positionPath.annualizedVariance") !in
                            1e-8..25.0 ||
                            requiredFiniteDouble("trendSignal", "$positionPath.trendSignal") !in -100.0..100.0 ||
                            requiredFiniteDouble(
                                "sourceAnnualIncomeYield",
                                "$positionPath.sourceAnnualIncomeYield",
                            ) !in 0.0..1.0 ||
                            requiredFiniteDouble(
                                "sourceDurationYears",
                                "$positionPath.sourceDurationYears",
                            ) !in -50.0..50.0
                        ) {
                            throw JsonParseException("필드 '$positionPath'의 위험·소득·듀레이션 범위가 유효하지 않습니다.")
                        }
                        requiredFiniteDouble("lastSourceLogReturn", "$positionPath.lastSourceLogReturn")
                        requiredBoolean("sourceAvailable", "$positionPath.sourceAvailable")
                        nullableFiniteDouble(
                            "conditionalPrepaymentRateAnnual",
                            "$positionPath.conditionalPrepaymentRateAnnual",
                        )?.let { value ->
                            if (value !in 0.0..1.0) {
                                throw JsonParseException(
                                    "필드 '$positionPath.conditionalPrepaymentRateAnnual' 범위가 유효하지 않습니다.",
                                )
                            }
                        }
                    }
                }
                if (requiredLong("revision", "$valuePath.revision") < 0L) {
                    throw JsonParseException("필드 '$valuePath.revision'은 0 이상이어야 합니다.")
                }
                nullableLocalDate("lastSelectionDate", "$valuePath.lastSelectionDate")
                nullableLocalDate("nextSelectionDate", "$valuePath.nextSelectionDate")
                nullableLocalDate("lastReweightDate", "$valuePath.lastReweightDate")
                nullableLocalDate("nextReweightDate", "$valuePath.nextReweightDate")
                requireStructuredReferenceMeasures(valuePath)
                if (requiredFiniteDouble("lastMortgageRateAnnual", "$valuePath.lastMortgageRateAnnual") !in
                    0.0..1.0
                ) {
                    throw JsonParseException("필드 '$valuePath.lastMortgageRateAnnual' 범위가 유효하지 않습니다.")
                }
                requiredInstant("asOf", "$valuePath.asOf")
            }
        }
        state.requiredArray("compositeReferenceRebalanceLedger").forEachIndexed { index, element ->
            val path = "state.compositeReferenceRebalanceLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(COMPOSITE_REFERENCE_REBALANCE_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
                val kind = requiredEnum<CompositeReferenceActionKind>("kind", "$path.kind")
                requiredLocalDate("effectiveDate", "$path.effectiveDate")
                requiredInstant("effectiveAt", "$path.effectiveAt")
                val added = requireCompositeMemberIds("addedSleeveIds", "$path.addedSleeveIds")
                val removed = requireCompositeMemberIds("removedSleeveIds", "$path.removedSleeveIds")
                val cashSubstituted = requireCompositeMemberIds(
                    "cashSubstitutedSleeveIds",
                    "$path.cashSubstitutedSleeveIds",
                )
                if (added.intersect(removed.toSet()).isNotEmpty() || when (kind) {
                        CompositeReferenceActionKind.SELECTION -> cashSubstituted.isNotEmpty()
                        CompositeReferenceActionKind.REWEIGHT ->
                            added.isNotEmpty() || removed.isNotEmpty() || cashSubstituted.isNotEmpty()
                        CompositeReferenceActionKind.EXTRAORDINARY_SOURCE_TO_CASH ->
                            added.isNotEmpty() || removed.isNotEmpty() || cashSubstituted.isEmpty() ||
                                requiredFiniteDouble("turnoverRate", "$path.turnoverRate") != 0.0
                    }
                ) {
                    throw JsonParseException("필드 '$path'의 편입·편출 집합이 action kind와 다릅니다.")
                }
                requireStructuredReferenceRecordMeasures(path)
            }
        }
        state.requiredObject("pendingFundFlowRates").apply {
            entrySet().forEach { (stockId, _) ->
                val path = "state.pendingFundFlowRates[$stockId]"
                val rate = requiredFiniteDouble(stockId, path)
                if (rate !in -MAX_SAVED_FUND_FLOW_RATE..MAX_SAVED_FUND_FLOW_RATE) {
                    throw JsonParseException(
                        "필드 '$path'은 -${MAX_SAVED_FUND_FLOW_RATE}와 " +
                            "${MAX_SAVED_FUND_FLOW_RATE} 사이여야 합니다.",
                    )
                }
            }
        }
        state.requiredObject("macro").apply {
            requireExactFields(MACRO_ENVIRONMENT_FIELDS, "state.macro")
            requiredObject("fxRatesToKrw")
            requiredObject("previousFxRatesToKrw")
            listOf(
                "policyRate",
                "policyRateChange",
                "koreanPolicyRate",
                "koreanPolicyRateChange",
                "inflationRate",
                "inflationSurprise",
                "growthRate",
                "growthSurprise",
                "usdKrw",
                "previousUsdKrw",
                "riskSentiment",
                "volatilityRegime",
                "retailOrderFlow",
                "institutionalOrderFlow",
                "liquidityStress",
                "newsIntensity",
            ).forEach { field -> requiredFiniteDouble(field, "state.macro.$field") }
            requiredEnumFiniteDoubleMap<ReferenceCurrency>("fxRatesToKrw", "state.macro.fxRatesToKrw")
            requiredEnumFiniteDoubleMap<ReferenceCurrency>(
                "previousFxRatesToKrw",
                "state.macro.previousFxRatesToKrw",
            )
            requiredEnumFiniteDoubleMap<Market>("marketHourlyReturns", "state.macro.marketHourlyReturns")
            requiredEnumFiniteDoubleMap<Sector>("sectorHourlyReturns", "state.macro.sectorHourlyReturns")
            requiredEnumFiniteDoubleMap<Market>(
                "marketChangeFromPreviousClose",
                "state.macro.marketChangeFromPreviousClose",
            )
            requireMember("regionalEtfHourlyReturns")
            if (!get("regionalEtfHourlyReturns").isJsonNull) {
                requiredEnumFiniteDoubleMap<EtfExposureRegion>(
                    "regionalEtfHourlyReturns",
                    "state.macro.regionalEtfHourlyReturns",
                )
            }
            requiredInt("usCircuitBreakerLevel")
        }
        state.requiredArray("portfolioSnapshots").forEachIndexed { index, element ->
            val path = "state.portfolioSnapshots[$index]"
            element.requireObject(path).apply {
                requireExactFields(PORTFOLIO_SNAPSHOT_FIELDS, path)
                requiredInstant("timestamp", "$path.timestamp")
                if (requiredLong(
                        "accountingSequenceExclusiveUpperBound",
                        "$path.accountingSequenceExclusiveUpperBound",
                    ) < 0L
                ) {
                    throw JsonParseException(
                        "필드 '$path.accountingSequenceExclusiveUpperBound'는 0 이상이어야 합니다.",
                    )
                }
                val cash = requiredEnumFiniteDoubleMap<Currency>(
                    "cashByCurrency",
                    "$path.cashByCurrency",
                )
                if (cash.values.any { amount -> amount < 0.0 }) {
                    throw JsonParseException("필드 '$path.cashByCurrency'의 금액이 유효하지 않습니다.")
                }
                requiredArray("holdings")
                val receivables = requiredEnumFiniteDoubleMap<Currency>(
                    "distributionReceivableByCurrency",
                    "$path.distributionReceivableByCurrency",
                )
                if (receivables.values.any { amount ->
                        amount <= 0.0 || amount > MAX_FUND_REFERENCE_VALUE
                    }
                ) {
                    throw JsonParseException(
                        "필드 '$path.distributionReceivableByCurrency'의 금액이 유효하지 않습니다.",
                    )
                }
                val exchangeRates = requiredEnumFiniteDoubleMap<Currency>(
                    "exchangeRatesToKrw",
                    "$path.exchangeRatesToKrw",
                )
                if (exchangeRates.values.any { rate -> rate <= 0.0 }) {
                    throw JsonParseException("필드 '$path.exchangeRatesToKrw'의 환율이 유효하지 않습니다.")
                }
                listOf(
                    "initialCapitalKrw",
                    "cumulativeCommissionKrw",
                    "cumulativeTaxKrw",
                ).forEach { field ->
                    if (requiredFiniteDouble(field, "$path.$field") < 0.0) {
                        throw JsonParseException("필드 '$path.$field'는 0 이상이어야 합니다.")
                    }
                }
                requiredFiniteDouble("realizedProfitKrw", "$path.realizedProfitKrw")
                val holdingCosts = requiredObject("holdingCostBasisKrw")
                holdingCosts.entrySet().forEach { (stockId, _) ->
                    if (stockId.isBlank() ||
                        holdingCosts.requiredFiniteDouble(
                            stockId,
                            "$path.holdingCostBasisKrw.$stockId",
                        ) < 0.0
                    ) {
                        throw JsonParseException("필드 '$path.holdingCostBasisKrw'의 원가가 유효하지 않습니다.")
                    }
                }
            }
        }
        state.requiredArray("dailyStatistics").forEachIndexed { index, element ->
            val path = "state.dailyStatistics[$index]"
            element.requireObject(path).apply {
                requireExactFields(DAILY_PORTFOLIO_STAT_FIELDS, path)
                requiredLocalDate("date", "$path.date")
                listOf(
                    "totalAssetsKrw",
                    "cashValueKrw",
                    "stockValueKrw",
                    "dailyReturn",
                    "drawdown",
                    "benchmarkValue",
                    "usdKrw",
                ).forEach { field -> requiredFiniteDouble(field, "$path.$field") }
            }
        }
        state.requiredArray("orders").forEachIndexed { index, element ->
            val path = "state.orders[$index]"
            element.requireObject(path).requireExactFields(ORDER_FIELDS, path)
        }
        state.requiredArray("trades").forEachIndexed { index, element ->
            val path = "state.trades[$index]"
            element.requireObject(path).apply {
                requireExactFields(TRADE_FIELDS, path)
                required("settlementKind")
                required("accountingSequence")
            }
        }
        state.requiredArray("transactionCosts").forEachIndexed { index, element ->
            val path = "state.transactionCosts[$index]"
            element.requireObject(path).apply {
                requireExactFields(TRANSACTION_COST_FIELDS, path)
                requiredBoundedNonBlankString("tradeId", "$path.tradeId", 200)
                requiredBoundedNonBlankString("stockId", "$path.stockId", 200)
                requiredEnum<Market>("market", "$path.market")
                requiredInstant("paidAt", "$path.paidAt")
                requiredEnum<Currency>("currency", "$path.currency")
                listOf("commission", "saleTax").forEach { field ->
                    if (requiredFiniteDouble(field, "$path.$field") < 0.0) {
                        throw JsonParseException("필드 '$path.$field'는 0 이상이어야 합니다.")
                    }
                }
                if (requiredFiniteDouble("exchangeRateToKrw", "$path.exchangeRateToKrw") <= 0.0) {
                    throw JsonParseException("필드 '$path.exchangeRateToKrw'는 0보다 커야 합니다.")
                }
                requireMember("feeBreakdown")
                get("feeBreakdown").takeUnless(JsonElement::isJsonNull)?.requireObject(
                    "$path.feeBreakdown",
                )?.requireFeeBreakdown("$path.feeBreakdown")
                requireMember("taxBreakdown")
                get("taxBreakdown").takeUnless(JsonElement::isJsonNull)?.requireObject(
                    "$path.taxBreakdown",
                )?.requireTaxBreakdown("$path.taxBreakdown")
            }
        }
        state.requiredArray("realizedGains").forEachIndexed { index, element ->
            val path = "state.realizedGains[$index]"
            element.requireObject(path).apply {
                requireExactFields(REALIZED_GAIN_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("tradeId", "$path.tradeId", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredBoundedNonBlankString("stockId", "$path.stockId", MAX_REFERENCE_ASSET_ID_LENGTH)
                requiredEnum<Market>("market", "$path.market")
                requiredInstant("soldAt", "$path.soldAt")
                requiredLocalDate("settlementDate", "$path.settlementDate")
                listOf(
                    "quantity",
                    "proceeds",
                    "costBasis",
                    "commission",
                    "saleTax",
                    "exchangeRateToKrw",
                ).forEach { field ->
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value < 0.0 || field in setOf("quantity", "exchangeRateToKrw") && value == 0.0) {
                        throw JsonParseException("필드 '$path.$field'의 금액·수량이 유효하지 않습니다.")
                    }
                }
                requiredEnum<Currency>("currency", "$path.currency")
                requiredEnum<StockGainTaxTreatment>("taxTreatment", "$path.taxTreatment")
                requiredArray("assessmentNotes").forEachIndexed { noteIndex, noteElement ->
                    val notePath = "$path.assessmentNotes[$noteIndex]"
                    val note = noteElement.requireStrictString(notePath)
                    if (note.length > MAX_TAX_WARNING_LENGTH || note.any(Char::isISOControl)) {
                        throw JsonParseException("필드 '$notePath'의 길이·문자가 유효하지 않습니다.")
                    }
                }
                listOf(
                    "taxGrossProceedsKrw",
                    "taxCostBasisKrw",
                    "taxDirectSellingCostsKrw",
                    "taxableFinancialIncomeKrw",
                ).forEach { field ->
                    if (requiredLong(field, "$path.$field") < 0L) {
                        throw JsonParseException("필드 '$path.$field'는 음수일 수 없습니다.")
                    }
                }
                requiredLong("taxGainKrw", "$path.taxGainKrw")
            }
        }
        state.requiredObject("fifoCostBasisBook").apply {
            val path = "state.fifoCostBasisBook"
            requireExactFields(FIFO_COST_BASIS_BOOK_FIELDS, path)
            val lotIds = requiredArray("lots").mapIndexed { index, element ->
                val lotPath = "$path.lots[$index]"
                element.requireObject(lotPath).apply {
                    requireExactFields(TAX_LOT_FIELDS, lotPath)
                    requiredBoundedNonBlankString(
                        "lotId",
                        "$lotPath.lotId",
                        MAX_REFERENCE_LEDGER_ID_LENGTH,
                    )
                    requiredBoundedNonBlankString(
                        "stockId",
                        "$lotPath.stockId",
                        MAX_REFERENCE_ASSET_ID_LENGTH,
                    )
                    requiredLocalDate("acquiredOn", "$lotPath.acquiredOn")
                    if (requiredFiniteDouble(
                            "remainingQuantity",
                            "$lotPath.remainingQuantity",
                        ) <= 0.0
                    ) {
                        throw JsonParseException("필드 '$lotPath.remainingQuantity'는 양수여야 합니다.")
                    }
                    if (requiredLong(
                            "remainingCostBasisKrw",
                            "$lotPath.remainingCostBasisKrw",
                        ) < 0L
                    ) {
                        throw JsonParseException("필드 '$lotPath.remainingCostBasisKrw'는 음수일 수 없습니다.")
                    }
                }.requiredStrictString("lotId", "$lotPath.lotId")
            }
            if (lotIds.distinct().size != lotIds.size) {
                throw JsonParseException("필드 '$path.lots'의 lot ID가 중복되었습니다.")
            }
        }
        state.requiredObject("lastEvaluatedDistributionDateByStock").entrySet().forEach { (stockId, element) ->
            if (stockId.isBlank() || element.isJsonNull) {
                throw JsonParseException("분배 평가 날짜 맵의 종목 ID·날짜가 유효하지 않습니다.")
            }
            gson.fromJson(element, LocalDate::class.java)
                ?: throw JsonParseException("분배 평가 날짜 맵의 날짜를 복원할 수 없습니다.")
        }
        state.requiredArray("pendingDistributionEntitlements").forEachIndexed { index, element ->
            val path = "state.pendingDistributionEntitlements[$index]"
            element.requireObject(path).apply {
                requireExactFields(PENDING_DISTRIBUTION_ENTITLEMENT_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredBoundedNonBlankString("originId", "$path.originId", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredBoundedNonBlankString("stockId", "$path.stockId", MAX_REFERENCE_ASSET_ID_LENGTH)
                val exDate = requiredLocalDate("exDate", "$path.exDate")
                val recordDate = requiredLocalDate("recordDate", "$path.recordDate")
                val payDate = requiredLocalDate("payDate", "$path.payDate")
                if (exDate > recordDate || recordDate > payDate) {
                    throw JsonParseException(
                        "필드 '$path.exDate/$path.recordDate/$path.payDate'의 날짜 순서가 유효하지 않습니다.",
                    )
                }
                val currency = requiredEnum<Currency>("currency", "$path.currency")
                fun requiredPositiveFundValue(field: String): Double {
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value <= 0.0 || value > MAX_FUND_REFERENCE_VALUE) {
                        throw JsonParseException(
                            "필드 '$path.$field'는 0보다 크고 ${MAX_FUND_REFERENCE_VALUE} 이하여야 합니다.",
                        )
                    }
                    return value
                }
                val grossPerUnit = requiredPositiveFundValue("grossPerUnit")
                val entitledQuantity = requiredPositiveFundValue("entitledQuantity")
                val grossClaim = grossPerUnit * entitledQuantity
                val minorUnitFactor = if (currency == Currency.KRW) 1.0 else 100.0
                val roundedGrossClaim = kotlin.math.round(grossClaim * minorUnitFactor) /
                    minorUnitFactor
                if (!grossClaim.isFinite() || roundedGrossClaim <= 0.0 ||
                    roundedGrossClaim > MAX_FUND_REFERENCE_VALUE
                ) {
                    throw JsonParseException(
                        "필드 '$path.grossPerUnit/$path.entitledQuantity'의 gross 청구액이 유효하지 않습니다.",
                    )
                }
                val taxableCoverage = requiredFiniteDouble(
                    "taxableCoverageRatio",
                    "$path.taxableCoverageRatio",
                )
                if (taxableCoverage !in 0.0..1.0) {
                    throw JsonParseException("필드 '$path.taxableCoverageRatio'는 0과 1 사이여야 합니다.")
                }
            }
        }
        state.requiredArray("distributionEntitlementOrigins").forEachIndexed { index, element ->
            val path = "state.distributionEntitlementOrigins[$index]"
            element.requireObject(path).apply {
                requireExactFields(DISTRIBUTION_ENTITLEMENT_ORIGIN_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredBoundedNonBlankString("stockId", "$path.stockId", MAX_REFERENCE_ASSET_ID_LENGTH)
                requiredLocalDate("exDate", "$path.exDate")
                requiredInstant("establishedAt", "$path.establishedAt")
                requiredEnum<DistributionAmountBasis>("amountBasis", "$path.amountBasis")
                fun requiredFundValue(field: String, allowZero: Boolean): Double {
                    val value = requiredFiniteDouble(field, "$path.$field")
                    if (value < 0.0 || !allowZero && value == 0.0 || value > MAX_FUND_REFERENCE_VALUE) {
                        throw JsonParseException(
                            "필드 '$path.$field'가 허용된 펀드 기준값 범위를 벗어났습니다.",
                        )
                    }
                    return value
                }
                val grossPerUnit = requiredFundValue("grossPerUnit", allowZero = false)
                requiredFundValue("entitledQuantity", allowZero = false)
                val taxableCoverageRatio = requiredFiniteDouble(
                    "taxableCoverageRatio",
                    "$path.taxableCoverageRatio",
                )
                if (taxableCoverageRatio !in 0.0..1.0) {
                    throw JsonParseException("필드 '$path.taxableCoverageRatio'는 0과 1 사이여야 합니다.")
                }
                if (requiredFiniteDouble(
                        "taxBasisExchangeRateToKrw",
                        "$path.taxBasisExchangeRateToKrw",
                    ) <= 0.0
                ) {
                    throw JsonParseException("필드 '$path.taxBasisExchangeRateToKrw'는 양수여야 합니다.")
                }
                requiredFundValue("returnOfCapitalAmount", allowZero = true)
                if (requiredLong(
                        "excessReturnOfCapitalGainKrw",
                        "$path.excessReturnOfCapitalGainKrw",
                    ) < 0L
                ) {
                    throw JsonParseException(
                        "필드 '$path.excessReturnOfCapitalGainKrw'는 음수일 수 없습니다.",
                    )
                }
                requiredFundValue(
                    "accruedDistributionPerUnitBeforeEx",
                    allowZero = true,
                )
                val navBeforeEx = requiredFundValue("navPerUnitBeforeEx", allowZero = false)
                val navAfterEx = requiredFundValue("navPerUnitAfterEx", allowZero = false)
                if (navAfterEx.toBits() !=
                    (navBeforeEx - grossPerUnit).coerceAtLeast(MIN_FUND_REFERENCE_VALUE).toBits()
                ) {
                    throw JsonParseException("필드 '$path'의 분배 금액 source·NAV 전이가 유효하지 않습니다.")
                }
                val accountingSequence = requiredLong("accountingSequence", "$path.accountingSequence")
                if (accountingSequence <= 0L) {
                    throw JsonParseException("필드 '$path.accountingSequence'는 양수여야 합니다.")
                }
            }
        }
        state.requiredArray("dividendLedger").forEachIndexed { index, element ->
            val path = "state.dividendLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(DIVIDEND_LEDGER_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredBoundedNonBlankString("stockId", "$path.stockId", MAX_REFERENCE_ASSET_ID_LENGTH)
                requiredLocalDate("exDate", "$path.exDate")
                requiredLocalDate("recordDate", "$path.recordDate")
                requiredInstant("paidAt", "$path.paidAt")
                requiredEnum<Currency>("currency", "$path.currency")
                listOf(
                    "grossPerUnit",
                    "entitledQuantity",
                    "grossAmount",
                    "withholdingTax",
                    "netAmount",
                    "exchangeRateToKrw",
                    "taxableIncomeAmount",
                    "returnOfCapitalAmount",
                ).forEach { field -> requiredFiniteDouble(field, "$path.$field") }
                requiredLong("excessReturnOfCapitalGainKrw", "$path.excessReturnOfCapitalGainKrw")
                requiredLong("accountingSequence", "$path.accountingSequence")
                requiredObject("taxBreakdown").requireTaxBreakdown("$path.taxBreakdown")
            }
        }
        state.requiredArray("foreignExchangeLedger").forEachIndexed { index, element ->
            val path = "state.foreignExchangeLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(FOREIGN_EXCHANGE_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredInstant("executedAt", "$path.executedAt")
                val from = requiredEnum<Currency>("fromCurrency", "$path.fromCurrency")
                val to = requiredEnum<Currency>("toCurrency", "$path.toCurrency")
                if (from == to) throw JsonParseException("필드 '$path'의 환전 통화쌍이 유효하지 않습니다.")
                listOf("sourceAmount", "receivedAmount", "usdKrwRate").forEach { field ->
                    if (requiredFiniteDouble(field, "$path.$field") <= 0.0) {
                        throw JsonParseException("필드 '$path.$field'는 유한한 양수여야 합니다.")
                    }
                }
                if (requiredFiniteDouble("spreadCostKrw", "$path.spreadCostKrw") < 0.0) {
                    throw JsonParseException("필드 '$path.spreadCostKrw'는 음수일 수 없습니다.")
                }
                requiredBoolean("automatic", "$path.automatic")
                if (requiredLong("accountingSequence", "$path.accountingSequence") <= 0L) {
                    throw JsonParseException("필드 '$path.accountingSequence'는 양수여야 합니다.")
                }
            }
        }
        state.requiredArray("cashAdjustmentLedger").forEachIndexed { index, element ->
            val path = "state.cashAdjustmentLedger[$index]"
            element.requireObject(path).apply {
                requireExactFields(CASH_ADJUSTMENT_RECORD_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                requiredInstant("adjustedAt", "$path.adjustedAt")
                requiredEnum<Currency>("currency", "$path.currency")
                listOf("balanceBefore", "balanceAfter").forEach { field ->
                    if (requiredFiniteDouble(field, "$path.$field") < 0.0) {
                        throw JsonParseException("필드 '$path.$field'는 음수일 수 없습니다.")
                    }
                }
                val reason = requiredBoundedNonBlankString("reason", "$path.reason", 64)
                if (reason != "DEBUG_SET_CASH") {
                    throw JsonParseException("필드 '$path.reason'은 지원하는 디버그 조정 사유가 아닙니다.")
                }
                if (requiredLong("accountingSequence", "$path.accountingSequence") <= 0L) {
                    throw JsonParseException("필드 '$path.accountingSequence'는 양수여야 합니다.")
                }
            }
        }
        state.requiredArray("taxPaymentNotices").forEachIndexed { index, element ->
            val path = "state.taxPaymentNotices[$index]"
            element.requireObject(path).apply {
                requireExactFields(TAX_PAYMENT_NOTICE_FIELDS, path)
                requiredBoundedNonBlankString("id", "$path.id", MAX_REFERENCE_LEDGER_ID_LENGTH)
                val taxYear = requiredInt("taxYear")
                if (taxYear !in 2026..2040) {
                    throw JsonParseException("필드 '$path.taxYear'가 지원 세무 연도를 벗어났습니다.")
                }
                requiredLocalDate("dueDate", "$path.dueDate")
                if (requiredEnum<Currency>("currency", "$path.currency") != Currency.KRW) {
                    throw JsonParseException("필드 '$path.currency'는 KRW여야 합니다.")
                }
                if (requiredLong("amountKrw", "$path.amountKrw") <= 0L) {
                    throw JsonParseException("필드 '$path.amountKrw'는 양수여야 합니다.")
                }
                val status = requiredEnum<TaxLiabilityStatus>("status", "$path.status")
                val paidAt = nullableInstant("paidAt", "$path.paidAt")
                val accountingSequence = nullableLong(
                    "accountingSequence",
                    "$path.accountingSequence",
                )
                if (status == TaxLiabilityStatus.PAID) {
                    if (paidAt == null || accountingSequence == null || accountingSequence <= 0L) {
                        throw JsonParseException(
                            "필드 '$path'의 납부 시각·회계 순번이 PAID 상태와 맞지 않습니다.",
                        )
                    }
                } else if (paidAt != null || accountingSequence != null) {
                    throw JsonParseException(
                        "필드 '$path'의 미납 상태에는 납부 시각·회계 순번이 없어야 합니다.",
                    )
                }
                requiredBoundedNonBlankString("message", "$path.message", MAX_TAX_TEXT_LENGTH)
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
                            requireExactFields(
                                CORPORATE_ACTION_NEWS_REFERENCE_FIELDS,
                                "$path[$index].corporateActionReference",
                            )
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
                            nullableEnum<CorporateActionCancellationReason>(
                                "cancellationReason",
                                "$path[$index].corporateActionReference.cancellationReason",
                            )
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

    private fun JsonObject.requireBenchmarkRef(path: String): BenchmarkRef {
        requireExactFields(BENCHMARK_REF_FIELDS, path)
        val benchmarkId = requiredStrictString("benchmarkId", "$path.benchmarkId")
        val version = requiredInt("version")
        return try {
            BenchmarkRef(benchmarkId = benchmarkId, version = version)
        } catch (error: IllegalArgumentException) {
            throw JsonParseException("필드 '$path'의 벤치마크 참조가 유효하지 않습니다.", error)
        }
    }

    /** 종목팩 파서와 같은 exact field·enum·수치 경계를 저장 JSON에도 적용한다. */
    private fun JsonObject.requireFundProductProfile(path: String) {
        requireExactFields(FUND_PRODUCT_PROFILE_FIELDS, path)
        requiredObject("benchmarkRef").requireBenchmarkRef("$path.benchmarkRef")
        val replicationMode = requiredEnum<FundReplicationMode>(
            "replicationMode",
            "$path.replicationMode",
        )
        requiredEnum<FundReturnVariant>("returnVariant", "$path.returnVariant")
        val legalStructure = requiredEnum<FundLegalStructure>(
            "legalStructure",
            "$path.legalStructure",
        )
        requiredEnum<FundReferenceExposure>("referenceExposure", "$path.referenceExposure")
        val transforms = requiredEnumArray<FundReturnTransform>(
            "returnTransforms",
            "$path.returnTransforms",
        )
        if (transforms.isEmpty() || transforms.distinct().size != transforms.size ||
            transforms != transforms.sortedBy(FundReturnTransform::ordinal) ||
            FundReturnTransform.PLAIN in transforms && transforms.size != 1
        ) {
            throw JsonParseException("필드 '$path.returnTransforms'의 정렬·중복·조합이 유효하지 않습니다.")
        }
        nullableFiniteDouble(
            "trackingErrorAnnualVolatility",
            "$path.trackingErrorAnnualVolatility",
        )?.let { value ->
            if (value !in 0.0..1.0) {
                throw JsonParseException(
                    "필드 '$path.trackingErrorAnnualVolatility'는 0과 1 사이여야 합니다.",
                )
            }
        }
        requireMember("operationProfile")
        get("operationProfile").takeUnless(JsonElement::isJsonNull)?.let { element ->
            val operationPath = "$path.operationProfile"
            element.requireObject(operationPath).apply {
                requireExactFields(FUND_OPERATION_PROFILE_FIELDS, operationPath)
                val managementStyle = requiredEnum<FundManagementStyle>(
                    "managementStyle",
                    "$operationPath.managementStyle",
                )
                val syntheticSwapFunding = nullableEnum<SyntheticSwapFunding>(
                    "syntheticSwapFunding",
                    "$operationPath.syntheticSwapFunding",
                )
                val activeReturnSupport = requiredEnum<ActiveReturnModelSupport>(
                    "activeReturnModelSupport",
                    "$operationPath.activeReturnModelSupport",
                )
                val hasActiveSyntheticSwapParameters =
                    !get("activeSyntheticSwapModelParameters").isJsonNull
                if (hasActiveSyntheticSwapParameters) {
                    val modelPath = "$operationPath.activeSyntheticSwapModelParameters"
                    getAsJsonObject("activeSyntheticSwapModelParameters").apply {
                        requireExactFields(ACTIVE_SYNTHETIC_SWAP_MODEL_PARAMETER_FIELDS, modelPath)
                        val assumptionId = requiredStrictString("assumptionId", "$modelPath.assumptionId")
                        if (!OPTION_ASSUMPTION_ID.matches(assumptionId)) {
                            throw JsonParseException("필드 '$modelPath.assumptionId'의 형식이 유효하지 않습니다.")
                        }
                        val alphaMean = requiredFiniteDouble(
                            "activeAlphaAnnualMean",
                            "$modelPath.activeAlphaAnnualMean",
                        )
                        val valuesInUnitInterval = listOf(
                            "activeAlphaAnnualVolatility",
                            "annualSwapFundingSpread",
                            "counterpartyDefaultHazardRateAnnual",
                            "counterpartyRecoveryRate",
                            "counterpartyExposureFraction",
                        ).all { field -> requiredFiniteDouble(field, "$modelPath.$field") in 0.0..1.0 }
                        if (alphaMean !in -1.0..1.0 || !valuesInUnitInterval) {
                            throw JsonParseException("필드 '$modelPath'의 연율·회수율·노출 가정이 유효하지 않습니다.")
                        }
                    }
                }
                val provenance = requiredEnum<FundOperationProvenance>(
                    "provenance",
                    "$operationPath.provenance",
                )
                val sourceElements = requiredArray("officialSourceUrls")
                if (sourceElements.size() > FundOperationProfile.MAX_OFFICIAL_SOURCE_URLS) {
                    throw JsonParseException("필드 '$operationPath.officialSourceUrls'의 항목이 너무 많습니다.")
                }
                val sourceUrls = sourceElements.mapIndexed { index, sourceElement ->
                    sourceElement.requireStrictString("$operationPath.officialSourceUrls[$index]").also { url ->
                        requireSaveHttpsUrl(url, "$operationPath.officialSourceUrls[$index]")
                    }
                }
                if (sourceUrls != sourceUrls.sorted() || sourceUrls.distinct().size != sourceUrls.size) {
                    throw JsonParseException(
                        "필드 '$operationPath.officialSourceUrls'는 정렬된 중복 없는 URL이어야 합니다.",
                    )
                }
                val managementValid = when (managementStyle) {
                    FundManagementStyle.PASSIVE ->
                        activeReturnSupport == ActiveReturnModelSupport.NOT_APPLICABLE &&
                            !hasActiveSyntheticSwapParameters
                    FundManagementStyle.ACTIVE -> when (activeReturnSupport) {
                        ActiveReturnModelSupport.UNMODELED -> !hasActiveSyntheticSwapParameters
                        ActiveReturnModelSupport.DETERMINISTIC_ASSUMPTION ->
                            hasActiveSyntheticSwapParameters &&
                                syntheticSwapFunding == SyntheticSwapFunding.FULLY_FUNDED
                        ActiveReturnModelSupport.NOT_APPLICABLE -> false
                    }
                }
                val provenanceValid = when (provenance) {
                    FundOperationProvenance.VERIFIED_PRODUCT_DISCLOSURE -> sourceUrls.isNotEmpty()
                    FundOperationProvenance.UNVERIFIED -> sourceUrls.isEmpty()
                }
                val structureValid =
                    (replicationMode == FundReplicationMode.DERIVATIVE_SYNTHETIC) ==
                    (syntheticSwapFunding != null) &&
                    (syntheticSwapFunding == null || legalStructure == FundLegalStructure.OPEN_END_ETF) &&
                    (replicationMode != FundReplicationMode.ACTIVE_MANAGEMENT ||
                        managementStyle == FundManagementStyle.ACTIVE)
                if (!managementValid || !provenanceValid || !structureValid) {
                    throw JsonParseException("필드 '$operationPath'의 운용·합성 스왑 구조가 유효하지 않습니다.")
                }
            }
        }
        requireMember("dailyResetTerms")
        val hasDailyResetTerms = !get("dailyResetTerms").isJsonNull
        if (hasDailyResetTerms) {
            get("dailyResetTerms").requireObject("$path.dailyResetTerms")
                .requireDailyResetTerms("$path.dailyResetTerms")
        }
        val hasDailyResetTransform =
            FundReturnTransform.DAILY_LEVERAGED in transforms ||
                FundReturnTransform.DAILY_INVERSE in transforms
        if (hasDailyResetTransform != hasDailyResetTerms) {
            throw JsonParseException("필드 '$path'의 일일 reset 변환과 약관이 일치하지 않습니다.")
        }
        requireMember("etnProductTerms")
        requireMember("etnIssuerCreditModelParameters")
        requireMember("closedEndFundTerms")
        requireMember("closedEndFundMarketModelParameters")
        requireMember("optionStrategyTerms")
        requireMember("cashCollateralizedPutSpreadTerms")
        val hasEtnTerms = !get("etnProductTerms").isJsonNull
        val hasEtnCreditModel = !get("etnIssuerCreditModelParameters").isJsonNull
        val hasClosedEndFundTerms = !get("closedEndFundTerms").isJsonNull
        val hasClosedEndFundMarketModel = !get("closedEndFundMarketModelParameters").isJsonNull
        val hasCashCollateralizedPutSpreadTerms =
            !get("cashCollateralizedPutSpreadTerms").isJsonNull
        val optionStrategyKind = if (get("optionStrategyTerms").isJsonNull) {
            null
        } else {
            get("optionStrategyTerms").requireObject("$path.optionStrategyTerms")
                .requireOptionStrategyTerms("$path.optionStrategyTerms")
        }
        if (hasCashCollateralizedPutSpreadTerms) {
            get("cashCollateralizedPutSpreadTerms")
                .requireObject("$path.cashCollateralizedPutSpreadTerms")
                .requireCashCollateralizedPutSpreadTerms(
                    "$path.cashCollateralizedPutSpreadTerms",
                )
        }
        if (hasEtnTerms) {
            get("etnProductTerms").requireObject("$path.etnProductTerms")
                .requireEtnProductTerms("$path.etnProductTerms")
        }
        if (hasEtnCreditModel) {
            get("etnIssuerCreditModelParameters")
                .requireObject("$path.etnIssuerCreditModelParameters")
                .requireEtnIssuerCreditModel("$path.etnIssuerCreditModelParameters")
        }
        if (hasClosedEndFundTerms) {
            get("closedEndFundTerms").requireObject("$path.closedEndFundTerms")
                .requireClosedEndFundTerms("$path.closedEndFundTerms")
        }
        if (hasClosedEndFundMarketModel) {
            get("closedEndFundMarketModelParameters")
                .requireObject("$path.closedEndFundMarketModelParameters")
                .requireClosedEndFundMarketModel("$path.closedEndFundMarketModelParameters")
        }
        if (hasClosedEndFundTerms && hasClosedEndFundMarketModel) {
            val termsObject = getAsJsonObject("closedEndFundTerms")
            val modelObject = getAsJsonObject("closedEndFundMarketModelParameters")
            val termsFundId = termsObject.requiredStrictString(
                "fundId",
                "$path.closedEndFundTerms.fundId",
            )
            val modelFundId = modelObject.requiredStrictString(
                "fundId",
                "$path.closedEndFundMarketModelParameters.fundId",
            )
            val allowsDebt = termsObject.requiredBoolean(
                "allowsDebtLeverage",
                "$path.closedEndFundTerms.allowsDebtLeverage",
            )
            val allowsPreferred = termsObject.requiredBoolean(
                "allowsPreferredLeverage",
                "$path.closedEndFundTerms.allowsPreferredLeverage",
            )
            val debtCoverage = termsObject.nullableFiniteDouble(
                "minimumDebtAssetCoverageRatio",
                "$path.closedEndFundTerms.minimumDebtAssetCoverageRatio",
            )
            val preferredCoverage = termsObject.nullableFiniteDouble(
                "minimumPreferredAssetCoverageRatio",
                "$path.closedEndFundTerms.minimumPreferredAssetCoverageRatio",
            )
            val initialDebt = modelObject.requiredFiniteDouble(
                "initialDebtToGrossAssets",
                "$path.closedEndFundMarketModelParameters.initialDebtToGrossAssets",
            )
            val initialPreferred = modelObject.requiredFiniteDouble(
                "initialPreferredToGrossAssets",
                "$path.closedEndFundMarketModelParameters.initialPreferredToGrossAssets",
            )
            val borrowingSpread = modelObject.requiredFiniteDouble(
                "annualBorrowingSpread",
                "$path.closedEndFundMarketModelParameters.annualBorrowingSpread",
            )
            val preferredSpread = modelObject.requiredFiniteDouble(
                "annualPreferredDistributionSpread",
                "$path.closedEndFundMarketModelParameters.annualPreferredDistributionSpread",
            )
            val debtValid = if (allowsDebt) {
                initialDebt > 0.0 && debtCoverage != null && initialDebt <= 1.0 / debtCoverage &&
                    borrowingSpread > 0.0
            } else {
                initialDebt == 0.0 && borrowingSpread == 0.0
            }
            val preferredValid = if (allowsPreferred) {
                initialPreferred > 0.0 && preferredCoverage != null &&
                    initialDebt + initialPreferred <= 1.0 / preferredCoverage &&
                    preferredSpread > 0.0
            } else {
                initialPreferred == 0.0 && preferredSpread == 0.0
            }
            if (termsFundId != modelFundId || !debtValid || !preferredValid) {
                throw JsonParseException(
                    "필드 '$path'의 CEF 법적 커버리지와 초기 레버리지·조달 스프레드가 다릅니다.",
                )
            }
        }
        val optionTransformValid = when (optionStrategyKind) {
            null -> true
            OptionStrategyKind.COVERED_CALL ->
                FundReturnTransform.COVERED_CALL in transforms &&
                    FundReturnTransform.OPTION_INCOME !in transforms &&
                    FundReturnTransform.BUFFERED !in transforms
            OptionStrategyKind.OPTION_INCOME ->
                FundReturnTransform.OPTION_INCOME in transforms &&
                    FundReturnTransform.COVERED_CALL !in transforms &&
                    FundReturnTransform.BUFFERED !in transforms
            OptionStrategyKind.BUFFERED_PUT_SPREAD ->
                FundReturnTransform.BUFFERED in transforms &&
                    FundReturnTransform.OPTION_SPREAD in transforms &&
                    FundReturnTransform.COVERED_CALL !in transforms &&
                    FundReturnTransform.OPTION_INCOME !in transforms
        }
        if (!optionTransformValid) {
            throw JsonParseException("필드 '$path'의 옵션 전략 종류와 수익률 변환이 일치하지 않습니다.")
        }
        if (hasDailyResetTerms && optionStrategyKind != null) {
            throw JsonParseException("필드 '$path'에는 일일 reset과 옵션 전략 약관을 동시에 둘 수 없습니다.")
        }
        val hasCashCollateralizedPutSpreadTransform =
            FundReturnTransform.CASH_COLLATERALIZED_PUT_SPREAD in transforms
        if (hasCashCollateralizedPutSpreadTransform != hasCashCollateralizedPutSpreadTerms ||
            hasCashCollateralizedPutSpreadTerms &&
            (hasDailyResetTerms || optionStrategyKind != null)
        ) {
            throw JsonParseException("필드 '$path'의 현금담보 풋스프레드 변환·전용 약관 조합이 유효하지 않습니다.")
        }
        val validLegalCombination = when (legalStructure) {
            FundLegalStructure.OPEN_END_ETF ->
                replicationMode != FundReplicationMode.SYNTHETIC_NOTE &&
                    FundReturnTransform.ISSUER_CREDIT !in transforms &&
                    FundReturnTransform.PREMIUM_DISCOUNT !in transforms &&
                    !hasEtnTerms && !hasEtnCreditModel &&
                    !hasClosedEndFundTerms && !hasClosedEndFundMarketModel
            FundLegalStructure.EXCHANGE_TRADED_NOTE ->
                    replicationMode == FundReplicationMode.SYNTHETIC_NOTE &&
                    FundReturnTransform.ISSUER_CREDIT in transforms &&
                    hasEtnTerms && hasEtnCreditModel &&
                    !hasClosedEndFundTerms && !hasClosedEndFundMarketModel &&
                    !hasCashCollateralizedPutSpreadTerms
            FundLegalStructure.CLOSED_END_FUND ->
                replicationMode == FundReplicationMode.ACTIVE_MANAGEMENT &&
                    FundReturnTransform.PREMIUM_DISCOUNT in transforms &&
                    !hasEtnTerms && !hasEtnCreditModel &&
                    hasClosedEndFundTerms && hasClosedEndFundMarketModel &&
                    optionStrategyKind == null && !hasCashCollateralizedPutSpreadTerms
        }
        if (hasEtnTerms && hasEtnCreditModel) {
            val termsIssuerId = getAsJsonObject("etnProductTerms")
                .requiredStrictString("issuerId", "$path.etnProductTerms.issuerId")
            val modelIssuerId = getAsJsonObject("etnIssuerCreditModelParameters")
                .requiredStrictString("issuerId", "$path.etnIssuerCreditModelParameters.issuerId")
            if (termsIssuerId != modelIssuerId) {
                throw JsonParseException("필드 '$path'의 ETN 계약과 신용 모델 issuerId가 다릅니다.")
            }
        }
        if (!validLegalCombination) {
            throw JsonParseException("필드 '$path'의 법적 구조·복제 방식·수익률 변환이 일치하지 않습니다.")
        }
    }

    private fun JsonObject.requireDirectReferenceTerminationRule(path: String) {
        requireExactFields(DIRECT_REFERENCE_TERMINATION_RULE_FIELDS, path)
        requiredEnum<DirectReferenceTerminationPolicy>("policy", "$path.policy")
        val provenance = requiredEnum<DirectReferenceTerminationRuleProvenance>(
            "provenance",
            "$path.provenance",
        )
        val sourceElements = requiredArray("officialSourceUrls")
        if (sourceElements.size() > MAX_OPTION_OFFICIAL_SOURCE_URLS) {
            throw JsonParseException("필드 '$path.officialSourceUrls'의 항목이 너무 많습니다.")
        }
        val sourceUrls = sourceElements.mapIndexed { index, element ->
            element.requireStrictString("$path.officialSourceUrls[$index]").also { url ->
                requireSaveHttpsUrl(url, "$path.officialSourceUrls[$index]")
            }
        }
        val assumptionId = nullableStrictString("assumptionId", "$path.assumptionId")
        if (sourceUrls != sourceUrls.distinct().sorted() || when (provenance) {
                DirectReferenceTerminationRuleProvenance.VERIFIED_PRODUCT_DISCLOSURE ->
                    sourceUrls.isEmpty() || assumptionId != null
                DirectReferenceTerminationRuleProvenance.MODEL_ASSUMPTION ->
                    assumptionId == null || !OPTION_ASSUMPTION_ID.matches(assumptionId)
            }
        ) {
            throw JsonParseException("필드 '$path'의 직접 reference 종료 정책 출처가 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireDailyResetTerms(path: String) {
        requireExactFields(DAILY_RESET_TERMS_FIELDS, path)
        val productId = requiredStrictString("productId", "$path.productId")
        if (!DAILY_RESET_PRODUCT_ID.matches(productId)) {
            throw JsonParseException("필드 '$path.productId'의 형식이 유효하지 않습니다.")
        }
        val referenceKind = requiredObject("reference").run {
            val referencePath = "$path.reference"
            requireExactFields(DAILY_RESET_REFERENCE_FIELDS, referencePath)
            val kind = requiredEnum<DailyResetReferenceKind>("kind", "$referencePath.kind")
            requireMember("benchmarkRef")
            requireMember("instrumentId")
            when (kind) {
                DailyResetReferenceKind.BENCHMARK -> {
                    get("benchmarkRef").requireObject("$referencePath.benchmarkRef")
                        .requireBenchmarkRef("$referencePath.benchmarkRef")
                    if (!get("instrumentId").isJsonNull) {
                        throw JsonParseException("필드 '$referencePath.instrumentId'는 null이어야 합니다.")
                    }
                }
                DailyResetReferenceKind.INSTRUMENT -> {
                    if (!get("benchmarkRef").isJsonNull) {
                        throw JsonParseException("필드 '$referencePath.benchmarkRef'는 null이어야 합니다.")
                    }
                    val instrumentId = requiredStrictString("instrumentId", "$referencePath.instrumentId")
                    if (!DAILY_RESET_INSTRUMENT_ID.matches(instrumentId)) {
                        throw JsonParseException("필드 '$referencePath.instrumentId'의 형식이 유효하지 않습니다.")
                    }
                }
            }
            kind
        }
        requireMember("directReferenceTerminationRule")
        val hasDirectTerminationRule = !get("directReferenceTerminationRule").isJsonNull
        if (hasDirectTerminationRule) {
            get("directReferenceTerminationRule").requireObject("$path.directReferenceTerminationRule")
                .requireDirectReferenceTerminationRule("$path.directReferenceTerminationRule")
        }
        if ((referenceKind == DailyResetReferenceKind.INSTRUMENT) != hasDirectTerminationRule) {
            throw JsonParseException("필드 '$path'의 직접 reference와 종료 정책 조합이 유효하지 않습니다.")
        }
        val leverage = requiredFiniteDouble("targetLeverage", "$path.targetLeverage")
        if (leverage !in -MAX_DAILY_RESET_ABS_LEVERAGE..MAX_DAILY_RESET_ABS_LEVERAGE ||
            kotlin.math.abs(leverage) < 1.0
        ) {
            throw JsonParseException("필드 '$path.targetLeverage'의 목표 배율이 유효하지 않습니다.")
        }
        requiredEnum<DailyResetCalendar>("resetCalendar", "$path.resetCalendar")
        val provenance = requiredEnum<DailyResetTermsProvenance>("provenance", "$path.provenance")
        val officialSourceUrl = nullableStrictString("officialSourceUrl", "$path.officialSourceUrl")
        when (provenance) {
            DailyResetTermsProvenance.VERIFIED_PRODUCT_TERMS ->
                requireSaveHttpsUrl(officialSourceUrl, "$path.officialSourceUrl")
            DailyResetTermsProvenance.MODEL_ASSUMPTION -> if (officialSourceUrl != null) {
                throw JsonParseException("필드 '$path.officialSourceUrl'는 null이어야 합니다.")
            }
        }
        requiredObject("modelParameters").apply {
            val modelPath = "$path.modelParameters"
            requireExactFields(DAILY_RESET_MODEL_PARAMETER_FIELDS, modelPath)
            val financingSpread = requiredFiniteDouble(
                "annualFinancingSpread",
                "$modelPath.annualFinancingSpread",
            )
            val collateralParticipation = requiredFiniteDouble(
                "collateralYieldParticipation",
                "$modelPath.collateralYieldParticipation",
            )
            if (financingSpread !in 0.0..1.0 || collateralParticipation !in 0.0..1.5) {
                throw JsonParseException("필드 '$modelPath'의 자금조달·담보 가정이 유효하지 않습니다.")
            }
            val origin = requiredEnum<DailyResetModelParameterOrigin>("origin", "$modelPath.origin")
            val sourceUrl = nullableStrictString("sourceUrl", "$modelPath.sourceUrl")
            when (origin) {
                DailyResetModelParameterOrigin.VERIFIED_DISCLOSURE ->
                    requireSaveHttpsUrl(sourceUrl, "$modelPath.sourceUrl")
                DailyResetModelParameterOrigin.CALIBRATED_ASSUMPTION -> if (sourceUrl != null) {
                    throw JsonParseException("필드 '$modelPath.sourceUrl'은 null이어야 합니다.")
                }
            }
        }
    }

    private fun JsonObject.requireEtnSettlementRule(path: String) {
        requireExactFields(ETN_SETTLEMENT_RULE_FIELDS, path)
        val method = requiredEnum<EtnSettlementValuationMethod>("method", "$path.method")
        val observations = requiredInt("observationCount")
        if (observations !in 1..31 ||
            method == EtnSettlementValuationMethod.LAST_INDICATIVE_VALUE && observations != 1
        ) {
            throw JsonParseException("필드 '$path'의 ETN 평가 관측 규칙이 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireOptionStrategyTerms(path: String): OptionStrategyKind {
        requireExactFields(OPTION_STRATEGY_TERMS_FIELDS, path)
        requiredBoundedNonBlankString("productId", "$path.productId", MAX_DAILY_RESET_PRODUCT_ID_LENGTH)
        val referenceKind = requiredObject("reference").run {
            val referencePath = "$path.reference"
            requireExactFields(DAILY_RESET_REFERENCE_FIELDS, referencePath)
            val kind = requiredEnum<DailyResetReferenceKind>("kind", "$referencePath.kind")
            requireMember("benchmarkRef")
            requireMember("instrumentId")
            when (kind) {
                DailyResetReferenceKind.BENCHMARK -> {
                    get("benchmarkRef").requireObject("$referencePath.benchmarkRef")
                        .requireBenchmarkRef("$referencePath.benchmarkRef")
                    if (!get("instrumentId").isJsonNull) {
                        throw JsonParseException("필드 '$referencePath.instrumentId'는 null이어야 합니다.")
                    }
                }
                DailyResetReferenceKind.INSTRUMENT -> {
                    if (!get("benchmarkRef").isJsonNull) {
                        throw JsonParseException("필드 '$referencePath.benchmarkRef'는 null이어야 합니다.")
                    }
                    val instrumentId = requiredStrictString("instrumentId", "$referencePath.instrumentId")
                    if (!DAILY_RESET_INSTRUMENT_ID.matches(instrumentId)) {
                        throw JsonParseException("필드 '$referencePath.instrumentId'의 형식이 유효하지 않습니다.")
                    }
                }
            }
            kind
        }
        requireMember("directReferenceTerminationRule")
        val hasDirectTerminationRule = !get("directReferenceTerminationRule").isJsonNull
        if (hasDirectTerminationRule) {
            get("directReferenceTerminationRule").requireObject("$path.directReferenceTerminationRule")
                .requireDirectReferenceTerminationRule("$path.directReferenceTerminationRule")
        }
        if ((referenceKind == DailyResetReferenceKind.INSTRUMENT) != hasDirectTerminationRule) {
            throw JsonParseException("필드 '$path'의 직접 reference와 종료 정책 조합이 유효하지 않습니다.")
        }
        val strategyKind = requiredEnum<OptionStrategyKind>("kind", "$path.kind")
        val tenor = requiredInt("tenorTradingDays")
        requiredEnum<OptionRollCalendar>("rollCalendar", "$path.rollCalendar")
        val lead = requiredInt("rollLeadTradingDays")
        val provenance = requiredEnum<OptionStrategyTermsProvenance>("provenance", "$path.provenance")
        val sourceElements = requiredArray("officialSourceUrls")
        if (sourceElements.size() > MAX_OPTION_OFFICIAL_SOURCE_URLS) {
            throw JsonParseException("필드 '$path.officialSourceUrls'의 항목이 너무 많습니다.")
        }
        val sourceUrls = sourceElements.mapIndexed { index, element ->
            element.requireStrictString("$path.officialSourceUrls[$index]").also { url ->
                requireSaveHttpsUrl(url, "$path.officialSourceUrls[$index]")
            }
        }
        val assumptionId = nullableStrictString("assumptionId", "$path.assumptionId")
        if (tenor !in 1..MAX_OPTION_TENOR_TRADING_DAYS || lead !in 0 until tenor ||
            sourceUrls != sourceUrls.distinct().sorted() ||
            provenance == OptionStrategyTermsProvenance.MODEL_ASSUMPTION &&
            (sourceUrls.isNotEmpty() || assumptionId == null || !OPTION_ASSUMPTION_ID.matches(assumptionId)) ||
            provenance != OptionStrategyTermsProvenance.MODEL_ASSUMPTION &&
            (sourceUrls.isEmpty() || assumptionId != null)
        ) {
            throw JsonParseException("필드 '$path'의 옵션 전략 출처·만기·롤 조건이 유효하지 않습니다.")
        }
        requiredObject("premiumModel").apply {
            val modelPath = "$path.premiumModel"
            requireExactFields(OPTION_PREMIUM_MODEL_FIELDS, modelPath)
            val volatility = requiredFiniteDouble(
                "impliedVolatilityMultiplier",
                "$modelPath.impliedVolatilityMultiplier",
            )
            val capture = requiredFiniteDouble("soldPremiumCaptureRatio", "$modelPath.soldPremiumCaptureRatio")
            val purchase = requiredFiniteDouble(
                "purchasedPremiumCostRatio",
                "$modelPath.purchasedPremiumCostRatio",
            )
            val cost = requiredFiniteDouble(
                "implementationCostRatePerRoll",
                "$modelPath.implementationCostRatePerRoll",
            )
            val origin = requiredEnum<OptionPremiumModelParameterOrigin>("origin", "$modelPath.origin")
            val sourceUrl = nullableStrictString("sourceUrl", "$modelPath.sourceUrl")
            sourceUrl?.let { requireSaveHttpsUrl(it, "$modelPath.sourceUrl") }
            val calibrationId = nullableStrictString("calibrationId", "$modelPath.calibrationId")
            if (volatility !in 0.25..4.0 || capture !in 0.0..1.5 || purchase !in 0.5..2.0 ||
                cost !in 0.0..0.10 ||
                origin == OptionPremiumModelParameterOrigin.VERIFIED_DISCLOSURE &&
                (sourceUrl == null || calibrationId != null) ||
                origin == OptionPremiumModelParameterOrigin.CALIBRATED_ASSUMPTION &&
                (sourceUrl != null || calibrationId == null || !OPTION_ASSUMPTION_ID.matches(calibrationId))
            ) {
                throw JsonParseException("필드 '$modelPath'의 옵션 프리미엄 모수가 유효하지 않습니다.")
            }
        }
        listOf("coveredCall", "optionIncome", "bufferedPutSpread").forEach { field ->
            requireMember(field)
        }
        val covered = !get("coveredCall").isJsonNull
        val income = !get("optionIncome").isJsonNull
        val buffered = !get("bufferedPutSpread").isJsonNull
        if (listOf(covered, income, buffered).count { it } != 1 ||
            covered != (strategyKind == OptionStrategyKind.COVERED_CALL) ||
            income != (strategyKind == OptionStrategyKind.OPTION_INCOME) ||
            buffered != (strategyKind == OptionStrategyKind.BUFFERED_PUT_SPREAD)
        ) {
            throw JsonParseException("필드 '$path'의 옵션 전략별 조건 객체가 일치하지 않습니다.")
        }
        if (covered) {
            val detailPath = "$path.coveredCall"
            get("coveredCall").requireObject(detailPath).apply {
                requireExactFields(COVERED_CALL_TERMS_FIELDS, detailPath)
                val ratio = requiredFiniteDouble("overwriteRatio", "$detailPath.overwriteRatio")
                val strike = requiredFiniteDouble("callStrikeMoneyness", "$detailPath.callStrikeMoneyness")
                if (ratio !in MIN_OPTION_POSITIVE_RATIO..1.0 || strike !in 0.50..2.0) {
                    throw JsonParseException("필드 '$detailPath'의 covered-call 조건이 유효하지 않습니다.")
                }
            }
        }
        if (income) {
            val detailPath = "$path.optionIncome"
            get("optionIncome").requireObject(detailPath).apply {
                requireExactFields(OPTION_INCOME_TERMS_FIELDS, detailPath)
                val core = requiredFiniteDouble("coreEquityAllocation", "$detailPath.coreEquityAllocation")
                val allocation = requiredFiniteDouble(
                    "optionIncomeAllocation",
                    "$detailPath.optionIncomeAllocation",
                )
                val upside = requiredFiniteDouble("upsideParticipation", "$detailPath.upsideParticipation")
                val downside = requiredFiniteDouble(
                    "downsideParticipation",
                    "$detailPath.downsideParticipation",
                )
                val strike = requiredFiniteDouble("callStrikeMoneyness", "$detailPath.callStrikeMoneyness")
                if (core !in 0.0..1.0 || allocation !in MIN_OPTION_POSITIVE_RATIO..1.0 ||
                    core + allocation > 1.0 + OPTION_WEIGHT_EPSILON ||
                    upside !in 0.0..1.0 || downside !in 0.0..1.0 ||
                    upside == 0.0 && downside == 0.0 || strike !in 1.0..3.0
                ) {
                    throw JsonParseException("필드 '$detailPath'의 option-income 조건이 유효하지 않습니다.")
                }
            }
        }
        if (buffered) {
            val detailPath = "$path.bufferedPutSpread"
            get("bufferedPutSpread").requireObject(detailPath).apply {
                requireExactFields(BUFFERED_PUT_SPREAD_TERMS_FIELDS, detailPath)
                val notional = requiredFiniteDouble("outcomeNotionalRatio", "$detailPath.outcomeNotionalRatio")
                val longStrike = requiredFiniteDouble(
                    "longPutStrikeMoneyness",
                    "$detailPath.longPutStrikeMoneyness",
                )
                val buffer = requiredFiniteDouble("downsideBufferFraction", "$detailPath.downsideBufferFraction")
                val downside = requiredFiniteDouble(
                    "downsideParticipationBeyondBuffer",
                    "$detailPath.downsideParticipationBeyondBuffer",
                )
                val cap = requiredFiniteDouble("upsideCapFraction", "$detailPath.upsideCapFraction")
                if (notional !in MIN_OPTION_POSITIVE_RATIO..1.0 || longStrike !in 0.50..1.50 ||
                    buffer !in 0.001..0.95 || longStrike - buffer <= MIN_OPTION_STRIKE_MONEYNESS ||
                    downside !in 0.0..1.0 || cap !in 0.001..2.0
                ) {
                    throw JsonParseException("필드 '$detailPath'의 buffered-put-spread 조건이 유효하지 않습니다.")
                }
            }
        }
        return strategyKind
    }

    private fun JsonObject.requireCashCollateralizedPutSpreadTerms(path: String) {
        requireExactFields(CASH_COLLATERALIZED_PUT_SPREAD_TERMS_FIELDS, path)
        requiredBoundedNonBlankString(
            "productId",
            "$path.productId",
            MAX_DAILY_RESET_PRODUCT_ID_LENGTH,
        )
        requiredObject("cashBenchmarkRef").requireBenchmarkRef("$path.cashBenchmarkRef")
        val referenceKind = requiredObject("optionReference").run {
            val referencePath = "$path.optionReference"
            requireExactFields(DAILY_RESET_REFERENCE_FIELDS, referencePath)
            val kind = requiredEnum<DailyResetReferenceKind>("kind", "$referencePath.kind")
            requireMember("benchmarkRef")
            requireMember("instrumentId")
            when (kind) {
                DailyResetReferenceKind.BENCHMARK -> {
                    get("benchmarkRef").requireObject("$referencePath.benchmarkRef")
                        .requireBenchmarkRef("$referencePath.benchmarkRef")
                    if (!get("instrumentId").isJsonNull) {
                        throw JsonParseException("필드 '$referencePath.instrumentId'는 null이어야 합니다.")
                    }
                }
                DailyResetReferenceKind.INSTRUMENT -> {
                    if (!get("benchmarkRef").isJsonNull) {
                        throw JsonParseException("필드 '$referencePath.benchmarkRef'는 null이어야 합니다.")
                    }
                    val instrumentId = requiredStrictString(
                        "instrumentId",
                        "$referencePath.instrumentId",
                    )
                    if (!DAILY_RESET_INSTRUMENT_ID.matches(instrumentId)) {
                        throw JsonParseException("필드 '$referencePath.instrumentId'의 형식이 유효하지 않습니다.")
                    }
                }
            }
            kind
        }
        requireMember("directReferenceTerminationRule")
        val hasDirectTerminationRule = !get("directReferenceTerminationRule").isJsonNull
        if (hasDirectTerminationRule) {
            get("directReferenceTerminationRule").requireObject("$path.directReferenceTerminationRule")
                .requireDirectReferenceTerminationRule("$path.directReferenceTerminationRule")
        }
        if ((referenceKind == DailyResetReferenceKind.INSTRUMENT) != hasDirectTerminationRule) {
            throw JsonParseException("필드 '$path'의 직접 option reference와 종료 정책 조합이 유효하지 않습니다.")
        }
        val tenor = requiredInt("tenorTradingDays")
        requiredEnum<OptionRollCalendar>("rollCalendar", "$path.rollCalendar")
        val lead = requiredInt("rollLeadTradingDays")
        val maximumLoss = requiredFiniteDouble(
            "maximumSettlementLossRatio",
            "$path.maximumSettlementLossRatio",
        )
        val shortStrike = requiredFiniteDouble(
            "shortPutStrikeMoneyness",
            "$path.shortPutStrikeMoneyness",
        )
        val longStrike = requiredFiniteDouble(
            "longPutStrikeMoneyness",
            "$path.longPutStrikeMoneyness",
        )
        val provenance = requiredEnum<OptionStrategyTermsProvenance>(
            "provenance",
            "$path.provenance",
        )
        val sourceElements = requiredArray("officialSourceUrls")
        if (sourceElements.size() > MAX_OPTION_OFFICIAL_SOURCE_URLS) {
            throw JsonParseException("필드 '$path.officialSourceUrls'의 항목이 너무 많습니다.")
        }
        val sourceUrls = sourceElements.mapIndexed { index, element ->
            element.requireStrictString("$path.officialSourceUrls[$index]").also { url ->
                requireSaveHttpsUrl(url, "$path.officialSourceUrls[$index]")
            }
        }
        val assumptionId = nullableStrictString("assumptionId", "$path.assumptionId")
        if (tenor !in 1..MAX_OPTION_TENOR_TRADING_DAYS || lead !in 0 until tenor ||
            maximumLoss !in MIN_OPTION_POSITIVE_RATIO..1.0 ||
            shortStrike !in 0.051..1.50 || longStrike !in 0.05..1.50 ||
            shortStrike - longStrike < MIN_CASH_PUT_SPREAD_WIDTH ||
            sourceUrls != sourceUrls.distinct().sorted() ||
            provenance == OptionStrategyTermsProvenance.MODEL_ASSUMPTION &&
            (assumptionId == null || !OPTION_ASSUMPTION_ID.matches(assumptionId)) ||
            provenance != OptionStrategyTermsProvenance.MODEL_ASSUMPTION &&
            (sourceUrls.isEmpty() || assumptionId != null)
        ) {
            throw JsonParseException("필드 '$path'의 현금담보 풋스프레드 약관·출처가 유효하지 않습니다.")
        }
        requiredObject("premiumModel").apply {
            val modelPath = "$path.premiumModel"
            requireExactFields(OPTION_PREMIUM_MODEL_FIELDS, modelPath)
            val volatility = requiredFiniteDouble(
                "impliedVolatilityMultiplier",
                "$modelPath.impliedVolatilityMultiplier",
            )
            val capture = requiredFiniteDouble(
                "soldPremiumCaptureRatio",
                "$modelPath.soldPremiumCaptureRatio",
            )
            val purchase = requiredFiniteDouble(
                "purchasedPremiumCostRatio",
                "$modelPath.purchasedPremiumCostRatio",
            )
            val cost = requiredFiniteDouble(
                "implementationCostRatePerRoll",
                "$modelPath.implementationCostRatePerRoll",
            )
            val origin = requiredEnum<OptionPremiumModelParameterOrigin>(
                "origin",
                "$modelPath.origin",
            )
            val sourceUrl = nullableStrictString("sourceUrl", "$modelPath.sourceUrl")
            sourceUrl?.let { requireSaveHttpsUrl(it, "$modelPath.sourceUrl") }
            val calibrationId = nullableStrictString("calibrationId", "$modelPath.calibrationId")
            if (volatility !in 0.25..4.0 || capture !in 0.0..1.5 || purchase !in 0.5..2.0 ||
                cost !in 0.0..0.10 ||
                origin == OptionPremiumModelParameterOrigin.VERIFIED_DISCLOSURE &&
                (sourceUrl == null || calibrationId != null) ||
                origin == OptionPremiumModelParameterOrigin.CALIBRATED_ASSUMPTION &&
                (sourceUrl != null || calibrationId == null ||
                    !OPTION_ASSUMPTION_ID.matches(calibrationId))
            ) {
                throw JsonParseException("필드 '$modelPath'의 옵션 프리미엄 모수가 유효하지 않습니다.")
            }
        }
    }

    private fun JsonObject.requireNullableEtnSettlementRule(field: String, path: String): Boolean {
        requireMember(field)
        if (get(field).isJsonNull) return false
        get(field).requireObject(path).requireEtnSettlementRule(path)
        return true
    }

    private fun JsonObject.requireEtnCouponRule(path: String) {
        requireExactFields(ETN_COUPON_RULE_FIELDS, path)
        val kind = requiredEnum<EtnCouponKind>("kind", "$path.kind")
        val frequency = requiredInt("paymentFrequencyMonths")
        val fixedRate = requiredFiniteDouble("annualFixedRate", "$path.annualFixedRate")
        val participation = requiredFiniteDouble("participationRate", "$path.participationRate")
        val reducesValue = requiredBoolean(
            "accrualReducesIndicativeValue",
            "$path.accrualReducesIndicativeValue",
        )
        val paidAtTermination = requiredBoolean(
            "accruedCouponPaidAtTermination",
            "$path.accruedCouponPaidAtTermination",
        )
        if (frequency !in 0..120 || fixedRate !in 0.0..MAX_FUND_STRUCTURE_RATE ||
            participation !in 0.0..MAX_FUND_STRUCTURE_RATE
        ) {
            throw JsonParseException("필드 '$path'의 ETN 쿠폰 수치가 유효하지 않습니다.")
        }
        val valid = when (kind) {
            EtnCouponKind.NONE -> frequency == 0 && fixedRate == 0.0 && participation == 0.0 &&
                !reducesValue && !paidAtTermination
            EtnCouponKind.FIXED_RATE -> frequency > 0 && fixedRate > 0.0 && participation == 0.0
            EtnCouponKind.REFERENCE_CASH_FLOW,
            EtnCouponKind.OPTION_PREMIUM_LINKED,
            -> frequency > 0 && fixedRate == 0.0 && participation > 0.0
        }
        if (!valid) throw JsonParseException("필드 '$path'의 ETN 쿠폰 종류·조건이 일치하지 않습니다.")
    }

    private fun JsonObject.requireEtnCallTerms(path: String) {
        requireExactFields(ETN_CALL_TERMS_FIELDS, path)
        val issuerCallable = requiredBoolean("issuerCallable", "$path.issuerCallable")
        val partialCall = requiredBoolean("issuerCallMayBePartial", "$path.issuerCallMayBePartial")
        val holderRedeemable = requiredBoolean("holderRedeemable", "$path.holderRedeemable")
        val minimumNotes = nullableLong(
            "minimumHolderRedemptionNotes",
            "$path.minimumHolderRedemptionNotes",
        )
        val increment = nullableLong(
            "holderRedemptionNoteIncrement",
            "$path.holderRedemptionNoteIncrement",
        )
        val noticeDays = requiredInt("minimumNoticeBusinessDays")
        val hasIssuerRule = requireNullableEtnSettlementRule(
            "issuerCallValuationRule",
            "$path.issuerCallValuationRule",
        )
        val hasHolderRule = requireNullableEtnSettlementRule(
            "holderRedemptionValuationRule",
            "$path.holderRedemptionValuationRule",
        )
        val issuerMultiplier = requiredFiniteDouble(
            "issuerCallSettlementMultiplier",
            "$path.issuerCallSettlementMultiplier",
        )
        val holderMultiplier = requiredFiniteDouble(
            "holderRedemptionSettlementMultiplier",
            "$path.holderRedemptionSettlementMultiplier",
        )
        val charge = requiredFiniteDouble("holderRedemptionChargeRate", "$path.holderRedemptionChargeRate")
        val includesCoupon = requiredBoolean("includesAccruedCoupon", "$path.includesAccruedCoupon")
        val holderValid = if (holderRedeemable) {
            minimumNotes != null && minimumNotes in 1L..MAX_FUND_STRUCTURE_EXACT_QUANTITY &&
                increment != null && increment in 1L..MAX_FUND_STRUCTURE_EXACT_QUANTITY && hasHolderRule &&
                holderMultiplier > 0.0
        } else {
            minimumNotes == null && increment == null && !hasHolderRule &&
                holderMultiplier == 0.0 && charge == 0.0
        }
        val issuerValid = if (issuerCallable) {
            hasIssuerRule && issuerMultiplier > 0.0
        } else {
            !partialCall && !hasIssuerRule && issuerMultiplier == 0.0
        }
        if (noticeDays !in 0..365 || issuerMultiplier !in 0.0..MAX_FUND_STRUCTURE_RATE ||
            holderMultiplier !in 0.0..MAX_FUND_STRUCTURE_RATE || charge !in 0.0..1.0 ||
            charge > holderMultiplier || !holderValid || !issuerValid ||
            !issuerCallable && !holderRedeemable && (noticeDays != 0 || includesCoupon)
        ) {
            throw JsonParseException("필드 '$path'의 ETN 콜·상환 조건이 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireEtnAccelerationTerms(path: String) {
        requireExactFields(ETN_ACCELERATION_TERMS_FIELDS, path)
        val mayAccelerate = requiredBoolean("issuerMayAccelerate", "$path.issuerMayAccelerate")
        val partialAllowed = requiredBoolean(
            "partialAccelerationAllowed",
            "$path.partialAccelerationAllowed",
        )
        val minimumNotes = nullableLong(
            "minimumPartialAccelerationNotes",
            "$path.minimumPartialAccelerationNotes",
        )
        val increment = nullableLong(
            "partialAccelerationNoteIncrement",
            "$path.partialAccelerationNoteIncrement",
        )
        val defaultAccelerates = requiredBoolean(
            "creditDefaultCausesAcceleration",
            "$path.creditDefaultCausesAcceleration",
        )
        val hasFullRule = requireNullableEtnSettlementRule(
            "fullAccelerationValuationRule",
            "$path.fullAccelerationValuationRule",
        )
        val hasPartialRule = requireNullableEtnSettlementRule(
            "partialAccelerationValuationRule",
            "$path.partialAccelerationValuationRule",
        )
        val multiplier = requiredFiniteDouble(
            "accelerationSettlementMultiplier",
            "$path.accelerationSettlementMultiplier",
        )
        val includesNonCreditCoupon = requiredBoolean(
            "nonCreditAccelerationIncludesAccruedCoupon",
            "$path.nonCreditAccelerationIncludesAccruedCoupon",
        )
        val includesDefaultCoupon = requiredBoolean(
            "creditDefaultIncludesAccruedCouponBeforeRecovery",
            "$path.creditDefaultIncludesAccruedCouponBeforeRecovery",
        )
        val partialValid = if (partialAllowed) {
            mayAccelerate && minimumNotes != null &&
                minimumNotes in 1L..MAX_FUND_STRUCTURE_EXACT_QUANTITY && increment != null &&
                increment in 1L..MAX_FUND_STRUCTURE_EXACT_QUANTITY && hasPartialRule
        } else {
            minimumNotes == null && increment == null && !hasPartialRule
        }
        if (multiplier !in 0.0..MAX_FUND_STRUCTURE_RATE || !partialValid ||
            mayAccelerate != hasFullRule ||
            (mayAccelerate || defaultAccelerates) != (multiplier > 0.0) ||
            !mayAccelerate && includesNonCreditCoupon ||
            !defaultAccelerates && includesDefaultCoupon
        ) {
            throw JsonParseException("필드 '$path'의 ETN 가속상환 조건이 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireEtnProductTerms(path: String) {
        requireExactFields(ETN_PRODUCT_TERMS_FIELDS, path)
        listOf("productId", "referenceId", "issuerId").forEach { field ->
            requiredBoundedNonBlankString(field, "$path.$field", MAX_FUND_STRUCTURE_ID_LENGTH)
        }
        requiredEnum<ReferenceCurrency>("settlementCurrency", "$path.settlementCurrency")
        val principal = requiredFiniteDouble("statedPrincipalPerNote", "$path.statedPrincipalPerNote")
        val fee = requiredFiniteDouble("annualInvestorFeeRate", "$path.annualInvestorFeeRate")
        val basis = requiredInt("investorFeeDayCountBasis")
        val issueDate = requiredLocalDate("issueDate", "$path.issueDate")
        val maturityDate = requiredLocalDate("maturityDate", "$path.maturityDate")
        requiredObject("maturityValuationRule").requireEtnSettlementRule(
            "$path.maturityValuationRule",
        )
        val multiplier = requiredFiniteDouble(
            "maturitySettlementMultiplier",
            "$path.maturitySettlementMultiplier",
        )
        requiredBoolean("maturityIncludesAccruedCoupon", "$path.maturityIncludesAccruedCoupon")
        requiredObject("couponRule").requireEtnCouponRule("$path.couponRule")
        requiredObject("callTerms").requireEtnCallTerms("$path.callTerms")
        requiredObject("accelerationTerms").requireEtnAccelerationTerms("$path.accelerationTerms")
        val provenance = requiredEnum<FundStructureTermsProvenance>(
            "termsProvenance",
            "$path.termsProvenance",
        )
        val sourceUrl = nullableStrictString("officialSourceUrl", "$path.officialSourceUrl")
        sourceUrl?.let { requireSaveHttpsUrl(it, "$path.officialSourceUrl") }
        if (principal !in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_VALUE ||
            fee !in 0.0..MAX_FUND_STRUCTURE_RATE || basis !in 1..366 || fee >= basis.toDouble() ||
            issueDate >= maturityDate ||
            multiplier !in MIN_FUND_STRUCTURE_VALUE..MAX_FUND_STRUCTURE_RATE ||
            provenance != FundStructureTermsProvenance.MODEL_ASSUMPTION && sourceUrl == null
        ) {
            throw JsonParseException("필드 '$path'의 ETN 계약 조건이 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireEtnIssuerCreditModel(path: String) {
        requireExactFields(ETN_ISSUER_CREDIT_MODEL_FIELDS, path)
        requiredBoundedNonBlankString("issuerId", "$path.issuerId", MAX_FUND_STRUCTURE_ID_LENGTH)
        val creditSpread = requiredFiniteDouble("initialCreditSpread", "$path.initialCreditSpread")
        val hazardRate = requiredFiniteDouble("initialHazardRate", "$path.initialHazardRate")
        val recoveryRate = requiredFiniteDouble("recoveryRate", "$path.recoveryRate")
        val meanReversion = requiredFiniteDouble(
            "annualSpreadMeanReversionRate",
            "$path.annualSpreadMeanReversionRate",
        )
        val shockVolatility = requiredFiniteDouble(
            "spreadShockAnnualVolatility",
            "$path.spreadShockAnnualVolatility",
        )
        val origin = requiredEnum<FundStructureModelParameterOrigin>("origin", "$path.origin")
        val sourceUrl = nullableStrictString("sourceUrl", "$path.sourceUrl")
        sourceUrl?.let { requireSaveHttpsUrl(it, "$path.sourceUrl") }
        if (creditSpread !in 0.0..1.0 || hazardRate !in 0.0..1.0 ||
            recoveryRate !in 0.0..1.0 || meanReversion !in 0.0..100.0 ||
            shockVolatility !in 0.0..5.0 ||
            origin == FundStructureModelParameterOrigin.OFFICIAL_DISCLOSURE && sourceUrl == null
        ) {
            throw JsonParseException("필드 '$path'의 ETN 발행자 신용 모수가 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireClosedEndFundTerms(path: String) {
        requireExactFields(CLOSED_END_FUND_TERMS_FIELDS, path)
        requiredBoundedNonBlankString("fundId", "$path.fundId", MAX_FUND_STRUCTURE_ID_LENGTH)
        requiredEnum<ReferenceCurrency>("settlementCurrency", "$path.settlementCurrency")
        requiredEnum<ClosedEndFundDistributionPolicy>("distributionPolicy", "$path.distributionPolicy")
        listOf(
            "allowsTenderOffers",
            "allowsShareRepurchases",
            "allowsRightsOfferings",
            "allowsAtTheMarketOfferings",
        ).forEach { field -> requiredBoolean(field, "$path.$field") }
        val allowsDebt = requiredBoolean("allowsDebtLeverage", "$path.allowsDebtLeverage")
        val allowsPreferred = requiredBoolean(
            "allowsPreferredLeverage",
            "$path.allowsPreferredLeverage",
        )
        val debtCoverage = nullableFiniteDouble(
            "minimumDebtAssetCoverageRatio",
            "$path.minimumDebtAssetCoverageRatio",
        )
        val preferredCoverage = nullableFiniteDouble(
            "minimumPreferredAssetCoverageRatio",
            "$path.minimumPreferredAssetCoverageRatio",
        )
        val provenance = requiredEnum<FundStructureTermsProvenance>(
            "termsProvenance",
            "$path.termsProvenance",
        )
        val sourceUrl = nullableStrictString("officialSourceUrl", "$path.officialSourceUrl")
        sourceUrl?.let { requireSaveHttpsUrl(it, "$path.officialSourceUrl") }
        if (allowsDebt != (debtCoverage != null) || allowsPreferred != (preferredCoverage != null) ||
            debtCoverage?.let { it !in 1.0..MAX_CEF_ASSET_COVERAGE_RATIO } == true ||
            preferredCoverage?.let { it !in 1.0..MAX_CEF_ASSET_COVERAGE_RATIO } == true ||
            provenance != FundStructureTermsProvenance.MODEL_ASSUMPTION && sourceUrl == null
        ) {
            throw JsonParseException("필드 '$path'의 CEF 법적·레버리지 조건이 유효하지 않습니다.")
        }
    }

    private fun JsonObject.requireTaxBreakdown(path: String) {
        requireExactFields(TAX_BREAKDOWN_FIELDS, path)
        requiredBoundedNonBlankString("policyId", "$path.policyId", MAX_TAX_TEXT_LENGTH)
        requiredLocalDate("calculatedOn", "$path.calculatedOn")
        requiredObject("taxableBase").requireMoneyAmount("$path.taxableBase")
        requiredArray("items").forEachIndexed { index, element ->
            val itemPath = "$path.items[$index]"
            element.requireObject(itemPath).apply {
                requireExactFields(TAX_LINE_ITEM_FIELDS, itemPath)
                requiredBoundedNonBlankString("id", "$itemPath.id", MAX_TAX_TEXT_LENGTH)
                requiredBoundedNonBlankString("label", "$itemPath.label", MAX_TAX_TEXT_LENGTH)
                requiredObject("amount").requireMoneyAmount("$itemPath.amount")
                requiredEnum<TaxJurisdiction>("jurisdiction", "$itemPath.jurisdiction")
                requiredEnum<TaxCategory>("category", "$itemPath.category")
                requiredObject("source").requireRuleSource("$itemPath.source")
                requiredObject("effectiveRange").requireEffectiveDateRange(
                    "$itemPath.effectiveRange",
                )
            }
        }
        requiredArray("warnings").forEachIndexed { index, element ->
            val warning = element.requireStrictString("$path.warnings[$index]")
            if (warning.length > MAX_TAX_WARNING_LENGTH || warning.any(Char::isISOControl)) {
                throw JsonParseException("필드 '$path.warnings[$index]'의 길이·문자가 유효하지 않습니다.")
            }
        }
    }

    private fun JsonObject.requireFeeBreakdown(path: String) {
        requireExactFields(FEE_BREAKDOWN_FIELDS, path)
        requiredLocalDate("calculatedOn", "$path.calculatedOn")
        requiredEnum<Currency>("currency", "$path.currency")
        requiredArray("items").forEachIndexed { index, element ->
            val itemPath = "$path.items[$index]"
            element.requireObject(itemPath).apply {
                requireExactFields(FEE_LINE_ITEM_FIELDS, itemPath)
                requiredBoundedNonBlankString("id", "$itemPath.id", MAX_TAX_TEXT_LENGTH)
                requiredBoundedNonBlankString("label", "$itemPath.label", MAX_TAX_TEXT_LENGTH)
                requiredObject("amount").requireMoneyAmount("$itemPath.amount")
                requiredEnum<FeeJurisdiction>("jurisdiction", "$itemPath.jurisdiction")
                requiredEnum<FeeCategory>("category", "$itemPath.category")
                requiredObject("source").requireRuleSource("$itemPath.source")
                requiredObject("effectiveRange").requireEffectiveDateRange(
                    "$itemPath.effectiveRange",
                )
            }
        }
        requiredArray("warnings").forEachIndexed { index, element ->
            val warning = element.requireStrictString("$path.warnings[$index]")
            if (warning.length > MAX_TAX_WARNING_LENGTH || warning.any(Char::isISOControl)) {
                throw JsonParseException("필드 '$path.warnings[$index]'의 길이·문자가 유효하지 않습니다.")
            }
        }
    }

    private fun JsonObject.requireMoneyAmount(path: String) {
        requireExactFields(MONEY_AMOUNT_FIELDS, path)
        if (requiredLong("minorUnits", "$path.minorUnits") < 0L) {
            throw JsonParseException("필드 '$path.minorUnits'는 음수일 수 없습니다.")
        }
        requiredEnum<Currency>("currency", "$path.currency")
    }

    private fun JsonObject.requireRuleSource(path: String) {
        requireExactFields(RULE_SOURCE_FIELDS, path)
        requiredBoundedNonBlankString("title", "$path.title", MAX_TAX_TEXT_LENGTH)
        nullableStrictString("url", "$path.url")?.let { requireSaveHttpsUrl(it, "$path.url") }
        requiredLocalDate("accessedOn", "$path.accessedOn")
    }

    private fun JsonObject.requireEffectiveDateRange(path: String) {
        requireExactFields(EFFECTIVE_DATE_RANGE_FIELDS, path)
        val validFrom = requiredLocalDate("validFrom", "$path.validFrom")
        val validThrough = nullableLocalDate("validThrough", "$path.validThrough")
        if (validThrough != null && validThrough < validFrom) {
            throw JsonParseException("필드 '$path'의 유효기간 순서가 올바르지 않습니다.")
        }
    }

    private fun JsonObject.requireClosedEndFundMarketModel(path: String) {
        requireExactFields(CLOSED_END_FUND_MARKET_MODEL_FIELDS, path)
        requiredBoundedNonBlankString("fundId", "$path.fundId", MAX_FUND_STRUCTURE_ID_LENGTH)
        val discount = requiredFiniteDouble("targetMarketDiscountRate", "$path.targetMarketDiscountRate")
        val reversion = requiredFiniteDouble(
            "annualDiscountMeanReversionRate",
            "$path.annualDiscountMeanReversionRate",
        )
        val initialDebt = requiredFiniteDouble(
            "initialDebtToGrossAssets",
            "$path.initialDebtToGrossAssets",
        )
        val initialPreferred = requiredFiniteDouble(
            "initialPreferredToGrossAssets",
            "$path.initialPreferredToGrossAssets",
        )
        val borrowingSpread = requiredFiniteDouble(
            "annualBorrowingSpread",
            "$path.annualBorrowingSpread",
        )
        val preferredSpread = requiredFiniteDouble(
            "annualPreferredDistributionSpread",
            "$path.annualPreferredDistributionSpread",
        )
        val discountVolatility = requiredFiniteDouble(
            "discountShockAnnualVolatility",
            "$path.discountShockAnnualVolatility",
        )
        val origin = requiredEnum<FundStructureModelParameterOrigin>("origin", "$path.origin")
        val sourceUrl = nullableStrictString("sourceUrl", "$path.sourceUrl")
        sourceUrl?.let { requireSaveHttpsUrl(it, "$path.sourceUrl") }
        if (discount !in -0.99..MAX_FUND_STRUCTURE_RATE ||
            reversion !in 0.0..MAX_FUND_STRUCTURE_RATE ||
            initialDebt !in 0.0..0.95 || initialPreferred !in 0.0..0.95 ||
            initialDebt + initialPreferred > 0.95 ||
            borrowingSpread !in 0.0..1.0 || preferredSpread !in 0.0..1.0 ||
            discountVolatility !in 0.0..5.0 ||
            origin == FundStructureModelParameterOrigin.OFFICIAL_DISCLOSURE && sourceUrl == null
        ) {
            throw JsonParseException("필드 '$path'의 CEF 시장가격 모수가 유효하지 않습니다.")
        }
    }

    private fun requireSaveHttpsUrl(value: String?, path: String) {
        if (value == null || !value.startsWith("https://") || value.length > MAX_SAVE_URL_LENGTH ||
            value.any(Char::isISOControl)
        ) {
            throw JsonParseException("필드 '$path'는 유효한 HTTPS URL이어야 합니다.")
        }
    }

    private fun corrupted(path: Path, message: String, cause: Throwable? = null): GameLoadFailure =
        GameLoadFailure(
            path = path.toString(),
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

    private fun uncompressedTooLargeError(actualSize: Long): GameSaveError = GameSaveError(
        code = GameSaveErrorCode.FILE_TOO_LARGE,
        message = "저장 JSON이 안전 한도 ${MAX_UNCOMPRESSED_GAME_SAVE_BYTES}바이트를 " +
            "초과했습니다: ${actualSize}바이트.",
    )

    private fun readFrameHeader(input: java.io.InputStream): GameSaveFrameHeader {
        val bytes = ByteArray(GameSaveFrameHeader.BYTE_SIZE)
        var offset = 0
        while (offset < bytes.size) {
            val read = input.read(bytes, offset, bytes.size - offset)
            if (read < 0) throw CorruptSaveFrameException("저장 프레임 헤더가 잘렸습니다.")
            offset += read
        }
        return try {
            GameSaveFrameHeader.decode(bytes)
        } catch (error: IllegalArgumentException) {
            throw JsonParseException("저장 프레임 헤더가 유효하지 않습니다: ${safeMessage(error)}", error)
        }
    }

    private fun validateFrameLengths(header: GameSaveFrameHeader, physicalSize: Long) {
        if (header.rawLength > MAX_UNCOMPRESSED_GAME_SAVE_BYTES) {
            throw UncompressedSaveTooLargeException(header.rawLength)
        }
        val expected = GameSaveFrameHeader.BYTE_SIZE.toLong() + header.compressedLength
        if (expected != physicalSize || expected > MAX_GAME_SAVE_FILE_BYTES) {
            throw JsonParseException("저장 프레임 선언 길이와 실제 파일 길이가 일치하지 않습니다.")
        }
    }

    private fun validateFrameMetadata(header: GameSaveFrameHeader) {
        val calendar = com.amond.kmpbook.domain.time.GameCalendar
        if (!calendar.isWithinGameRange(header.gameTime) ||
            header.turn != calendar.turnAt(header.gameTime) ||
            header.gameTime != calendar.startInstant + header.turn.hours
        ) {
            throw JsonParseException("저장 프레임 게임 시각·턴이 캠페인 시간 격자와 다릅니다.")
        }
        val savedAtIsDisplayable = runCatching {
            calendar.toGameLocalDateTime(header.savedAt)
        }.isSuccess
        if (!savedAtIsDisplayable || header.savedAt !in MIN_SANE_SAVED_AT..MAX_SANE_SAVED_AT) {
            throw JsonParseException("저장 프레임 savedAt이 표시 가능한 보존 범위를 벗어났습니다.")
        }
    }

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

    private fun sizeLimitError(error: Throwable): GameSaveError? {
        var current: Throwable? = error
        repeat(MAX_CAUSE_CHAIN_DEPTH) {
            when (val cause = current ?: return null) {
                is UncompressedSaveTooLargeException ->
                    return uncompressedTooLargeError(cause.actualSize)
                is SaveFileTooLargeException -> return tooLargeError(cause.actualSize)
            }
            val next = current.cause
            if (next === current) return null
            current = next
        }
        return null
    }

    private fun createPrivateTemporaryFile(
        directory: Path,
        prefix: String,
        suffix: String,
    ): Path = if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        Files.createTempFile(
            directory,
            prefix,
            suffix,
            PosixFilePermissions.asFileAttribute(
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            ),
        )
    } else {
        Files.createTempFile(directory, prefix, suffix)
    }

    private fun GameSaveFrameHeader.matches(metadata: GameSaveMetadata): Boolean =
        metadata.format == GAME_SAVE_FORMAT_ID &&
            metadata.schemaVersion == schemaVersion && metadata.savedAt == savedAt &&
            metadata.gameTime == gameTime && metadata.turn == turn

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

    private fun writeFully(channel: FileChannel, buffer: java.nio.ByteBuffer) {
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    private fun forceDirectoryBestEffort(directory: Path) {
        runCatching {
            FileChannel.open(directory, StandardOpenOption.READ).use { channel -> channel.force(true) }
        }
    }
    private companion object {
        val INVALID_FILE_NAME_CHARACTERS: Set<Char> = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        const val SAVE_STREAM_BUFFER_BYTES: Int = 64 * 1024
        const val MAX_CAUSE_CHAIN_DEPTH: Int = 32
        val MIN_SANE_SAVED_AT: Instant = Instant.parse("2000-01-01T00:00:00Z")
        val MAX_SANE_SAVED_AT: Instant = Instant.parse("2100-12-31T23:59:59.999999999Z")
        val WINDOWS_RESERVED_NAMES: Regex = Regex(
            "^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$",
            RegexOption.IGNORE_CASE,
        )

        val CURRENT_ENVELOPE_FIELDS: Set<String> = setOf(
            "format",
            "schemaVersion",
            "savedAt",
            "state",
        )

        val CURRENT_STATE_FIELDS: Set<String> = setOf(
            "options",
            "catalogReference",
            "phase",
            "screen",
            "currentTime",
            "turn",
            "selectedTurnStep",
            "stocks",
            "corporateFundamentals",
            "fundFinancialStates",
            "referencePortfolioStates",
            "referencePortfolioLedger",
            "dailyResetStates",
            "optionStrategyStates",
            "cashCollateralizedPutSpreadStates",
            "etnStates",
            "etnLedger",
            "closedEndFundStates",
            "closedEndFundLedger",
            "fixedIncomeReferenceStates",
            "kofrIndexStates",
            "fixedIncomeRollLedger",
            "commoditySpotReferenceStates",
            "futuresReferenceStates",
            "futuresRollLedger",
            "futuresAllocationLedger",
            "equityReferenceStates",
            "equityReferenceLedger",
            "fundOfFundsStates",
            "fundOfFundsRebalanceLedger",
            "alternativeRiskPremiaStates",
            "alternativeRiskPremiaRebalanceLedger",
            "compositeReferenceStates",
            "compositeReferenceRebalanceLedger",
            "pendingFundFlowRates",
            "selectedStockId",
            "quotes",
            "priceHistory",
            "chartPriceHistory",
            "cashByCurrency",
            "holdings",
            "orders",
            "trades",
            "selectedOrderBook",
            "marketSessions",
            "macro",
            "externalMarketForcesTarget",
            "marketDynamicsSnapshot",
            "activeEvents",
            "newsEvents",
            "readEventIds",
            "readStockNewsEventIds",
            "portfolioSnapshots",
            "dailyStatistics",
            "currentBenchmarkValue",
            "benchmarkHistory",
            "transactionCosts",
            "realizedGains",
            "fifoCostBasisBook",
            "lastEvaluatedDistributionDateByStock",
            "pendingDistributionEntitlements",
            "distributionEntitlementOrigins",
            "dividendLedger",
            "foreignExchangeLedger",
            "cashAdjustmentLedger",
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

        val DIVIDEND_LEDGER_FIELDS: Set<String> = setOf(
            "id",
            "stockId",
            "exDate",
            "recordDate",
            "paidAt",
            "currency",
            "grossPerUnit",
            "entitledQuantity",
            "grossAmount",
            "withholdingTax",
            "netAmount",
            "exchangeRateToKrw",
            "taxBreakdown",
            "taxableIncomeAmount",
            "returnOfCapitalAmount",
            "excessReturnOfCapitalGainKrw",
            "accountingSequence",
        )

        val TAX_PAYMENT_NOTICE_FIELDS: Set<String> = setOf(
            "id",
            "taxYear",
            "dueDate",
            "currency",
            "amountKrw",
            "status",
            "paidAt",
            "accountingSequence",
            "message",
        )

        val PORTFOLIO_SNAPSHOT_FIELDS: Set<String> = setOf(
            "timestamp",
            "accountingSequenceExclusiveUpperBound",
            "cashByCurrency",
            "holdings",
            "distributionReceivableByCurrency",
            "exchangeRatesToKrw",
            "initialCapitalKrw",
            "realizedProfitKrw",
            "cumulativeCommissionKrw",
            "cumulativeTaxKrw",
            "holdingCostBasisKrw",
        )

        val DAILY_PORTFOLIO_STAT_FIELDS: Set<String> = setOf(
            "date",
            "totalAssetsKrw",
            "cashValueKrw",
            "stockValueKrw",
            "dailyReturn",
            "drawdown",
            "benchmarkValue",
            "usdKrw",
        )

        val ORDER_FIELDS: Set<String> = setOf(
            "id",
            "stockId",
            "side",
            "type",
            "quantity",
            "createdAt",
            "limitPrice",
            "status",
            "filledQuantity",
            "averageFilledPrice",
            "updatedAt",
            "timeInForce",
            "rejectionReason",
            "isNonMarketDisposition",
        )

        val TRADE_FIELDS: Set<String> = setOf(
            "id",
            "orderId",
            "stockId",
            "side",
            "quantity",
            "price",
            "currency",
            "executedAt",
            "commission",
            "tax",
            "settlementKind",
            "settlementDateOverride",
            "accountingSequence",
        )

        val TRANSACTION_COST_FIELDS: Set<String> = setOf(
            "tradeId",
            "stockId",
            "market",
            "paidAt",
            "currency",
            "commission",
            "saleTax",
            "exchangeRateToKrw",
            "feeBreakdown",
            "taxBreakdown",
        )

        val REALIZED_GAIN_RECORD_FIELDS: Set<String> = setOf(
            "tradeId",
            "stockId",
            "market",
            "soldAt",
            "settlementDate",
            "quantity",
            "proceeds",
            "costBasis",
            "commission",
            "saleTax",
            "currency",
            "exchangeRateToKrw",
            "taxTreatment",
            "assessmentNotes",
            "taxGrossProceedsKrw",
            "taxCostBasisKrw",
            "taxDirectSellingCostsKrw",
            "taxGainKrw",
            "taxableFinancialIncomeKrw",
        )

        val FIFO_COST_BASIS_BOOK_FIELDS: Set<String> = setOf("lots")

        val TAX_LOT_FIELDS: Set<String> = setOf(
            "lotId",
            "stockId",
            "acquiredOn",
            "remainingQuantity",
            "remainingCostBasisKrw",
        )

        val PENDING_DISTRIBUTION_ENTITLEMENT_FIELDS: Set<String> = setOf(
            "id",
            "originId",
            "stockId",
            "exDate",
            "recordDate",
            "payDate",
            "currency",
            "grossPerUnit",
            "entitledQuantity",
            "taxableCoverageRatio",
        )

        val DISTRIBUTION_ENTITLEMENT_ORIGIN_FIELDS: Set<String> = setOf(
            "id",
            "stockId",
            "exDate",
            "establishedAt",
            "amountBasis",
            "grossPerUnit",
            "entitledQuantity",
            "taxableCoverageRatio",
            "taxBasisExchangeRateToKrw",
            "returnOfCapitalAmount",
            "excessReturnOfCapitalGainKrw",
            "accruedDistributionPerUnitBeforeEx",
            "navPerUnitBeforeEx",
            "navPerUnitAfterEx",
            "accountingSequence",
        )

        val FOREIGN_EXCHANGE_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "executedAt",
            "fromCurrency",
            "toCurrency",
            "sourceAmount",
            "receivedAmount",
            "usdKrwRate",
            "spreadCostKrw",
            "automatic",
            "accountingSequence",
        )

        val CASH_ADJUSTMENT_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "adjustedAt",
            "currency",
            "balanceBefore",
            "balanceAfter",
            "reason",
            "accountingSequence",
        )

        val TAX_BREAKDOWN_FIELDS: Set<String> = setOf(
            "policyId",
            "calculatedOn",
            "taxableBase",
            "items",
            "warnings",
        )

        val FEE_BREAKDOWN_FIELDS: Set<String> = setOf(
            "calculatedOn",
            "currency",
            "items",
            "warnings",
        )

        val MONEY_AMOUNT_FIELDS: Set<String> = setOf("minorUnits", "currency")

        val TAX_LINE_ITEM_FIELDS: Set<String> = setOf(
            "id",
            "label",
            "amount",
            "jurisdiction",
            "category",
            "source",
            "effectiveRange",
        )

        val FEE_LINE_ITEM_FIELDS: Set<String> = setOf(
            "id",
            "label",
            "amount",
            "jurisdiction",
            "category",
            "source",
            "effectiveRange",
        )

        val RULE_SOURCE_FIELDS: Set<String> = setOf("title", "url", "accessedOn")

        val EFFECTIVE_DATE_RANGE_FIELDS: Set<String> = setOf("validFrom", "validThrough")

        val NEW_GAME_OPTIONS_FIELDS: Set<String> = setOf(
            "scenarioName",
            "difficultyName",
            "initialCapitalKrw",
            "seed",
            "usFractionalTrading",
            "autoExchange",
            "ironmanMode",
            "initialUsdKrw",
            "initialExternalMarketForces",
            "activeMods",
        )

        val ACTIVE_MOD_FIELDS: Set<String> = setOf(
            "id",
            "version",
            "settings",
            "contentFingerprint",
        )

        val CATALOG_REFERENCE_FIELDS: Set<String> = setOf(
            "schemaVersion",
            "orderedSources",
        )

        val CATALOG_SOURCE_REFERENCE_FIELDS: Set<String> = setOf(
            "sourceId",
            "contentSha256",
        )

        val STOCK_DEFINITION_FIELDS: Set<String> = setOf(
            "symbol",
            "name",
            "englishName",
            "market",
            "sector",
            "initialPrice",
            "volatility",
            "dividendYield",
            "marketCap",
            "sharesOutstanding",
            "description",
            "beta",
            "quantityStep",
            "lotSize",
            "etfProfile",
            "fundProductProfile",
            "instrumentTypeOverride",
            "behaviorProfile",
            "identityProfile",
            "industrySegments",
        )

        val BENCHMARK_REF_FIELDS: Set<String> = setOf(
            "benchmarkId",
            "version",
        )

        val FUND_PRODUCT_PROFILE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "replicationMode",
            "returnVariant",
            "legalStructure",
            "referenceExposure",
            "returnTransforms",
            "trackingErrorAnnualVolatility",
            "dailyResetTerms",
            "etnProductTerms",
            "etnIssuerCreditModelParameters",
            "closedEndFundTerms",
            "closedEndFundMarketModelParameters",
            "optionStrategyTerms",
            "cashCollateralizedPutSpreadTerms",
            "operationProfile",
        )

        val FUND_OPERATION_PROFILE_FIELDS: Set<String> = setOf(
            "managementStyle",
            "syntheticSwapFunding",
            "activeReturnModelSupport",
            "activeSyntheticSwapModelParameters",
            "provenance",
            "officialSourceUrls",
        )

        val ACTIVE_SYNTHETIC_SWAP_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
            "assumptionId",
            "activeAlphaAnnualMean",
            "activeAlphaAnnualVolatility",
            "annualSwapFundingSpread",
            "counterpartyDefaultHazardRateAnnual",
            "counterpartyRecoveryRate",
            "counterpartyExposureFraction",
        )

        val DAILY_RESET_TERMS_FIELDS: Set<String> = setOf(
            "productId",
            "reference",
            "directReferenceTerminationRule",
            "targetLeverage",
            "resetCalendar",
            "provenance",
            "officialSourceUrl",
            "modelParameters",
        )

        val DAILY_RESET_REFERENCE_FIELDS: Set<String> = setOf(
            "kind",
            "benchmarkRef",
            "instrumentId",
        )

        val DIRECT_REFERENCE_TERMINATION_RULE_FIELDS: Set<String> = setOf(
            "policy",
            "provenance",
            "officialSourceUrls",
            "assumptionId",
        )

        val DAILY_RESET_MODEL_PARAMETER_FIELDS: Set<String> = setOf(
            "annualFinancingSpread",
            "collateralYieldParticipation",
            "origin",
            "sourceUrl",
        )

        val ETN_PRODUCT_TERMS_FIELDS: Set<String> = setOf(
            "productId",
            "referenceId",
            "issuerId",
            "settlementCurrency",
            "statedPrincipalPerNote",
            "annualInvestorFeeRate",
            "investorFeeDayCountBasis",
            "issueDate",
            "maturityDate",
            "maturityValuationRule",
            "maturitySettlementMultiplier",
            "maturityIncludesAccruedCoupon",
            "couponRule",
            "callTerms",
            "accelerationTerms",
            "termsProvenance",
            "officialSourceUrl",
        )

        val ETN_ISSUER_CREDIT_MODEL_FIELDS: Set<String> = setOf(
            "issuerId",
            "initialCreditSpread",
            "initialHazardRate",
            "recoveryRate",
            "annualSpreadMeanReversionRate",
            "spreadShockAnnualVolatility",
            "origin",
            "sourceUrl",
        )

        val OPTION_STRATEGY_TERMS_FIELDS: Set<String> = setOf(
            "productId",
            "reference",
            "directReferenceTerminationRule",
            "kind",
            "tenorTradingDays",
            "rollCalendar",
            "rollLeadTradingDays",
            "provenance",
            "officialSourceUrls",
            "assumptionId",
            "premiumModel",
            "coveredCall",
            "optionIncome",
            "bufferedPutSpread",
        )

        val OPTION_PREMIUM_MODEL_FIELDS: Set<String> = setOf(
            "impliedVolatilityMultiplier",
            "soldPremiumCaptureRatio",
            "purchasedPremiumCostRatio",
            "implementationCostRatePerRoll",
            "origin",
            "sourceUrl",
            "calibrationId",
        )

        val CASH_COLLATERALIZED_PUT_SPREAD_TERMS_FIELDS: Set<String> = setOf(
            "productId",
            "cashBenchmarkRef",
            "optionReference",
            "directReferenceTerminationRule",
            "tenorTradingDays",
            "rollCalendar",
            "rollLeadTradingDays",
            "maximumSettlementLossRatio",
            "shortPutStrikeMoneyness",
            "longPutStrikeMoneyness",
            "provenance",
            "officialSourceUrls",
            "assumptionId",
            "premiumModel",
        )

        val COVERED_CALL_TERMS_FIELDS: Set<String> = setOf(
            "overwriteRatio",
            "callStrikeMoneyness",
        )

        val OPTION_INCOME_TERMS_FIELDS: Set<String> = setOf(
            "coreEquityAllocation",
            "optionIncomeAllocation",
            "upsideParticipation",
            "downsideParticipation",
            "callStrikeMoneyness",
        )

        val BUFFERED_PUT_SPREAD_TERMS_FIELDS: Set<String> = setOf(
            "outcomeNotionalRatio",
            "longPutStrikeMoneyness",
            "downsideBufferFraction",
            "downsideParticipationBeyondBuffer",
            "upsideCapFraction",
        )

        val ETN_SETTLEMENT_RULE_FIELDS: Set<String> = setOf(
            "method",
            "observationCount",
        )

        val ETN_COUPON_RULE_FIELDS: Set<String> = setOf(
            "kind",
            "paymentFrequencyMonths",
            "annualFixedRate",
            "participationRate",
            "accrualReducesIndicativeValue",
            "accruedCouponPaidAtTermination",
        )

        val ETN_CALL_TERMS_FIELDS: Set<String> = setOf(
            "issuerCallable",
            "issuerCallMayBePartial",
            "holderRedeemable",
            "minimumHolderRedemptionNotes",
            "holderRedemptionNoteIncrement",
            "minimumNoticeBusinessDays",
            "issuerCallValuationRule",
            "holderRedemptionValuationRule",
            "issuerCallSettlementMultiplier",
            "holderRedemptionSettlementMultiplier",
            "holderRedemptionChargeRate",
            "includesAccruedCoupon",
        )

        val ETN_ACCELERATION_TERMS_FIELDS: Set<String> = setOf(
            "issuerMayAccelerate",
            "partialAccelerationAllowed",
            "minimumPartialAccelerationNotes",
            "partialAccelerationNoteIncrement",
            "creditDefaultCausesAcceleration",
            "fullAccelerationValuationRule",
            "partialAccelerationValuationRule",
            "accelerationSettlementMultiplier",
            "nonCreditAccelerationIncludesAccruedCoupon",
            "creditDefaultIncludesAccruedCouponBeforeRecovery",
        )

        val CLOSED_END_FUND_TERMS_FIELDS: Set<String> = setOf(
            "fundId",
            "settlementCurrency",
            "distributionPolicy",
            "allowsTenderOffers",
            "allowsShareRepurchases",
            "allowsRightsOfferings",
            "allowsAtTheMarketOfferings",
            "allowsDebtLeverage",
            "allowsPreferredLeverage",
            "minimumDebtAssetCoverageRatio",
            "minimumPreferredAssetCoverageRatio",
            "termsProvenance",
            "officialSourceUrl",
        )

        val CLOSED_END_FUND_MARKET_MODEL_FIELDS: Set<String> = setOf(
            "fundId",
            "targetMarketDiscountRate",
            "annualDiscountMeanReversionRate",
            "initialDebtToGrossAssets",
            "initialPreferredToGrossAssets",
            "annualBorrowingSpread",
            "annualPreferredDistributionSpread",
            "discountShockAnnualVolatility",
            "origin",
            "sourceUrl",
        )

        val EXTERNAL_MARKET_FORCES_FIELDS: Set<String> = setOf(
            "chaos",
            "worldTension",
            "retailBuyingPower",
            "institutionalBuyingPower",
            "marketLiquidity",
            "economicMomentum",
        )

        val MARKET_REGIME_PROBABILITY_FIELDS: Set<String> = setOf(
            "calm",
            "balanced",
            "stress",
            "crisis",
        )

        val MARKET_DYNAMICS_SNAPSHOT_FIELDS: Set<String> = setOf(
            "effectiveForces",
            "regimeProbabilities",
            "conditionalVariance",
            "newsExcitation",
            "newsIntensity",
            "eventSentimentMemory",
            "liquidityStress",
            "retailFlow",
            "institutionalFlow",
            "downsideMemory",
            "previousObservedReturn",
            "randomState",
        )

        val MACRO_ENVIRONMENT_FIELDS: Set<String> = setOf(
            "policyRate",
            "policyRateChange",
            "koreanPolicyRate",
            "koreanPolicyRateChange",
            "inflationRate",
            "inflationSurprise",
            "growthRate",
            "growthSurprise",
            "usdKrw",
            "previousUsdKrw",
            "fxRatesToKrw",
            "previousFxRatesToKrw",
            "riskSentiment",
            "volatilityRegime",
            "retailOrderFlow",
            "institutionalOrderFlow",
            "liquidityStress",
            "newsIntensity",
            "marketHourlyReturns",
            "sectorHourlyReturns",
            "regionalEtfHourlyReturns",
            "marketChangeFromPreviousClose",
            "usCircuitBreakerLevel",
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

        val CORPORATE_FUNDAMENTAL_FIELDS: Set<String> = setOf(
            "stockId",
            "quarters",
            "bookEquity",
            "equityAtTtmStart",
            "appliedEarningsOccurrenceIds",
            "asOf",
        )

        val QUARTERLY_FINANCIAL_REPORT_FIELDS: Set<String> = setOf(
            "periodId",
            "reportedAt",
            "revenue",
            "netIncome",
            "dilutedShares",
            "sourceOccurrenceId",
        )

        val CORPORATE_ACTION_NEWS_REFERENCE_FIELDS: Set<String> = setOf(
            "occurrenceId",
            "transition",
            "stockId",
            "kind",
            "announcedAt",
            "effectiveNotBefore",
            "quantityMultiplier",
            "source",
            "rationale",
            "appliedAt",
            "accountingSequence",
            "cancelledAt",
            "cancellationReason",
            "cancellingListingEventId",
            "cancellingListingLedgerSequence",
            "cancellingListingStatus",
        )

        val FUND_FINANCIAL_STATE_FIELDS: Set<String> = setOf(
            "stockId",
            "navPerUnit",
            "indicativeValuePerUnit",
            "unitsOrNotesOutstanding",
            "lastNetFlow",
            "accruedDistributionPerUnit",
            "cumulativeUnitAdjustmentFactor",
            "lastCorporateActionAccountingSequence",
            "asOf",
        )

        val REFERENCE_PORTFOLIO_STATE_FIELDS: Set<String> = setOf(
            "portfolioId",
            "benchmarkRef",
            "positions",
            "methodologyPathState",
            "revision",
            "lastReconstitutionDate",
            "lastRebalanceDate",
            "nextReconstitutionDate",
            "nextRebalanceDate",
            "pendingSelectionDate",
            "pendingSelectionIncumbentAssetIds",
            "pendingPlans",
            "lastTurnoverRate",
            "estimatedAnnualIncomeYield",
            "asOf",
            "lastAppliedActionKind",
        )

        val REFERENCE_PORTFOLIO_POSITION_FIELDS: Set<String> = setOf(
            "assetId",
            "currentWeight",
            "targetWeight",
            "referenceFloatMarketValue",
            "enteredOn",
            "selectionRank",
        )

        val REFERENCE_PORTFOLIO_PLAN_FIELDS: Set<String> = setOf(
            "id",
            "portfolioId",
            "benchmarkRef",
            "kind",
            "selectionDate",
            "weightReferenceDate",
            "effectiveDate",
            "selectionIncumbentAssetIds",
            "selectionAvailabilityDate",
            "positions",
            "transitionBaselineWeights",
            "methodologyPathState",
            "addedAssetIds",
            "removedAssetIds",
            "weightReferenceMarketValues",
            "corporateAction",
        )

        val EQUITY_METHODOLOGY_PATH_STATE_FIELDS: Set<String> = setOf("entries")

        val EQUITY_METHODOLOGY_PATH_ENTRY_FIELDS: Set<String> = setOf(
            "assetId",
            "decimalValues",
            "booleanValues",
        )

        val REFERENCE_PORTFOLIO_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "portfolioId",
            "benchmarkRef",
            "kind",
            "selectionDate",
            "weightReferenceDate",
            "effectiveDate",
            "addedAssetIds",
            "removedAssetIds",
            "beforeCompositionHash",
            "afterCompositionHash",
            "turnoverRate",
            "resultingConstituentCount",
            "revision",
            "corporateAction",
        )

        val REFERENCE_PORTFOLIO_CORPORATE_ACTION_FIELDS: Set<String> = setOf(
            "eventId",
            "kind",
            "announcementDate",
            "effectiveDate",
            "primaryAssetId",
            "secondaryAssetId",
            "considerationKind",
            "valueTransferFraction",
            "followUpEffectiveDate",
        )

        val DAILY_RESET_STATE_FIELDS: Set<String> = setOf(
            "productId",
            "resetTradingDate",
            "referenceLevelAtReset",
            "navAtReset",
            "currentReferenceLevel",
            "currentNav",
            "cumulativeCarryLogReturn",
            "exposureNotional",
            "collateralBalance",
            "lifecycle",
            "cumulativeUnitAdjustmentFactor",
            "lastCorporateActionAccountingSequence",
            "asOf",
            "revision",
        )

        val OPTION_STRATEGY_STATE_FIELDS: Set<String> = setOf(
            "productId",
            "strategyKind",
            "rollCalendar",
            "currentReferenceLevel",
            "currentNav",
            "underlyingUnits",
            "cashBalance",
            "cycleReferenceLevel",
            "optionNotionalAtRoll",
            "cycleStartedOn",
            "remainingTradingDays",
            "remainingTimeYears",
            "lastProcessedTradingDate",
            "longCallUnits",
            "longCallStrike",
            "shortCallUnits",
            "shortCallStrike",
            "longPutUnits",
            "longPutStrike",
            "shortPutUnits",
            "shortPutStrike",
            "netOptionMark",
            "cycleGrossPremiumReceived",
            "cycleGrossPremiumPaid",
            "cycleImplementationCost",
            "cumulativePremiumReceived",
            "cumulativePremiumPaid",
            "cumulativeSettlementCashFlow",
            "cumulativeImplementationCost",
            "lifecycle",
            "cumulativeUnitAdjustmentFactor",
            "lastCorporateActionAccountingSequence",
            "asOf",
            "revision",
        )

        val CASH_COLLATERALIZED_PUT_SPREAD_STATE_FIELDS: Set<String> = setOf(
            "productId",
            "cashBenchmarkRef",
            "optionReference",
            "rollCalendar",
            "currentCashReferenceLevel",
            "currentOptionReferenceLevel",
            "currentNav",
            "cashBalance",
            "cycleOptionReferenceLevel",
            "navAtRoll",
            "optionNotionalAtRoll",
            "maximumSettlementLossAtRoll",
            "cycleStartedOn",
            "remainingTradingDays",
            "remainingTimeYears",
            "lastProcessedTradingDate",
            "longPutUnits",
            "longPutStrike",
            "shortPutUnits",
            "shortPutStrike",
            "netOptionMark",
            "cycleGrossPremiumReceived",
            "cycleGrossPremiumPaid",
            "cycleImplementationCost",
            "cumulativePremiumReceived",
            "cumulativePremiumPaid",
            "cumulativeSettlementCashFlow",
            "cumulativeImplementationCost",
            "lifecycle",
            "cumulativeUnitAdjustmentFactor",
            "lastCorporateActionAccountingSequence",
            "asOf",
            "revision",
        )

        val ETN_STATE_FIELDS: Set<String> = setOf(
            "productId",
            "referenceLevel",
            "feeAdjustedIndicativeValuePerNote",
            "notesOutstanding",
            "accruedCouponPerNote",
            "issuerCreditSpread",
            "issuerHazardRate",
            "issuerRecoveryRate",
            "indicativeValueObservationWindow",
            "lifecycle",
            "terminalCreditEvent",
            "asOf",
            "revision",
        )

        val ETN_INDICATIVE_VALUE_OBSERVATION_FIELDS: Set<String> = setOf(
            "observationDate",
            "indicativeValuePerNote",
        )

        val ETN_LEDGER_ENTRY_FIELDS: Set<String> = setOf(
            "id",
            "productId",
            "kind",
            "effectiveAt",
            "revision",
            "sequenceInBatch",
            "settlementCurrency",
            "referenceLevelBefore",
            "referenceLevelAfter",
            "indicativeValueBefore",
            "indicativeValueAfter",
            "notesOutstandingBefore",
            "notesOutstandingAfter",
            "notesIssued",
            "notesCancelled",
            "notesSettled",
            "notesDelta",
            "cashPaidToNoteholders",
            "cashReceivedFromNoteholders",
            "contractEvent",
            "settlementIndicativeValueObservations",
        )

        val CLOSED_END_FUND_STATE_FIELDS: Set<String> = setOf(
            "fundId",
            "grossAssets",
            "commonSharesOutstanding",
            "debtLiability",
            "preferredShareLiability",
            "navPerCommonShare",
            "undistributedNetInvestmentIncome",
            "distributionReserve",
            "marketDiscountRate",
            "cumulativeUnitAdjustmentFactor",
            "lastCorporateActionAccountingSequence",
            "asOf",
            "revision",
        )

        val CLOSED_END_FUND_LEDGER_ENTRY_FIELDS: Set<String> = setOf(
            "id",
            "fundId",
            "kind",
            "effectiveAt",
            "revision",
            "sequenceInBatch",
            "settlementCurrency",
            "capitalActionKind",
            "financingActionKind",
            "grossAssetsDelta",
            "commonSharesDelta",
            "debtLiabilityDelta",
            "preferredShareLiabilityDelta",
            "externalCashFlow",
            "cashToCommonShareholders",
            "netInvestmentIncomeDistribution",
            "realizedGainDistribution",
            "returnOfCapitalDistribution",
            "navPerShareBefore",
            "navPerShareAfter",
        )

        val FIXED_INCOME_REFERENCE_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "positions",
            "nominalCurves",
            "realCurves",
            "creditSpreads",
            "revision",
            "asOf",
        )

        val FIXED_INCOME_POSITION_FIELDS: Set<String> = setOf(
            "assetId",
            "kind",
            "currency",
            "creditQuality",
            "currentWeight",
            "targetWeight",
            "dirtyMarketValue",
            "remainingMaturityYears",
            "modifiedDurationYears",
            "convexityYearsSquared",
            "spreadDurationYears",
            "couponRateAnnual",
            "floatingSpreadAnnual",
            "floatingRateFloorAnnual",
            "inflationIndexRatio",
        )

        val YIELD_CURVE_SNAPSHOT_FIELDS: Set<String> = setOf(
            "currency",
            "annualZeroRates",
            "asOf",
        )

        val CREDIT_SPREAD_SNAPSHOT_FIELDS: Set<String> = setOf(
            "currency",
            "annualSpreads",
            "asOf",
        )

        val FIXED_INCOME_ROLL_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "removedAssetIds",
            "addedAssetIds",
            "effectiveAt",
            "revision",
        )

        val KOFR_INDEX_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "publishedRateAnnual",
            "publishedRateObservationDate",
            "indexLevel",
            "indexPublicationDate",
            "pendingRateAnnual",
            "pendingRateObservationDate",
            "revision",
            "asOf",
        )

        val COMMODITY_SPOT_REFERENCE_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "assetClass",
            "baseCurrency",
            "currentSpotLevel",
            "currentReferenceLevel",
            "currentSpotWeight",
            "currentCollateralWeight",
            "annualizedNetCarryRate",
            "asOf",
        )

        val FUTURES_REFERENCE_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "baseCurrency",
            "portfolioStyle",
            "allocationMode",
            "currentReferenceLevel",
            "sleeves",
            "revision",
            "asOf",
        )

        val FUTURES_SLEEVE_STATE_FIELDS: Set<String> = setOf(
            "sleeveId",
            "curveId",
            "assetClass",
            "rollCalendar",
            "priceReturnConvention",
            "fixedPriceReturnNotional",
            "currentWeight",
            "targetWeight",
            "currentSpotLevel",
            "frontContractId",
            "frontExpiryDate",
            "frontPrice",
            "frontContractWeight",
            "nextContractId",
            "nextExpiryDate",
            "nextPrice",
            "nextContractWeight",
            "lastRollTradingDate",
        )

        val FUTURES_ROLL_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "sleeveId",
            "rollTradingDate",
            "fromContractId",
            "toContractId",
            "transferredContractWeight",
            "frontWeightBefore",
            "frontWeightAfter",
            "normalizedCurveBasis",
            "promotedDeferredToFront",
            "successorContractId",
            "effectiveAt",
            "revision",
        )

        val FUTURES_ALLOCATION_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "weightsBefore",
            "weightsAfter",
            "effectiveAt",
            "revision",
        )

        val EQUITY_REFERENCE_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "region",
            "resolvedCountryCodes",
            "themeId",
            "positions",
            "factorExposure",
            "revision",
            "lastSelectionDate",
            "nextSelectionDate",
            "lastReweightDate",
            "nextReweightDate",
            "estimatedAnnualIncomeYield",
            "declaredTargetConstituentCount",
            "eligibleCandidateCount",
            "representativeBasketLimit",
            "profileFingerprint",
            "universeModelVersion",
            "universeFingerprint",
            "compositionHash",
            "asOf",
        )

        val EQUITY_REFERENCE_POSITION_FIELDS: Set<String> = setOf(
            "assetId",
            "region",
            "countryCode",
            "sector",
            "weight",
            "targetWeight",
            "representedConstituentCount",
            "selectionScore",
            "indicatedAnnualDividendYield",
            "enteredOn",
        )

        val EQUITY_REFERENCE_FACTOR_EXPOSURE_FIELDS: Set<String> = setOf(
            "countryWeights",
            "sectorWeights",
            "styleExposures",
            "idiosyncraticVolatilityWeights",
            "thematicExposure",
            "activeManagementExposure",
        )

        val EQUITY_REFERENCE_REBALANCE_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "kind",
            "selectionDate",
            "effectiveAt",
            "addedAssetIds",
            "removedAssetIds",
            "compositionHashBefore",
            "compositionHashAfter",
            "turnoverRate",
            "resultingPositionCount",
            "representedConstituentCount",
            "revision",
        )

        val FUND_OF_FUNDS_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "universe",
            "positions",
            "revision",
            "bootstrapDate",
            "lastSelectionDate",
            "nextSelectionDate",
            "lastReweightDate",
            "nextReweightDate",
            "estimatedAnnualIncomeYield",
            "eligibleCandidateCount",
            "profileFingerprint",
            "universeFingerprint",
            "compositionHash",
            "asOf",
        )

        val FUND_OF_FUNDS_POSITION_FIELDS: Set<String> = setOf(
            "candidateFundId",
            "category",
            "underlyingBenchmarkRef",
            "currentWeight",
            "targetWeight",
            "marketDiscountRate",
            "indicatedAnnualDistributionYield",
            "leverageRatio",
            "annualExpenseRate",
            "annualResidualVolatility",
            "liquidityScore",
            "selectionScore",
            "enteredOn",
            "asOf",
        )

        val FUND_OF_FUNDS_REBALANCE_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "kind",
            "effectiveDate",
            "effectiveAt",
            "addedCandidateFundIds",
            "removedCandidateFundIds",
            "compositionHashBefore",
            "compositionHashAfter",
            "oneWayTurnoverRate",
            "resultingFundCount",
            "revision",
        )

        val ALTERNATIVE_RISK_PREMIA_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "positions",
            "revision",
            "lastReweightDate",
            "nextReweightDate",
            "estimatedAnnualIncomeYield",
            "grossExposure",
            "netExposure",
            "effectiveDurationYears",
            "bootstrapCompositionHash",
            "profileFingerprint",
            "compositionHash",
            "asOf",
        )

        val ALTERNATIVE_RISK_PREMIA_POSITION_FIELDS: Set<String> = setOf(
            "driverId",
            "strategyFamily",
            "currentSignedWeight",
            "targetSignedWeight",
            "annualizedVariance",
            "trendSignal",
            "lastSourceLogReturn",
            "sourceAvailable",
            "sourceAnnualIncomeYield",
            "sourceDurationYears",
        )

        val ALTERNATIVE_RISK_PREMIA_REBALANCE_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "kind",
            "effectiveDate",
            "effectiveAt",
            "cashSubstitutedDriverIds",
            "compositionHashBefore",
            "compositionHashAfter",
            "turnoverRate",
            "resultingGrossExposure",
            "resultingNetExposure",
            "resultingDurationYears",
            "revision",
        )

        val COMPOSITE_REFERENCE_STATE_FIELDS: Set<String> = setOf(
            "benchmarkRef",
            "positions",
            "revision",
            "lastSelectionDate",
            "nextSelectionDate",
            "lastReweightDate",
            "nextReweightDate",
            "estimatedAnnualIncomeYield",
            "grossExposure",
            "netExposure",
            "effectiveDurationYears",
            "lastMortgageRateAnnual",
            "bootstrapCompositionHash",
            "profileFingerprint",
            "compositionHash",
            "asOf",
        )

        val COMPOSITE_REFERENCE_POSITION_FIELDS: Set<String> = setOf(
            "sleeveId",
            "direction",
            "currentWeightMagnitude",
            "targetWeightMagnitude",
            "annualizedVariance",
            "trendSignal",
            "lastSourceLogReturn",
            "sourceAvailable",
            "sourceAnnualIncomeYield",
            "sourceDurationYears",
            "conditionalPrepaymentRateAnnual",
        )

        val COMPOSITE_REFERENCE_REBALANCE_RECORD_FIELDS: Set<String> = setOf(
            "id",
            "benchmarkRef",
            "kind",
            "effectiveDate",
            "effectiveAt",
            "addedSleeveIds",
            "removedSleeveIds",
            "cashSubstitutedSleeveIds",
            "compositionHashBefore",
            "compositionHashAfter",
            "turnoverRate",
            "resultingGrossExposure",
            "resultingNetExposure",
            "resultingDurationYears",
            "revision",
        )

        const val MIN_FUND_CONSTITUENT_WEIGHT: Double = 0.0
        const val MAX_FUND_SELECTION_RANK: Int = 1_000_000
        const val MAX_REFERENCE_ASSET_ID_LENGTH: Int = 200
        const val MAX_REFERENCE_PORTFOLIO_ID_LENGTH: Int = 200
        const val MAX_REFERENCE_LEDGER_ID_LENGTH: Int = 512
        const val MAX_DAILY_RESET_PRODUCT_ID_LENGTH: Int = 200
        const val MIN_FIXED_INCOME_MARKET_VALUE: Double = 1e-9
        const val MAX_FIXED_INCOME_MARKET_VALUE: Double = 1e24
        const val MAX_FIXED_INCOME_YEARS: Double = 100.0
        const val MAX_FIXED_INCOME_CONVEXITY: Double = 10_000.0
        const val MIN_FIXED_INCOME_RATE: Double = -0.10
        const val MAX_FIXED_INCOME_POSITION_RATE: Double = 2.0
        const val MAX_YIELD_CURVE_RATE: Double = 1.0
        const val MAX_FIXED_INCOME_CREDIT_SPREAD: Double = 2.0
        const val MIN_FIXED_INCOME_INDEX_RATIO: Double = 0.01
        const val MAX_FIXED_INCOME_INDEX_RATIO: Double = 100.0
        const val MAX_COMMODITY_REFERENCE_COUNT: Int = 4_096
        const val MAX_EQUITY_REFERENCE_COUNT: Int = 1_024
        const val MAX_FUND_OF_FUNDS_REFERENCE_COUNT: Int = 1_024
        const val MAX_STRUCTURED_REFERENCE_COUNT: Int = 1_024
        const val MAX_EQUITY_COUNTRY_CODES: Int = 64
        const val MAX_EQUITY_REPRESENTED_COUNT: Int = 10_000
        const val EQUITY_IDIOSYNCRATIC_BUCKET_COUNT: Int = 32
        const val MIN_EQUITY_REFERENCE_WEIGHT: Double = 1e-12
        const val MAX_EQUITY_FACTOR_EXPOSURE: Double = 3.0
        const val MAX_EQUITY_IDIOSYNCRATIC_WEIGHT: Double = 5.0
        const val MIN_FUND_OF_FUNDS_WEIGHT: Double = 1e-12
        const val MIN_FUND_OF_FUNDS_DISCOUNT: Double = -0.95
        const val MAX_FUND_OF_FUNDS_PREMIUM: Double = 2.0
        const val MAX_FUND_OF_FUNDS_LEVERAGE: Double = 5.0
        const val MAX_FUND_OF_FUNDS_EXPENSE_RATE: Double = 0.25
        const val MAX_FUND_OF_FUNDS_RESIDUAL_VOLATILITY: Double = 3.0
        const val MAX_FUTURES_SLEEVES: Int = 128
        const val MIN_COMMODITY_REFERENCE_LEVEL: Double = 1e-12
        const val MAX_COMMODITY_REFERENCE_LEVEL: Double = 1e24
        const val MIN_COMMODITY_CARRY_RATE: Double = -2.0
        const val MAX_COMMODITY_CARRY_RATE: Double = 2.0
        const val MIN_FUTURES_PRICE: Double = -1e12
        const val MAX_FUTURES_PRICE: Double = 1e12
        const val COMMODITY_WEIGHT_EPSILON: Double = 1e-8
        const val MAX_FUND_STRUCTURE_ID_LENGTH: Int = 256
        const val MIN_FUND_STRUCTURE_VALUE: Double = 1e-9
        const val MAX_FUND_STRUCTURE_VALUE: Double = 1e18
        const val MAX_FUND_STRUCTURE_RATE: Double = 100.0
        const val MAX_FUND_STRUCTURE_EXACT_QUANTITY: Long = 9_000_000_000_000_000L
        const val MAX_FUND_STRUCTURE_BATCH_ENTRIES: Int = 100
        const val MAX_CEF_ASSET_COVERAGE_RATIO: Double = 1_000.0
        const val MAX_OPTION_TENOR_TRADING_DAYS: Int = 504
        const val MAX_OPTION_TIME_YEARS: Double = 2.0
        const val MAX_CASH_PUT_SPREAD_AMOUNT: Double = 1e24
        const val MAX_OPTION_OFFICIAL_SOURCE_URLS: Int = 16
        const val MIN_OPTION_POSITIVE_RATIO: Double = 1e-9
        const val MIN_OPTION_STRIKE_MONEYNESS: Double = 0.05
        const val OPTION_WEIGHT_EPSILON: Double = 1e-10
        const val MIN_CASH_PUT_SPREAD_WIDTH: Double = 0.001
        val OPTION_ASSUMPTION_ID: Regex = Regex("[a-z0-9][a-z0-9._-]{2,159}")
        const val MAX_DAILY_RESET_ABS_LEVERAGE: Double = 5.0
        const val MAX_SAVE_URL_LENGTH: Int = 2_048
        const val MAX_TAX_TEXT_LENGTH: Int = 512
        const val MAX_TAX_WARNING_LENGTH: Int = 2_048
        val REFERENCE_COMPOSITION_HASH: Regex = Regex("[0-9a-f]{16}")
        val REFERENCE_EVENT_ID: Regex = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,511}")
        val REFERENCE_ASSET_ID: Regex = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{0,199}")
        val EQUITY_COUNTRY_CODE_PATTERN: Regex = Regex("[A-Z]{2}")
        val EQUITY_THEME_ID_PATTERN: Regex = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")
        val EQUITY_REFERENCE_ASSET_ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9:._-]{2,199}")
        val EQUITY_UNIVERSE_VERSION_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{2,159}")
        val FUND_OF_FUNDS_CANDIDATE_ID_PATTERN: Regex = Regex("sim-fof:[a-z0-9._-]+:[0-9]{3}")
        val COMPOSITE_MEMBER_ID_PATTERN: Regex = Regex("[a-z0-9][a-z0-9._-]{2,119}")
        val DAILY_RESET_PRODUCT_ID: Regex = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
        val DAILY_RESET_INSTRUMENT_ID: Regex = Regex("[A-Z_]+:[A-Za-z0-9.]+")

        fun createSaveGson(): Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, InstantTypeAdapter().nullSafe())
            .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter().nullSafe())
            .enableComplexMapKeySerialization()
            .disableHtmlEscaping()
            .serializeNulls()
            .setStrictness(Strictness.STRICT)
            .create()

        fun now(): Instant = Instant.fromEpochMilliseconds(System.currentTimeMillis())

        fun safeMessage(error: Throwable): String = error.message?.takeIf(String::isNotBlank)
            ?: error::class.simpleName
            ?: "알 수 없는 오류"
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

private fun JsonObject.requiredStrictString(name: String, path: String): String =
    required(name).requireStrictString(path)

private fun JsonObject.requiredBoundedNonBlankString(
    name: String,
    path: String,
    maxLength: Int,
): String = requiredStrictString(name, path).also { value ->
    if (value.isBlank() || value.length > maxLength) {
        throw JsonParseException("필드 '$path'의 길이가 올바르지 않습니다.")
    }
}

private fun JsonObject.requiredLocalDate(name: String, path: String): LocalDate {
    val raw = requiredStrictString(name, path)
    return try {
        LocalDate.parse(raw).also { parsed ->
            if (parsed.toString() != raw) throw JsonParseException("필드 '$path'의 날짜 형식이 정규화되지 않았습니다.")
        }
    } catch (error: RuntimeException) {
        if (error is JsonParseException) throw error
        throw JsonParseException("필드 '$path'는 YYYY-MM-DD 날짜여야 합니다.", error)
    }
}

private fun JsonObject.nullableLocalDate(name: String, path: String): LocalDate? {
    requireMember(name)
    return if (get(name).isJsonNull) null else requiredLocalDate(name, path)
}

private fun JsonObject.requiredInstant(name: String, path: String): Instant {
    val raw = requiredStrictString(name, path)
    return try {
        Instant.parse(raw).also { parsed ->
            if (parsed.toString() != raw) throw JsonParseException("필드 '$path'의 시각 형식이 정규화되지 않았습니다.")
        }
    } catch (error: RuntimeException) {
        if (error is JsonParseException) throw error
        throw JsonParseException("필드 '$path'는 ISO-8601 시각이어야 합니다.", error)
    }
}

private fun JsonObject.nullableInstant(name: String, path: String): Instant? {
    requireMember(name)
    return if (get(name).isJsonNull) null else requiredInstant(name, path)
}

private fun JsonObject.nullableStrictString(name: String, path: String): String? {
    requireMember(name)
    val element = get(name)
    return if (element.isJsonNull) null else element.requireStrictString(path)
}

private fun JsonElement.requireStrictString(path: String): String {
    val primitive = takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
    if (primitive?.isString != true) {
        throw JsonParseException("필드 '$path'은 문자열이어야 합니다.")
    }
    return primitive.asString
}

private fun JsonElement.requireFiniteDouble(path: String): Double = try {
    val primitive = takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        ?: throw JsonParseException("필드 '$path'은 숫자여야 합니다.")
    if (!primitive.isNumber) throw JsonParseException("필드 '$path'은 숫자여야 합니다.")
    primitive.asDouble.takeIf(Double::isFinite)
        ?: throw JsonParseException("필드 '$path'은 유한한 숫자여야 합니다.")
} catch (error: RuntimeException) {
    if (error is JsonParseException) throw error
    throw JsonParseException("필드 '$path'은 유한한 숫자여야 합니다.", error)
}

private fun JsonElement.requireExactInt(path: String): Int = try {
    val primitive = takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        ?: throw JsonParseException("필드 '$path'은 정수여야 합니다.")
    if (!primitive.isNumber) throw JsonParseException("필드 '$path'은 정수여야 합니다.")
    primitive.asString.toIntOrNull()
        ?: throw JsonParseException("필드 '$path'은 Int 범위의 10진 정수여야 합니다.")
} catch (error: RuntimeException) {
    if (error is JsonParseException) throw error
    throw JsonParseException("필드 '$path'은 정수여야 합니다.", error)
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

private fun JsonObject.requiredLong(name: String, path: String): Long = try {
    val primitive = required(name).takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        ?: throw JsonParseException("필드 '$path'은 정수여야 합니다.")
    if (!primitive.isNumber) throw JsonParseException("필드 '$path'은 정수여야 합니다.")
    primitive.asString.toLongOrNull()
        ?: throw JsonParseException("필드 '$path'은 Long 범위의 10진 정수여야 합니다.")
} catch (error: RuntimeException) {
    if (error is JsonParseException) throw error
    throw JsonParseException("필드 '$path'은 정수여야 합니다.", error)
}

private fun JsonObject.nullableLong(name: String, path: String): Long? {
    requireMember(name)
    return if (get(name).isJsonNull) null else requiredLong(name, path)
}

/** Strict wire boundary for the cumulative listed-unit denomination lineage. */
private fun JsonObject.requireUnitAdjustmentMarker(path: String) {
    val factor = requiredFiniteDouble(
        "cumulativeUnitAdjustmentFactor",
        "$path.cumulativeUnitAdjustmentFactor",
    )
    val sequence = nullableLong(
        "lastCorporateActionAccountingSequence",
        "$path.lastCorporateActionAccountingSequence",
    )
    if (factor <= 0.0 || sequence?.let { it <= 0L } == true || sequence == null && factor != 1.0) {
        throw JsonParseException("필드 '$path'의 누적 좌수조정 배수·기업행동 시퀀스가 유효하지 않습니다.")
    }
}

private fun JsonObject.requiredBoolean(name: String, path: String): Boolean = try {
    val primitive = required(name).takeIf(JsonElement::isJsonPrimitive)?.asJsonPrimitive
        ?: throw JsonParseException("필드 '$path'은 boolean이어야 합니다.")
    if (!primitive.isBoolean) throw JsonParseException("필드 '$path'은 boolean이어야 합니다.")
    primitive.asBoolean
} catch (error: RuntimeException) {
    if (error is JsonParseException) throw error
    throw JsonParseException("필드 '$path'은 boolean이어야 합니다.", error)
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

private fun JsonObject.nullableFiniteDouble(name: String, path: String): Double? {
    requireMember(name)
    val element = get(name)
    if (element.isJsonNull) return null
    return requiredFiniteDouble(name, path)
}
