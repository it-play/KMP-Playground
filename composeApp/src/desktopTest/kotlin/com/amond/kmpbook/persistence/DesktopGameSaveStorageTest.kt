package com.amond.kmpbook.persistence

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderStatus
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
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
import com.amond.kmpbook.presentation.DividendLedgerEntry
import com.amond.kmpbook.presentation.ForeignExchangeRecord
import com.amond.kmpbook.presentation.NewGameOptions
import com.amond.kmpbook.presentation.RealizedGainRecord
import com.amond.kmpbook.presentation.SimulatorUiState
import com.amond.kmpbook.presentation.TaxPaymentNotice
import com.amond.kmpbook.presentation.TransactionCostRecord
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
        val original = richState()

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

        val leftovers = Files.list(file.parent).use { paths ->
            paths.filter { it.fileName.toString().startsWith(".savegame-") }.count()
        }
        assertEquals(0L, leftovers)
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
    fun unsupportedSchemaIsDistinguishedFromCorruptJson(): Unit = runBlocking {
        val file = temporaryDirectory.resolve("savegame.json")
        val storage = GameSaveStorage(file.toString())
        assertIs<GameSaveResult.Success>(storage.save(richState()))
        val futureJson = file.readText(StandardCharsets.UTF_8).replace(
            "\"schemaVersion\": $CURRENT_GAME_SAVE_SCHEMA_VERSION",
            "\"schemaVersion\": 99",
        )
        file.writeText(futureJson, StandardCharsets.UTF_8)

        val failure = assertIs<GameLoadResult.Failure>(storage.load())
        assertEquals(GameSaveErrorCode.UNSUPPORTED_SCHEMA, failure.error.code)
        assertTrue(failure.error.message.contains("99"))
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
        )
    }
}
