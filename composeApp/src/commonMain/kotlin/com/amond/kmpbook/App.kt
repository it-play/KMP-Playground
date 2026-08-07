package com.amond.kmpbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.tax.FeeCategory
import com.amond.kmpbook.domain.tax.TaxCategory
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.persistence.GameLoadResult
import com.amond.kmpbook.persistence.GameSaveDeleteResult
import com.amond.kmpbook.persistence.GameSavePresenceResult
import com.amond.kmpbook.persistence.GameSaveResult
import com.amond.kmpbook.persistence.GameSaveStorage
import com.amond.kmpbook.presentation.NewGameOptions
import com.amond.kmpbook.presentation.SimulatorUiState
import com.amond.kmpbook.presentation.SimulatorViewModel
import com.amond.kmpbook.ui.screens.AnalyticsScreen
import com.amond.kmpbook.ui.screens.EndingScreen
import com.amond.kmpbook.ui.screens.EventsScreen
import com.amond.kmpbook.ui.screens.EventNewsFilterState
import com.amond.kmpbook.ui.screens.GameSettingsDisplay
import com.amond.kmpbook.ui.screens.HomeDashboardScreen
import com.amond.kmpbook.ui.screens.MarketTradingScreen
import com.amond.kmpbook.ui.screens.NewGameScreen
import com.amond.kmpbook.ui.screens.OrdersScreen
import com.amond.kmpbook.ui.screens.PortfolioScreen
import com.amond.kmpbook.ui.screens.SettingsScreen
import com.amond.kmpbook.ui.screens.TaxCenterData
import com.amond.kmpbook.ui.screens.TaxCenterScreen
import com.amond.kmpbook.ui.screens.TaxYearDisplay
import com.amond.kmpbook.ui.shell.SidebarSummary
import com.amond.kmpbook.ui.shell.SimulationClockRail
import com.amond.kmpbook.ui.shell.SimulatorSidebar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketSimulatorTheme
import com.amond.kmpbook.ui.theme.MarketType
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch

@Composable
fun App() {
    val viewModel = remember { SimulatorViewModel() }
    val storage = remember { GameSaveStorage() }
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var hasSavedGame by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("아직 확인된 저장 장부가 없습니다.") }

    LaunchedEffect(storage) {
        when (val result = storage.exists()) {
            is GameSavePresenceResult.Present -> {
                hasSavedGame = true
                saveStatus = "저장 장부 ${result.sizeBytes / 1024} KB가 있습니다."
            }
            is GameSavePresenceResult.Missing -> {
                hasSavedGame = false
                saveStatus = "저장 장부가 없습니다."
            }
            is GameSavePresenceResult.Failure -> saveStatus = result.error.message
        }
    }

    val saveGame: () -> Unit = {
        scope.launch {
            when (val result = storage.save(viewModel.currentState)) {
                is GameSaveResult.Success -> {
                    hasSavedGame = true
                    saveStatus = "${result.metadata.gameTime} 시점 장부를 저장했습니다."
                }
                is GameSaveResult.Failure -> saveStatus = result.error.message
            }
        }
    }
    val loadGame: () -> Unit = {
        scope.launch {
            when (val result = storage.load()) {
                is GameLoadResult.Success -> {
                    if (viewModel.restoreGame(result.state)) {
                        hasSavedGame = true
                        saveStatus = "${result.metadata.gameTime} 시점 장부를 불러왔습니다."
                    } else {
                        saveStatus = "저장 장부의 게임 상태 검증에 실패했습니다."
                    }
                }
                is GameLoadResult.NotFound -> {
                    hasSavedGame = false
                    saveStatus = result.message
                }
                is GameLoadResult.Failure -> saveStatus = result.error.message
            }
        }
    }
    val deleteSave: () -> Unit = {
        scope.launch {
            when (val result = storage.delete()) {
                is GameSaveDeleteResult.Deleted -> {
                    hasSavedGame = false
                    saveStatus = "저장 장부를 삭제했습니다."
                }
                is GameSaveDeleteResult.NotFound -> {
                    hasSavedGame = false
                    saveStatus = "삭제할 저장 장부가 없습니다."
                }
                is GameSaveDeleteResult.Failure -> saveStatus = result.error.message
            }
        }
    }

    MarketSimulatorTheme {
        when (state.phase) {
            GamePhase.SETUP -> NewGameScreen(
                onStart = { capital, seed, fractional, autoExchange ->
                    viewModel.newGame(
                        NewGameOptions(
                            initialCapitalKrw = capital,
                            seed = seed,
                            usFractionalTrading = fractional,
                            autoExchange = autoExchange,
                        ),
                    )
                },
                hasSavedGame = hasSavedGame,
                onLoadSavedGame = loadGame,
            )

            GamePhase.SETTLEMENT,
            GamePhase.FINISHED,
            -> EndingScreen(
                snapshot = state.currentPortfolio,
                history = state.portfolioSnapshots,
                tradeCount = state.trades.size,
                eventCount = state.newsEvents.size,
                totalTaxKrw = state.totalSaleTaxKrw +
                    state.dividendLedger.sumOf { it.withholdingTaxKrw } +
                    state.annualTaxLedgers.values.sumOf { it.totalPayableKrw }.toDouble(),
                maxDrawdown = state.maximumDrawdown,
                onNewGame = viewModel::resetGame,
            )

            else -> RunningGame(
                state = state,
                viewModel = viewModel,
                hasSavedGame = hasSavedGame,
                savePath = storage.savePath,
                saveStatus = saveStatus,
                onSaveGame = saveGame,
                onLoadGame = loadGame,
                onDeleteSave = deleteSave,
            )
        }
    }
}

@Composable
private fun RunningGame(
    state: SimulatorUiState,
    viewModel: SimulatorViewModel,
    hasSavedGame: Boolean,
    savePath: String,
    saveStatus: String,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onDeleteSave: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MarketColors.Ledger)) {
        Row(Modifier.fillMaxSize()) {
            SimulatorSidebar(
                selected = state.screen,
                summary = SidebarSummary(
                    totalAssetsKrw = state.totalAssetsKrw,
                    returnRate = state.totalReturnRate,
                    unreadEvents = state.unreadEvents,
                ),
                onSelect = { screen ->
                    if (screen == Screen.EVENTS) viewModel.markAllEventsRead()
                    viewModel.selectScreen(screen)
                },
            )
            Column(Modifier.weight(1f).fillMaxSize()) {
                SimulationClockRail(
                    currentTime = state.currentTime,
                    turn = state.turn,
                    progress = state.progress.toFloat(),
                    selectedStep = state.selectedTurnStep,
                    koreanSession = state.marketSessions[Market.KOSPI] ?: MarketSession.CLOSED,
                    usSession = state.marketSessions[Market.NASDAQ] ?: MarketSession.CLOSED,
                    canAdvance = !state.isAdvancing && !state.isAtEnd,
                    onStepSelected = viewModel::selectTurnStep,
                    onAdvance = { viewModel.advance() },
                )
                Box(Modifier.fillMaxSize()) {
                    ScreenContent(
                        state = state,
                        viewModel = viewModel,
                        hasSavedGame = hasSavedGame,
                        savePath = savePath,
                        saveStatus = saveStatus,
                        onSaveGame = onSaveGame,
                        onLoadGame = onLoadGame,
                        onDeleteSave = onDeleteSave,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.lastMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        ) {
            Box(
                Modifier
                    .background(MarketColors.NavyRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = viewModel::clearMessage)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    state.lastMessage.orEmpty(),
                    style = MarketType.body,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    state: SimulatorUiState,
    viewModel: SimulatorViewModel,
    hasSavedGame: Boolean,
    savePath: String,
    saveStatus: String,
    onSaveGame: () -> Unit,
    onLoadGame: () -> Unit,
    onDeleteSave: () -> Unit,
) {
    var eventNewsFilterState by remember { mutableStateOf(EventNewsFilterState()) }
    val portfolioHistory = state.portfolioSnapshots.ifEmpty { listOf(state.currentPortfolio) }
    val estimatedTax = state.annualTaxSummary?.totalPayableKrw?.toDouble() ?: 0.0
    val openStock: (String) -> Unit = { stockId ->
        viewModel.selectStock(stockId)
        viewModel.selectScreen(Screen.MARKET)
    }

    when (state.screen) {
        Screen.HOME -> HomeDashboardScreen(
            snapshot = state.currentPortfolio,
            history = portfolioHistory,
            stocks = state.stocks,
            marketIndices = state.marketIndices.orEmpty(),
            usCircuitBreakerState = state.usCircuitBreakerState ?: com.amond.kmpbook.presentation.UsCircuitBreakerState(),
            events = state.newsEvents,
            estimatedTaxKrw = estimatedTax,
            usdKrw = state.macro.usdKrw,
            maxDrawdown = state.maximumDrawdown,
            onOpenStock = openStock,
            onOpenEvents = {
                viewModel.markAllEventsRead()
                viewModel.selectScreen(Screen.EVENTS)
            },
            onOpenTax = { viewModel.selectScreen(Screen.TAX_REPORT) },
        )

        Screen.MARKET,
        Screen.STOCK_DETAIL,
        -> MarketTradingScreen(
            stocks = state.stocks,
            quotes = state.quotes,
            priceHistory = state.priceHistory,
            selectedStockId = state.selectedStockId,
            holding = state.selectedHolding,
            orderBook = state.selectedOrderBook?.toOrderBook(),
            cashKrw = state.cashByCurrency[Currency.KRW] ?: 0.0,
            cashUsd = state.cashByCurrency[Currency.USD] ?: 0.0,
            onSelectStock = viewModel::selectStock,
            watchlistedStockIds = state.watchlist,
            onToggleWatchlist = viewModel::toggleWatchlist,
            onSubmitOrder = { side, type, timeInForce, quantity, limitPrice ->
                state.selectedStockId?.let { stockId ->
                    viewModel.placeOrder(stockId, side, type, quantity, limitPrice, timeInForce)
                }
            },
        )

        Screen.ORDER -> OrdersScreen(
            orders = state.orders,
            trades = state.trades,
            stocks = state.stocks,
            onCancelOrder = { viewModel.cancelOrder(it) },
            onOpenStock = openStock,
        )

        Screen.PORTFOLIO -> PortfolioScreen(
            snapshot = state.currentPortfolio,
            history = portfolioHistory,
            stocks = state.stocks,
            onOpenStock = openStock,
        )

        Screen.EVENTS -> EventsScreen(
            currentTime = state.currentTime,
            events = state.newsEvents,
            upcomingEvents = state.upcomingScheduledEvents,
            stocks = state.stocks,
            holdingIds = state.holdings.keys,
            watchlistIds = state.watchlist,
            onOpenStock = openStock,
            filterState = eventNewsFilterState,
            onFilterStateChange = { eventNewsFilterState = it },
        )

        Screen.ANALYTICS -> AnalyticsScreen(
            snapshot = state.currentPortfolio,
            history = portfolioHistory,
            trades = state.trades,
            stocks = state.stocks,
            maxDrawdown = state.maximumDrawdown,
        )

        Screen.TAX_REPORT -> TaxCenterScreen(state.toTaxCenterData())

        Screen.SETTINGS -> SettingsScreen(
            settings = GameSettingsDisplay(
                initialCapitalKrw = state.initialCapitalKrw,
                seed = state.seed,
                fractionalUsTrading = state.usFractionalTrading,
                autoExchange = state.autoExchange,
                usdKrw = state.macro.usdKrw,
                cashKrw = state.cashByCurrency[Currency.KRW] ?: 0.0,
                cashUsd = state.cashByCurrency[Currency.USD] ?: 0.0,
            ),
            onAutoExchangeChanged = viewModel::setAutoExchange,
            onExchangeKrwToUsd = { viewModel.exchange(Currency.KRW, Currency.USD, it) },
            onExchangeUsdToKrw = { viewModel.exchange(Currency.USD, Currency.KRW, it) },
            hasSavedGame = hasSavedGame,
            savePath = savePath,
            saveStatus = saveStatus,
            onSaveGame = onSaveGame,
            onLoadGame = onLoadGame,
            onDeleteSave = onDeleteSave,
            onResetGame = viewModel::resetGame,
        )

        Screen.ENDING -> EndingScreen(
            snapshot = state.currentPortfolio,
            history = portfolioHistory,
            tradeCount = state.trades.size,
            eventCount = state.newsEvents.size,
            totalTaxKrw = state.totalSaleTaxKrw + estimatedTax,
            maxDrawdown = state.maximumDrawdown,
            onNewGame = viewModel::resetGame,
        )
    }
}

private fun SimulatorUiState.toTaxCenterData(): TaxCenterData {
    val timeZone = com.amond.kmpbook.domain.time.GameCalendar.KOREA_TIME_ZONE
    val yearsWithActivity = buildSet {
        add(currentDate.year)
        addAll(annualTaxLedgers.keys)
        transactionCosts.forEach { add(it.paidAt.toLocalDateTime(timeZone).year) }
        dividendLedger.forEach { add(it.paidAt.toLocalDateTime(timeZone).year) }
    }
    val yearRows = yearsWithActivity.sorted().map { year ->
        val annual = annualTaxLedgers[year]
        val yearCosts = transactionCosts.filter { it.paidAt.toLocalDateTime(timeZone).year == year }
        val yearDividends = dividendLedger.filter { it.paidAt.toLocalDateTime(timeZone).year == year }
        val transactionTax = yearCosts.sumOf { cost ->
            cost.taxBreakdown?.items
                ?.filter { it.category == TaxCategory.SECURITIES_TRANSACTION }
                ?.sumOf { it.amount.amount }
                ?: cost.saleTax
        }
        val ruralTax = yearCosts.sumOf { cost ->
            cost.taxBreakdown?.items
                ?.filter { it.category == TaxCategory.SPECIAL_RURAL }
                ?.sumOf { it.amount.amount }
                ?: 0.0
        }
        val etfHoldingPeriodWithholding = yearCosts.sumOf { cost ->
            cost.taxBreakdown?.items
                ?.filter { it.category == TaxCategory.DIVIDEND_WITHHOLDING }
                ?.sumOf { it.amount.amount }
                ?: 0.0
        }
        TaxYearDisplay(
            year = year,
            taxableStockGainKrw = annual?.currentYearNetStockGainKrw?.coerceAtLeast(0L)?.toDouble() ?: 0.0,
            stockLossKrw = annual?.expiredStockLossKrw?.toDouble() ?: 0.0,
            basicDeductionKrw = annual?.sharedStockBasicDeductionKrw?.toDouble() ?: 2_500_000.0,
            capitalGainsTaxKrw = annual?.totalPayableKrw?.toDouble() ?: 0.0,
            securitiesTransactionTaxKrw = transactionTax,
            ruralSpecialTaxKrw = ruralTax,
            financialIncomeGrossKrw = annual?.financialIncomeGrossKrw?.toDouble()
                ?: yearDividends.sumOf { it.financialIncomeAmountKrw },
            financialIncomeWithheldKrw = yearDividends.sumOf { it.withholdingTaxKrw } +
                etfHoldingPeriodWithholding,
            paidKrw = taxPaymentNotices
                .filter { it.taxYear == year && it.status == TaxLiabilityStatus.PAID }
                .sumOf { it.amountKrw }
                .toDouble(),
        )
    }
    val secFinra = transactionCosts.sumOf { cost ->
        cost.feeBreakdown?.items
            ?.filter { it.category == FeeCategory.SEC_SECTION_31 || it.category == FeeCategory.FINRA_TAF }
            ?.sumOf { item -> item.amount.amount * cost.exchangeRateToKrw }
            ?: 0.0
    }
    val fxCosts = foreignExchangeLedger.sumOf { it.spreadCostKrw }
    return TaxCenterData(
        currentYear = currentDate.year,
        years = yearRows,
        brokerFeesKrw = totalCommissionKrw + fxCosts,
        secFinraFeesKrw = secFinra,
        financialIncomeGrossKrw = annualTaxSummary?.financialIncomeGrossKrw?.toDouble()
            ?: dividendLedger
                .filter { it.paidAt.toLocalDateTime(timeZone).year == currentDate.year }
                .sumOf { it.grossAmountKrw },
        highDividendEligibleKrw = annualTaxSummary?.highDividendIncomeKrw?.toDouble() ?: 0.0,
        nextDueDate = "${currentDate.year + 1}.05.31",
    )
}
