package com.amond.kmpbook.persistence

import com.amond.kmpbook.domain.data.StockCatalog
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertReleaseRule
import com.amond.kmpbook.domain.model.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.KrxSidecarState
import com.amond.kmpbook.domain.model.KrxViState
import com.amond.kmpbook.domain.model.ListingFinalDisposition
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingNoticeLevel
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.ReferenceCurrency
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TradingDayWindow
import com.amond.kmpbook.domain.model.TradingHaltOrderPolicy
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.model.UsLuldTier
import com.amond.kmpbook.domain.simulation.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.ListingLifecycleEngine
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.MarketIndexEngine
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.TradingProtectionEngine
import com.amond.kmpbook.domain.tax.AnnualStockTaxCalculator
import com.amond.kmpbook.domain.tax.AnnualStockTaxRequest
import com.amond.kmpbook.domain.tax.BrokerFeeCalculator
import com.amond.kmpbook.domain.tax.BrokerFeeRequest
import com.amond.kmpbook.domain.tax.DividendTaxCalculator
import com.amond.kmpbook.domain.tax.DividendTaxClass
import com.amond.kmpbook.domain.tax.DividendTaxRequest
import com.amond.kmpbook.domain.tax.DomesticSaleTaxCalculator
import com.amond.kmpbook.domain.tax.DomesticSaleTaxRequest
import com.amond.kmpbook.domain.tax.ForeignInstrumentTaxClass
import com.amond.kmpbook.domain.tax.MoneyAmount
import com.amond.kmpbook.domain.tax.RealizedStockGain
import com.amond.kmpbook.domain.tax.StockGainTaxTreatment
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.presentation.BenchmarkPoint
import com.amond.kmpbook.presentation.DailyPortfolioStat
import com.amond.kmpbook.presentation.DailyTradingSurveillancePoint
import com.amond.kmpbook.presentation.DividendLedgerEntry
import com.amond.kmpbook.presentation.ForeignExchangeRecord
import com.amond.kmpbook.presentation.NewGameOptions
import com.amond.kmpbook.presentation.RealizedGainRecord
import com.amond.kmpbook.presentation.SimulatorRuntime
import com.amond.kmpbook.presentation.SimulatorUiState
import com.amond.kmpbook.presentation.SimulatorViewModel
import com.amond.kmpbook.presentation.TaxPaymentNotice
import com.amond.kmpbook.presentation.TransactionCostRecord
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class DesktopGameSaveStorageTest {
    private lateinit var temporaryDirectory: Path

    @BeforeTest
    fun createTemporaryDirectory() {
        temporaryDirectory = Files.createTempDirectory("market-ledger-save-test-")
    }

    @Test
    fun windowsDefaultSavePathUsesRoamingAppDataAndEnglishProductName() {
        val appData = temporaryDirectory.resolve("AppData/Roaming").toString()

        val path = defaultGameSavePath(
            osName = "Windows 11",
            userHome = temporaryDirectory.toString(),
            appData = appData,
        )

        assertEquals(
            Paths.get(appData, "MarketLedger2040", "savegame.json").toAbsolutePath().normalize(),
            path,
        )
    }

    @Test
    fun windowsDefaultSavePathFallsBackToUserRoamingDirectory() {
        val path = defaultGameSavePath(
            osName = "Windows 11",
            userHome = temporaryDirectory.toString(),
            appData = null,
        )

        assertEquals(
            temporaryDirectory.resolve("AppData/Roaming/MarketLedger2040/savegame.json").toAbsolutePath().normalize(),
            path,
        )
    }

    @AfterTest
    fun removeTemporaryDirectory() {
        temporaryDirectory.toFile().deleteRecursively()
    }

    @Test
    fun constructionExistsAndMissingLoadDoNotCreateSaveDirectory(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("not-created/savegame.json")
        val storage = GameSaveStorage(file.toString())

        assertFalse(file.parent.exists())
        assertIs<GameSavePresenceResult.Missing>(storage.exists())
        assertIs<GameLoadResult.NotFound>(storage.load())
        assertFalse(file.parent.exists())
        assertEquals(file.toAbsolutePath().normalize().toString(), storage.savePath)
    }

    @Test
    fun completeSimulatorStateRoundTripsThroughVersionedUtf8Envelope(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("nested/savegame.json")
        val storage = GameSaveStorage(file.toString())
        val original = richStateWithLifecycleAndProtection()

        val saved = assertIs<GameSaveResult.Success>(storage.save(original))
        assertTrue(file.exists())
        assertEquals(CURRENT_GAME_SAVE_SCHEMA_VERSION, saved.metadata.schemaVersion)
        assertEquals(original.currentTime, saved.metadata.gameTime)
        assertTrue(saved.bytesWritten > 0L)

        val json = file.readText(StandardCharsets.UTF_8)
        assertTrue(json.contains("\"format\": \"$GAME_SAVE_FORMAT_ID\""))
        assertTrue(json.contains("\"schemaVersion\": $CURRENT_GAME_SAVE_SCHEMA_VERSION"))
        assertTrue(json.contains("\"currentTime\": \"2026-08-07T01:00:00Z\""))
        assertTrue(json.contains("\"settlementDate\": \"2026-08-10\""))

        val present = assertIs<GameSavePresenceResult.Present>(storage.exists())
        assertEquals(saved.bytesWritten, present.sizeBytes)
        val loaded = assertIs<GameLoadResult.Success>(storage.load())
        assertEquals(original, loaded.state)
        assertEquals(saved.metadata, loaded.metadata)
        assertEquals(original.eventEngineSnapshot, loaded.state.eventEngineSnapshot)
        assertEquals(original.annualTaxLedgers, loaded.state.annualTaxLedgers)
        assertEquals(original.priceHistory, loaded.state.priceHistory)
        assertEquals(original.portfolioSnapshots, loaded.state.portfolioSnapshots)
        assertEquals(original.marketSessions, loaded.state.marketSessions)
        assertEquals(original.cashByCurrency, loaded.state.cashByCurrency)
        assertEquals(original.macro.marketHourlyReturns, loaded.state.macro.marketHourlyReturns)
        assertEquals(original.macro.sectorHourlyReturns, loaded.state.macro.sectorHourlyReturns)
        assertEquals(original.listingLifecycleStates, loaded.state.listingLifecycleStates)
        assertEquals(original.listingLifecycleLedger, loaded.state.listingLifecycleLedger)
        assertEquals(original.tradingProtectionSnapshot, loaded.state.tradingProtectionSnapshot)
        assertEquals(original.dailyTradingSurveillance, loaded.state.dailyTradingSurveillance)

        val leftovers = Files.list(file.parent).use { paths ->
            paths.filter { it.fileName.toString().startsWith(".savegame-") }.count()
        }
        assertEquals(0L, leftovers)
    }

    @Test
    fun versionOneAndTwoEnvelopesAreRejectedAsUnsupported(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("old-savegame.json")
        val storage = GameSaveStorage(file.toString())
        assertIs<GameSaveResult.Success>(storage.save(richState()))
        val currentJson = file.readText(StandardCharsets.UTF_8)

        listOf(1, 2).forEach { version ->
            val root = JsonParser.parseString(currentJson).asJsonObject
            root.addProperty("schemaVersion", version)
            file.writeText(root.toString(), StandardCharsets.UTF_8)

            val failure = assertIs<GameLoadResult.Failure>(storage.load())
            assertEquals(GameSaveErrorCode.UNSUPPORTED_SCHEMA, failure.error.code)
            assertTrue(failure.error.message.contains(version.toString()))
        }
    }

    @Test
    fun currentSchemaRejectsMissingRequiredStateFields(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("incomplete-current-savegame.json")
        val storage = GameSaveStorage(file.toString())
        assertIs<GameSaveResult.Success>(storage.save(richState()))
        val currentJson = file.readText(StandardCharsets.UTF_8)

        listOf(
            "options",
            "pendingEtfReferenceReturns",
            "marketIndices",
            "taxExchangeRatesByTradeId",
            "listingLifecycleStates",
            "tradingProtectionSnapshot",
        ).forEach { field ->
            val root = JsonParser.parseString(currentJson).asJsonObject
            root.getAsJsonObject("state").remove(field)
            file.writeText(root.toString(), StandardCharsets.UTF_8)

            val failure = assertIs<GameLoadResult.Failure>(storage.load())
            assertEquals(GameSaveErrorCode.CORRUPTED_FILE, failure.error.code)
            assertTrue(failure.error.message.contains(field))
        }
    }

    @Test
    fun currentSchemaRejectsRemovedLegacyStateFields(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("legacy-fields-current-savegame.json")
        val storage = GameSaveStorage(file.toString())
        assertIs<GameSaveResult.Success>(storage.save(richState()))
        val currentJson = file.readText(StandardCharsets.UTF_8)

        listOf("usCircuitBreakerState", "terminatedInstrumentIds").forEach { field ->
            val root = JsonParser.parseString(currentJson).asJsonObject
            root.getAsJsonObject("state").add(field, JsonObject())
            file.writeText(root.toString(), StandardCharsets.UTF_8)

            val failure = assertIs<GameLoadResult.Failure>(storage.load())
            assertEquals(GameSaveErrorCode.CORRUPTED_FILE, failure.error.code)
            assertTrue(failure.error.message.contains(field))
        }
    }

    @Test
    fun explicitSaveReplacesPriorStateAndDeleteIsScopedToSaveFile(): Unit = runBlocking {
        val sibling = temporaryDirectory.resolve("keep-me.txt")
        sibling.writeText("keep", StandardCharsets.UTF_8)
        val file = temporaryDirectory.resolve("savegame.json")
        val storage = GameSaveStorage(file.toString())
        val first = richState()
        val second = first.copy(turn = 999L, lastMessage = "두 번째 저장")

        assertIs<GameSaveResult.Success>(storage.save(first))
        assertIs<GameSaveResult.Success>(storage.save(second))
        assertEquals(second, assertIs<GameLoadResult.Success>(storage.load()).state)
        assertIs<GameSaveDeleteResult.Deleted>(storage.delete())
        assertFalse(file.exists())
        assertEquals("keep", sibling.readText(StandardCharsets.UTF_8))
        assertIs<GameSaveDeleteResult.NotFound>(storage.delete())
    }

    @Test
    fun malformedJsonAndInvalidUtf8ReturnCorruptionWithoutThrowing(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("savegame.json")
        val storage = GameSaveStorage(file.toString())
        file.writeText("{ not-json", StandardCharsets.UTF_8)

        val malformed = assertIs<GameLoadResult.Failure>(storage.load())
        assertEquals(GameSaveErrorCode.CORRUPTED_FILE, malformed.error.code)

        file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        val invalidUtf8 = assertIs<GameLoadResult.Failure>(storage.load())
        assertEquals(GameSaveErrorCode.CORRUPTED_FILE, invalidUtf8.error.code)
    }

    @Test
    fun otherNonCurrentSchemasAreRejectedAsUnsupported(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("savegame.json")
        val storage = GameSaveStorage(file.toString())
        assertIs<GameSaveResult.Success>(storage.save(richState()))
        val currentJson = file.readText(StandardCharsets.UTF_8)

        listOf(-1, 0, CURRENT_GAME_SAVE_SCHEMA_VERSION + 1, 99).forEach { version ->
            val unsupportedJson = currentJson.replace(
                "\"schemaVersion\": $CURRENT_GAME_SAVE_SCHEMA_VERSION",
                "\"schemaVersion\": $version",
            )
            file.writeText(unsupportedJson, StandardCharsets.UTF_8)

            val failure = assertIs<GameLoadResult.Failure>(storage.load())
            assertEquals(GameSaveErrorCode.UNSUPPORTED_SCHEMA, failure.error.code)
            assertTrue(failure.error.message.contains(version.toString()))
        }
    }

    @Test
    fun malformedListingLifecycleStatesAreRejectedBeforeWriting(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("invalid-listing-savegame.json")
        val storage = GameSaveStorage(file.toString())
        val valid = richStateWithLifecycleAndProtection()
        val stockId = valid.listingLifecycleStates.keys.first()
        val listing = valid.listingLifecycleStates.getValue(stockId)
        val event = valid.listingLifecycleLedger.single()
        val malformedStates = listOf(
            valid.copy(
                listingLifecycleStates = valid.listingLifecycleStates - stockId +
                    ("NASDAQ:WRONG" to listing),
            ),
            valid.copy(
                listingLifecycleStates = valid.listingLifecycleStates +
                    (stockId to listing.copy(lastEvaluatedTradingDate = LocalDate(2041, 1, 1))),
            ),
            valid.copy(
                listingLifecycleStates = valid.listingLifecycleStates +
                    (stockId to listing.copy(
                        status = ListingLifecycleStatus.DELISTED,
                        finalDisposition = ListingFinalDisposition(
                            type = ListingFinalDispositionType.WORTHLESS_DISPOSITION,
                            effectiveOn = valid.currentDate,
                        ),
                    )),
            ),
            valid.copy(
                listingLifecycleLedger = listOf(event, event.copy(id = "${event.id}:duplicate")),
            ),
        )

        malformedStates.forEach { malformed ->
            val failure = assertIs<GameSaveResult.Failure>(storage.save(malformed))
            assertEquals(GameSaveErrorCode.INVALID_STATE, failure.error.code)
        }
        assertFalse(file.exists())
    }

    @Test
    fun malformedTradingProtectionStatesAreRejectedBeforeWriting(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("invalid-protection-savegame.json")
        val storage = GameSaveStorage(file.toString())
        val valid = richStateWithLifecycleAndProtection()
        val protection = valid.tradingProtectionSnapshot
        val krxCircuitBreaker = protection.krxCircuitBreakers.getValue(Market.KOSPI)
        val luldEntry = protection.usLuldStates.entries.single()
        val mwcb = requireNotNull(protection.usMarketWideCircuitBreaker)
        val malformedSnapshots = listOf(
            protection.copy(
                krxCircuitBreakers = mapOf(Market.KOSDAQ to krxCircuitBreaker),
            ),
            protection.copy(
                usLuldStates = mapOf("NASDAQ:WRONG" to luldEntry.value),
            ),
            protection.copy(
                usMarketWideCircuitBreaker = mwcb.copy(
                    venueStatuses = mwcb.venueStatuses - Market.NYSE,
                ),
            ),
        )

        malformedSnapshots.forEach { malformed ->
            val failure = assertIs<GameSaveResult.Failure>(
                storage.save(valid.copy(tradingProtectionSnapshot = malformed)),
            )
            assertEquals(GameSaveErrorCode.INVALID_STATE, failure.error.code)
        }
        assertFalse(file.exists())
    }

    @Test
    fun malformedProtectionIdentityInJsonIsRejectedOnLoad(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("corrupted-protection-savegame.json")
        val storage = GameSaveStorage(file.toString())
        assertIs<GameSaveResult.Success>(storage.save(richStateWithLifecycleAndProtection()))

        val root = JsonParser.parseString(file.readText(StandardCharsets.UTF_8)).asJsonObject
        val breakers = root.getAsJsonObject("state")
            .getAsJsonObject("tradingProtectionSnapshot")
            .getAsJsonObject("krxCircuitBreakers")
        breakers.getAsJsonObject("KOSPI").addProperty("market", "KOSDAQ")
        file.writeText(root.toString(), StandardCharsets.UTF_8)

        val failure = assertIs<GameLoadResult.Failure>(storage.load())
        assertEquals(GameSaveErrorCode.INVALID_STATE, failure.error.code)
    }

    @Test
    fun maximumFileSizeIsEnforcedForReadAndBeforeWrite(): Unit = runBlocking {
        val oversizedReadFile = temporaryDirectory.resolve("oversized-read.json")
        oversizedReadFile.writeBytes(ByteArray(65) { 'x'.code.toByte() })
        val readStorage = GameSaveStorage(oversizedReadFile.toString(), maxFileSizeBytes = 64L)
        val readFailure = assertIs<GameLoadResult.Failure>(readStorage.load())
        assertEquals(GameSaveErrorCode.FILE_TOO_LARGE, readFailure.error.code)
        assertEquals(
            GameSaveErrorCode.FILE_TOO_LARGE,
            assertIs<GameSavePresenceResult.Failure>(readStorage.exists()).error.code,
        )

        val writeFile = temporaryDirectory.resolve("oversized-write.json")
        val writeStorage = GameSaveStorage(writeFile.toString(), maxFileSizeBytes = 64L)
        val writeFailure = assertIs<GameSaveResult.Failure>(writeStorage.save(richState()))
        assertEquals(GameSaveErrorCode.FILE_TOO_LARGE, writeFailure.error.code)
        assertFalse(writeFile.exists())
    }

    @Test
    fun fullCatalogWithSaturatedRecentHistoryStaysUnderSaveLimit(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("full-catalog-savegame.json")
        val storage = GameSaveStorage(file.toString())
        val initial = SimulatorViewModel().apply {
            newGame(NewGameOptions(seed = 20_260_807L))
        }.currentState
        val saturated = initial.copy(
            priceHistory = initial.priceHistory.mapValues { (_, bars) ->
                List(SimulatorRuntime.MAX_RECENT_BARS) { bars.single() }
            },
            marketIndexHistory = initial.marketIndexHistory.mapValues { (_, values) ->
                List(SimulatorRuntime.MAX_INDEX_BARS) { index ->
                    values.single().copy(
                        timestamp = initial.currentTime -
                            (SimulatorRuntime.MAX_INDEX_BARS - index - 1).hours,
                    )
                }
            },
        )

        val saved = assertIs<GameSaveResult.Success>(storage.save(saturated))
        assertTrue(saved.bytesWritten < DEFAULT_MAX_GAME_SAVE_FILE_BYTES)
        val loaded = assertIs<GameLoadResult.Success>(storage.load()).state
        assertEquals(StockCatalog.all.size, loaded.stocks.size)
        assertTrue(loaded.priceHistory.values.all { it.size == SimulatorRuntime.MAX_RECENT_BARS })
        assertTrue(loaded.marketIndexHistory.values.all { it.size == SimulatorRuntime.MAX_INDEX_BARS })
    }

    private fun richState(): SimulatorUiState {
        val now = Instant.parse("2026-08-07T01:00:00Z")
        val nextHour = Instant.parse("2026-08-07T02:00:00Z")
        val usStock = StockDefinition(
            symbol = "TEST",
            name = "테스트 테크",
            englishName = "Test Technology",
            market = Market.NASDAQ,
            sector = Sector.INFORMATION_TECHNOLOGY,
            initialPrice = 100.0,
            volatility = 0.25,
            dividendYield = 0.01,
            marketCap = 100_000_000_000.0,
            sharesOutstanding = 1_000_000_000L,
            description = "저장 왕복 테스트 종목",
            beta = 1.1,
            quantityStep = 0.001,
        )
        val krStock = StockDefinition(
            symbol = "000001",
            name = "테스트 코리아",
            englishName = "Test Korea",
            market = Market.KOSPI,
            sector = Sector.INDUSTRIALS,
            initialPrice = 50_000.0,
            volatility = 0.18,
            dividendYield = 0.02,
            marketCap = 5_000_000_000_000.0,
            sharesOutstanding = 100_000_000L,
            description = "국내 테스트 종목",
        )
        val event = GameEvent(
            id = "event:1",
            title = "기술주 실적 발표",
            description = "시장 예상치를 웃도는 실적",
            scope = EventScope.MARKET,
            type = EventType.EARNINGS,
            severity = EventSeverity.MODERATE,
            impact = GameEventImpact(
                direction = ImpactDirection.POSITIVE,
                shockReturn = 0.03,
                hourlyDrift = 0.001,
                volatilityMultiplier = 1.2,
                volumeMultiplier = 1.5,
                liquidityMultiplier = 0.9,
                sentiment = 0.4,
            ),
            startsAt = now,
            durationHours = 12,
            affectedMarkets = setOf(Market.NASDAQ),
            sourceLabel = "저장 테스트 뉴스",
        )
        val quote = Quote(
            stockId = usStock.id,
            timestamp = now,
            price = 105.5,
            previousClose = 100.0,
            open = 101.0,
            high = 106.0,
            low = 99.5,
            volume = 123_456L,
            bidPrice = 105.4,
            askPrice = 105.6,
            bidQuantity = 50.0,
            askQuantity = 45.0,
            session = MarketSession.REGULAR,
        )
        val krQuote = Quote(
            stockId = krStock.id,
            timestamp = now,
            price = 51_000.0,
            previousClose = 50_000.0,
            volume = 55_000L,
            session = MarketSession.REGULAR,
        )
        val priceBar = PriceBar(
            stockId = usStock.id,
            startTime = now,
            endTime = nextHour,
            step = TurnStep.ONE_HOUR,
            open = 101.0,
            high = 106.0,
            low = 100.5,
            close = 105.5,
            volume = 123_456L,
        )
        val holding = Holding(
            stockId = usStock.id,
            quantity = 12.345,
            averagePrice = 90.0,
            currentPrice = 105.5,
            currency = Currency.USD,
            realizedProfit = 20.0,
        )
        val cash = mapOf(Currency.KRW to 75_000_000.0, Currency.USD to 5_000.25)
        val portfolio = PortfolioSnapshot(
            timestamp = now,
            cashByCurrency = cash,
            holdings = listOf(holding),
            exchangeRatesToKrw = mapOf(Currency.USD to 1_350.0),
            initialCapitalKrw = 100_000_000.0,
            realizedProfitKrw = 150_000.0,
            cumulativeCommissionKrw = 12_000.0,
            cumulativeTaxKrw = 30_000.0,
            holdingCostBasisKrw = mapOf(usStock.id to holding.costBasis * 1_350.0),
        )
        val annualLedger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(
                taxYear = 2026,
                gains = listOf(
                    RealizedStockGain(
                        id = "gain:1",
                        stockId = usStock.id,
                        realizedOn = LocalDate(2026, 8, 10),
                        gainKrw = 12_000_000L,
                        treatment = StockGainTaxTreatment.FOREIGN_STANDARD,
                        instrumentTaxClass = ForeignInstrumentTaxClass.US_COMMON_STOCK,
                    ),
                ),
                financialIncomeGrossKrw = 1_300_000L,
                foreignTaxPaidKrw = 195_000L,
                withholdingCreditsKrw = 154_000L,
            ),
        )
        val saleTax = DomesticSaleTaxCalculator().calculate(
            DomesticSaleTaxRequest(Market.KOSPI, 10_000_000L, LocalDate(2026, 8, 7)),
        )
        val brokerFees = BrokerFeeCalculator().calculate(
            BrokerFeeRequest(
                market = Market.NASDAQ,
                side = OrderSide.SELL,
                grossAmount = MoneyAmount(1_000_000L, Currency.USD),
                quantity = 100.0,
                tradedOn = LocalDate(2026, 8, 7),
            ),
        )
        val dividendTax = DividendTaxCalculator().calculate(
            DividendTaxRequest(
                taxClass = DividendTaxClass.US_ORDINARY_CORPORATION,
                grossAmount = MoneyAmount(10_000L, Currency.USD),
                paidOn = LocalDate(2026, 8, 7),
                taxExchangeRateToKrw = 1_350.0,
                w8BenValid = true,
            ),
        )
        val marketIndices = MarketIndexEngine().initialSnapshots(now)
        val listingLifecycleStates = listOf(usStock, krStock).associate { stock ->
            stock.id to ListingLifecycleEngine().initialState(stock)
        }
        val krxMarkets = listOf(Market.KOSPI, Market.KOSDAQ)
        val usDate = LocalDate(2026, 8, 6)
        val currentProtection = TradingProtectionSnapshot(
            krxCircuitBreakers = krxMarkets.associateWith { market ->
                TradingProtectionEngine.initialKrxCircuitBreaker(market, LocalDate(2026, 8, 7))
            },
            krxSidecars = krxMarkets.associateWith { market ->
                TradingProtectionEngine.initialKrxSidecar(market, LocalDate(2026, 8, 7))
            },
            krxVolatilityInterruptions = mapOf(
                krStock.id to TradingProtectionEngine.initialKrxVi(krStock.id, krStock.market),
            ),
            usMarketWideCircuitBreaker = TradingProtectionEngine.initialUsMwcb(usDate, now),
            usLuldStates = mapOf(
                usStock.id to TradingProtectionEngine.initialUsLuld(
                    stockId = usStock.id,
                    primaryMarket = usStock.market,
                    tradingDate = usDate,
                    tier = UsLuldTier.TIER_1,
                    previousClose = quote.previousClose,
                    referencePrice = quote.price,
                    referencePriceEffectiveAt = now,
                    easternTime = LocalTime(10, 0),
                ),
            ),
        )
        val fxRates = ReferenceCurrency.entries.associateWith { currency ->
            when (currency) {
                ReferenceCurrency.KRW -> 1.0
                ReferenceCurrency.USD -> 1_350.0
                else -> 1_000.0
            }
        }

        return SimulatorUiState(
            options = NewGameOptions(
                initialCapitalKrw = 100_000_000.0,
                seed = 20260807L,
                usFractionalTrading = true,
                autoExchange = true,
                initialUsdKrw = 1_350.0,
            ),
            phase = GamePhase.PLAYING,
            screen = Screen.MARKET,
            currentTime = now,
            turn = 42L,
            selectedTurnStep = TurnStep.FOUR_HOURS,
            stocks = listOf(usStock, krStock),
            selectedStockId = usStock.id,
            quotes = mapOf(usStock.id to quote, krStock.id to krQuote),
            priceHistory = mapOf(usStock.id to listOf(priceBar), krStock.id to emptyList()),
            cashByCurrency = cash,
            holdings = mapOf(usStock.id to holding),
            orders = listOf(
                Order(
                    id = "order:1",
                    stockId = usStock.id,
                    side = OrderSide.BUY,
                    type = OrderType.LIMIT,
                    quantity = 1.25,
                    createdAt = now,
                    limitPrice = 99.0,
                    status = OrderStatus.ACCEPTED,
                    updatedAt = now,
                    timeInForce = TimeInForce.GOOD_TILL_CANCELLED,
                ),
            ),
            trades = listOf(
                Trade(
                    id = "trade:1",
                    orderId = "order:filled",
                    stockId = usStock.id,
                    side = OrderSide.BUY,
                    quantity = 2.5,
                    price = 100.0,
                    currency = Currency.USD,
                    executedAt = now,
                    commission = 0.25,
                    accountingSequence = 1L,
                ),
            ),
            selectedOrderBook = OrderBookSnapshot(
                stockId = usStock.id,
                timestamp = now,
                session = MarketSession.CLOSED,
                lastPrice = 105.5,
                bids = emptyList(),
                asks = emptyList(),
            ),
            marketSessions = mapOf(
                Market.KOSPI to MarketSession.REGULAR,
                Market.KOSDAQ to MarketSession.REGULAR,
                Market.NASDAQ to MarketSession.CLOSED,
                Market.NYSE to MarketSession.CLOSED,
            ),
            macro = MacroEnvironment(
                policyRate = 0.03,
                inflationRate = 0.025,
                growthRate = 0.02,
                usdKrw = 1_350.0,
                previousUsdKrw = 1_345.0,
                riskSentiment = 0.2,
                volatilityRegime = 1.1,
                marketHourlyReturns = mapOf(Market.NASDAQ to 0.004, Market.KOSPI to 0.001),
                sectorHourlyReturns = mapOf(Sector.INFORMATION_TECHNOLOGY to 0.006),
                fxRatesToKrw = fxRates,
                previousFxRatesToKrw = fxRates,
                regionalEtfHourlyReturns = EtfExposureRegion.entries.associateWith { 0.0 },
                marketChangeFromPreviousClose = mapOf(Market.NASDAQ to 0.02),
            ),
            activeEvents = listOf(event),
            newsEvents = listOf(event),
            readEventIds = setOf(event.id),
            portfolioSnapshots = listOf(portfolio),
            dailyStatistics = listOf(
                DailyPortfolioStat(
                    date = LocalDate(2026, 8, 7),
                    totalAssetsKrw = 101_000_000.0,
                    cashValueKrw = 81_750_337.5,
                    stockValueKrw = 19_249_662.5,
                    dailyReturn = 0.01,
                    drawdown = -0.002,
                    benchmarkValue = 101.5,
                    usdKrw = 1_350.0,
                ),
            ),
            benchmarkHistory = listOf(BenchmarkPoint(now, 101.5, 0.015)),
            transactionCosts = listOf(
                TransactionCostRecord(
                    tradeId = "trade:1",
                    stockId = usStock.id,
                    market = Market.NASDAQ,
                    paidAt = now,
                    currency = Currency.USD,
                    commission = 0.25,
                    saleTax = 0.0,
                    exchangeRateToKrw = 1_350.0,
                    feeBreakdown = brokerFees,
                    taxBreakdown = saleTax,
                ),
            ),
            realizedGains = listOf(
                RealizedGainRecord(
                    tradeId = "trade:sell",
                    stockId = usStock.id,
                    market = Market.NASDAQ,
                    soldAt = now,
                    settlementDate = LocalDate(2026, 8, 10),
                    quantity = 2.0,
                    proceeds = 220.0,
                    costBasis = 180.0,
                    commission = 0.22,
                    saleTax = 0.0,
                    currency = Currency.USD,
                    exchangeRateToKrw = 1_350.0,
                    taxTreatment = StockGainTaxTreatment.FOREIGN_STANDARD,
                    assessmentNotes = listOf("미국 일반주식 과세대상"),
                ),
            ),
            dividendLedger = listOf(
                DividendLedgerEntry(
                    id = "dividend:1",
                    stockId = usStock.id,
                    paidAt = now,
                    currency = Currency.USD,
                    grossAmount = 100.0,
                    withholdingTax = 15.0,
                    netAmount = 85.0,
                    exchangeRateToKrw = 1_350.0,
                    taxBreakdown = dividendTax.breakdown,
                    taxableIncomeAmount = 100.0,
                    returnOfCapitalAmount = 0.0,
                    excessReturnOfCapitalGainKrw = 0L,
                    accountingSequence = 2L,
                ),
            ),
            foreignExchangeLedger = listOf(
                ForeignExchangeRecord(
                    id = "fx:1",
                    executedAt = now,
                    fromCurrency = Currency.KRW,
                    toCurrency = Currency.USD,
                    sourceAmount = 1_350_000.0,
                    receivedAmount = 999.0,
                    usdKrwRate = 1_350.0,
                    spreadCostKrw = 1_350.0,
                    automatic = true,
                ),
            ),
            annualTaxLedgers = mapOf(2026 to annualLedger),
            taxPaymentNotices = listOf(
                TaxPaymentNotice(
                    id = "tax-notice:2026",
                    taxYear = 2026,
                    dueDate = LocalDate(2027, 5, 31),
                    amountKrw = annualLedger.totalPayableKrw,
                    status = TaxLiabilityStatus.DUE,
                    message = "해외주식 양도소득세 신고 예정",
                ),
            ),
            peakAssetsKrw = 102_000_000.0,
            maximumDrawdown = -0.015,
            rngState = 123_456_789L,
            eventEngineSnapshot = EventEngineSnapshot(
                randomState = 987_654_321L,
                sequence = 7L,
                lastTriggeredEpochSeconds = mapOf("earnings" to now.epochSeconds),
                activeEvents = listOf(event),
            ),
            nextSequence = 88L,
            isAdvancing = false,
            lastMessage = "수동 저장 테스트",
            pendingEtfReferenceReturns = emptyMap(),
            pendingClosedEventLogReturns = emptyMap(),
            marketIndices = marketIndices,
            marketIndexHistory = marketIndices.mapValues { (_, snapshot) -> listOf(snapshot) },
            taxExchangeRatesByTradeId = mapOf("trade:1" to 1_350.0),
            pendingTaxSettlementTradeIds = setOf("trade:1"),
            watchlistedStockIds = setOf(usStock.id),
            pendingCorporateActions = emptyList(),
            corporateActionLedger = emptyList(),
            listingLifecycleStates = listingLifecycleStates,
            listingLifecycleLedger = emptyList(),
            tradingProtectionSnapshot = currentProtection,
            dailyTradingSurveillance = mapOf(usStock.id to emptyList(), krStock.id to emptyList()),
        )
    }

    private fun richStateWithLifecycleAndProtection(): SimulatorUiState {
        val base = richState()
        val usStock = base.stocks.single { it.market == Market.NASDAQ }
        val krStock = base.stocks.single { it.market == Market.KOSPI }
        val usDate = LocalDate(2026, 8, 6)
        val krDate = base.currentDate
        val listingEngine = ListingLifecycleEngine()
        val usListing = listingEngine.initialState(usStock).copy(
            status = ListingLifecycleStatus.DEFICIENCY_NOTICE,
            activeReason = ListingLifecycleReason.US_LISTING_MAINTENANCE,
            designatedOn = usDate,
            cureDeadline = usDate,
            designationCount = 1,
            lastEvaluatedTradingDate = usDate,
            ledgerSequence = 1L,
        )
        val krListing = listingEngine.initialState(krStock)
        val lifecycleEvent = ListingLifecycleLedgerEvent(
            id = "listing-save:${usStock.id}:1",
            sequence = 1L,
            stockId = usStock.id,
            tradingDate = usDate,
            kind = ListingLifecycleEventKind.DEFICIENCY_DESIGNATED,
            fromStatus = ListingLifecycleStatus.LISTED,
            toStatus = ListingLifecycleStatus.DEFICIENCY_NOTICE,
            reason = ListingLifecycleReason.US_LISTING_MAINTENANCE,
            level = ListingNoticeLevel.CAUTION,
            title = "상장 유지 요건 안내",
            summary = "저장 스키마 회귀 테스트",
            deadline = usDate,
        )
        val halt = InstrumentTradingHalt(
            stockId = usStock.id,
            reason = TradingHaltReason.MATERIAL_DISCLOSURE,
            detail = "중요 공시 확인",
            startedAt = base.currentTime,
            policy = TradingHaltOrderPolicy(
                acceptsNewOrders = true,
                allowsCancellation = true,
                allowsExecution = false,
            ),
        )
        val alert = InvestmentAlertDesignation(
            stockId = krStock.id,
            level = InvestmentAlertLevel.CAUTION,
            reasonCodes = setOf("PRICE_SURGE"),
            summary = "단기 가격 급등",
            designatedAt = base.currentTime,
            designatedOn = krDate,
            releaseReviewWindow = TradingDayWindow(krDate, krDate),
            releaseRule = InvestmentAlertReleaseRule.CAUTION_PRICE_VOLUME,
        )
        val protection = base.tradingProtectionSnapshot.copy(
            krxCircuitBreakers = base.tradingProtectionSnapshot.krxCircuitBreakers +
                (Market.KOSPI to KrxCircuitBreakerState(Market.KOSPI, krDate)),
            krxSidecars = base.tradingProtectionSnapshot.krxSidecars +
                (Market.KOSPI to KrxSidecarState(Market.KOSPI, krDate)),
            krxVolatilityInterruptions = mapOf(
                krStock.id to KrxViState(krStock.id, krStock.market),
            ),
            instrumentTradingHalts = mapOf(usStock.id to halt),
            investmentAlerts = mapOf(krStock.id to alert),
            usMarketWideCircuitBreaker = TradingProtectionEngine.initialUsMwcb(usDate, base.currentTime),
            usLuldStates = mapOf(
                usStock.id to TradingProtectionEngine.initialUsLuld(
                    stockId = usStock.id,
                    primaryMarket = usStock.market,
                    tradingDate = usDate,
                    tier = UsLuldTier.TIER_1,
                    previousClose = 100.0,
                    referencePrice = 105.5,
                    referencePriceEffectiveAt = base.currentTime,
                    easternTime = LocalTime(10, 0),
                ),
            ),
        )
        return base.copy(
            listingLifecycleStates = mapOf(
                usStock.id to usListing,
                krStock.id to krListing,
            ),
            listingLifecycleLedger = listOf(lifecycleEvent),
            tradingProtectionSnapshot = protection,
            dailyTradingSurveillance = mapOf(
                usStock.id to listOf(
                    DailyTradingSurveillancePoint(
                        date = usDate,
                        close = 105.5,
                        volume = 123_456L,
                        turnoverRate = 0.000123456,
                    ),
                ),
                krStock.id to emptyList(),
            ),
        )
    }
}
