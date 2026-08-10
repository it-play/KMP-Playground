package com.amond.kmpbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.amond.kmpbook.domain.model.game.GamePhase
import com.amond.kmpbook.domain.model.game.Screen
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionAction
import com.amond.kmpbook.domain.model.protection.core.TradingProtectionRequest
import com.amond.kmpbook.domain.model.protection.core.TradingRestrictionSource
import com.amond.kmpbook.domain.model.trading.OrderType
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.protection.TradingProtectionEngine
import com.amond.kmpbook.domain.tax.core.TaxCategory
import com.amond.kmpbook.domain.tax.fee.FeeCategory
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.persistence.model.GameSaveEntry
import com.amond.kmpbook.persistence.result.GameLoadFailure
import com.amond.kmpbook.persistence.result.GameLoadNotFound
import com.amond.kmpbook.persistence.result.GameLoadSuccess
import com.amond.kmpbook.persistence.result.GameSaveDeleteFailure
import com.amond.kmpbook.persistence.result.GameSaveDeleteNotFound
import com.amond.kmpbook.persistence.result.GameSaveDeleted
import com.amond.kmpbook.persistence.result.GameSaveFailure
import com.amond.kmpbook.persistence.result.GameSaveSuccess
import com.amond.kmpbook.persistence.storage.GameSaveStorage
import com.amond.kmpbook.presentation.metrics.InstrumentMetricsProjection
import com.amond.kmpbook.presentation.news.buildNewsUiProjection
import com.amond.kmpbook.presentation.protection.ProtectionUiProjection
import com.amond.kmpbook.presentation.protection.buildProtectionUiProjection
import com.amond.kmpbook.presentation.simulator.SimulatorUiState
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import com.amond.kmpbook.ui.components.MarketProtectionDetailSurface
import com.amond.kmpbook.ui.components.MarketProtectionStrip
import com.amond.kmpbook.ui.screens.dashboard.HomeDashboardScreen
import com.amond.kmpbook.ui.screens.game.EndingScreen
import com.amond.kmpbook.ui.screens.game.GameEntryDestination
import com.amond.kmpbook.ui.screens.game.GameLobbyScreen
import com.amond.kmpbook.ui.screens.game.GameSettingsDisplay
import com.amond.kmpbook.ui.screens.game.LobbySettingsScreen
import com.amond.kmpbook.ui.screens.game.SettingsScreen
import com.amond.kmpbook.ui.screens.market.MarketTradingScreen
import com.amond.kmpbook.ui.screens.news.EventNewsFilterState
import com.amond.kmpbook.ui.screens.news.EventsScreen
import com.amond.kmpbook.ui.screens.news.NewsBrowseTab
import com.amond.kmpbook.ui.screens.orders.OrdersScreen
import com.amond.kmpbook.ui.screens.portfolio.AnalyticsScreen
import com.amond.kmpbook.ui.screens.portfolio.PortfolioScreen
import com.amond.kmpbook.ui.screens.tax.TaxCenterData
import com.amond.kmpbook.ui.screens.tax.TaxCenterScreen
import com.amond.kmpbook.ui.screens.tax.TaxYearDisplay
import com.amond.kmpbook.ui.shell.SidebarSummary
import com.amond.kmpbook.ui.shell.SimulationClockRail
import com.amond.kmpbook.ui.shell.SimulatorSidebar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketSimulatorTheme
import com.amond.kmpbook.ui.theme.MarketType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

private const val GAME_MESSAGE_DISPLAY_MILLIS = 3_000L

@Composable
fun App(
    onExitRequest: () -> Unit = {},
    escapeRequest: Int = 0,
) {
    val viewModel = remember { SimulatorViewModel() }
    val storage = remember { GameSaveStorage() }
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var entryDestination by remember { mutableStateOf(GameEntryDestination.LOBBY) }
    var saves by remember { mutableStateOf(emptyList<GameSaveEntry>()) }
    var saveStatus by remember { mutableStateOf("아직 확인된 저장 장부가 없습니다.") }

    LaunchedEffect(storage) {
        val catalog = storage.list()
        saves = catalog.entries
        saveStatus = catalog.error?.message ?: if (saves.isEmpty()) {
            "저장 장부가 없습니다."
        } else {
            "최근 저장 순으로 ${saves.size}개 장부를 찾았습니다."
        }
    }
    LaunchedEffect(escapeRequest) {
        if (
            escapeRequest > 0 &&
            !state.isAdvancing &&
            state.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED)
        ) {
            viewModel.selectScreen(Screen.SETTINGS)
        }
    }

    val refreshSaves: suspend () -> Unit = {
        val catalog = storage.list()
        saves = catalog.entries
        catalog.error?.let { saveStatus = it.message }
    }
    val saveGame: (String) -> Unit = { name ->
        scope.launch {
            when (val result = storage.save(viewModel.currentState, name)) {
                is GameSaveSuccess -> {
                    saveStatus = "${result.path.substringAfterLast('/').substringAfterLast('\\')} 파일로 저장했습니다."
                    refreshSaves()
                }
                is GameSaveFailure -> saveStatus = result.error.message
            }
        }
    }
    val loadGame: (GameSaveEntry) -> Unit = { save ->
        scope.launch {
            when (val result = storage.load(save.fileName)) {
                is GameLoadSuccess -> {
                    if (viewModel.restoreGame(result.state)) {
                        saveStatus = "${save.name} 장부를 불러왔습니다."
                    } else {
                        saveStatus = "저장 장부의 게임 상태 검증에 실패했습니다."
                    }
                }
                is GameLoadNotFound -> saveStatus = result.message
                is GameLoadFailure -> saveStatus = result.error.message
            }
        }
    }
    val deleteSave: (GameSaveEntry) -> Unit = { save ->
        scope.launch {
            when (val result = storage.delete(save.fileName)) {
                is GameSaveDeleted -> {
                    saveStatus = "${save.name} 장부를 삭제했습니다."
                    refreshSaves()
                }
                is GameSaveDeleteNotFound -> saveStatus = "삭제할 저장 장부가 없습니다."
                is GameSaveDeleteFailure -> saveStatus = result.error.message
            }
        }
    }

    MarketSimulatorTheme {
        Box(Modifier.fillMaxSize()) {
            when (state.phase) {
                GamePhase.SETUP -> when (entryDestination) {
                    GameEntryDestination.LOBBY -> GameLobbyScreen(
                        saves = saves,
                        onContinue = loadGame,
                        onLoad = loadGame,
                        onStartNewGame = viewModel::newGame,
                        onSettings = { entryDestination = GameEntryDestination.SETTINGS },
                        onExit = onExitRequest,
                    )
                    GameEntryDestination.SETTINGS -> LobbySettingsScreen(
                        saveDirectory = storage.saveDirectory,
                        saveCount = saves.size,
                        onBack = { entryDestination = GameEntryDestination.LOBBY },
                    )
                }

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
                    onNewGame = {
                        viewModel.resetGame()
                        entryDestination = GameEntryDestination.LOBBY
                    },
                )

                else -> RunningGame(
                    state = state,
                    viewModel = viewModel,
                    saves = saves,
                    saveDirectory = storage.saveDirectory,
                    saveStatus = saveStatus,
                    onSaveGame = saveGame,
                    onLoadGame = loadGame,
                    onDeleteSave = deleteSave,
                    onReturnToLobby = {
                        viewModel.resetGame()
                        entryDestination = GameEntryDestination.LOBBY
                    },
                )
            }
        }
    }
}

@Composable
private fun RunningGame(
    state: SimulatorUiState,
    viewModel: SimulatorViewModel,
    saves: List<GameSaveEntry>,
    saveDirectory: String,
    saveStatus: String,
    onSaveGame: (String) -> Unit,
    onLoadGame: (GameSaveEntry) -> Unit,
    onDeleteSave: (GameSaveEntry) -> Unit,
    onReturnToLobby: () -> Unit,
) {
    val selectedMarket = state.selectedStock?.market
    val protectionProjection = remember(
        state.tradingProtectionSnapshot,
        state.listingLifecycleStates,
        state.selectedStockId,
        selectedMarket,
        state.currentTime,
    ) {
        buildProtectionUiProjection(
            snapshot = state.tradingProtectionSnapshot,
            listingStates = state.listingLifecycleStates,
            selectedStockId = state.selectedStockId,
            selectedMarket = selectedMarket,
            at = state.currentTime,
        )
    }
    var showMarketProtectionDetail by remember { mutableStateOf(false) }
    LaunchedEffect(protectionProjection.marketStrip) {
        if (protectionProjection.marketStrip == null) showMarketProtectionDetail = false
    }
    LaunchedEffect(state.lastMessage, state.turn) {
        if (state.lastMessage != null) {
            delay(GAME_MESSAGE_DISPLAY_MILLIS)
            viewModel.clearMessage()
        }
    }
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
                MarketProtectionStrip(
                    model = protectionProjection.marketStrip,
                    onClick = { showMarketProtectionDetail = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Box(Modifier.fillMaxSize()) {
                    ScreenContent(
                        state = state,
                        viewModel = viewModel,
                        protectionProjection = protectionProjection,
                        saves = saves,
                        saveDirectory = saveDirectory,
                        saveStatus = saveStatus,
                        onSaveGame = onSaveGame,
                        onLoadGame = onLoadGame,
                        onDeleteSave = onDeleteSave,
                        onReturnToLobby = onReturnToLobby,
                    )
                }
            }
        }

        if (showMarketProtectionDetail) {
            protectionProjection.marketStrip?.let { strip ->
                Dialog(onDismissRequest = { showMarketProtectionDetail = false }) {
                    MarketProtectionDetailSurface(
                        model = strip.detail,
                        modifier = Modifier.width(720.dp),
                        onClose = { showMarketProtectionDetail = false },
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

        if (state.isAdvancing) {
            TurnAdvanceLoadingDialog(hours = state.selectedTurnStep.hours)
        }
    }
}

@Composable
private fun TurnAdvanceLoadingDialog(hours: Int) {
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.width(420.dp),
            color = MarketColors.NavyRaised,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = MarketColors.SignalLine,
                    strokeWidth = 3.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "턴 처리 중",
                        style = MarketType.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.SignalLine,
                    )
                    Text(
                        text = "시장을 계산하고 있습니다",
                        style = MarketType.heading,
                        color = Color.White,
                    )
                    Text(
                        text = "${hours}시간 동안의 시세·주문·이벤트를 순서대로 반영합니다.",
                        style = MarketType.body,
                        color = MarketColors.Grey200,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    state: SimulatorUiState,
    viewModel: SimulatorViewModel,
    protectionProjection: ProtectionUiProjection,
    saves: List<GameSaveEntry>,
    saveDirectory: String,
    saveStatus: String,
    onSaveGame: (String) -> Unit,
    onLoadGame: (GameSaveEntry) -> Unit,
    onDeleteSave: (GameSaveEntry) -> Unit,
    onReturnToLobby: () -> Unit,
) {
    var eventNewsFilterState by remember { mutableStateOf(EventNewsFilterState()) }
    val portfolioHistory = state.portfolioSnapshots.ifEmpty { listOf(state.currentPortfolio) }
    val estimatedTax = state.annualTaxSummary?.totalPayableKrw?.toDouble() ?: 0.0
    val newsProjection = remember(
        state.currentTime,
        state.newsEvents,
        state.activeEvents,
        state.readEventIds,
        state.stocks,
        state.holdings.keys,
        state.watchlist,
        state.listingLifecycleStates,
        state.listingLifecycleLedger,
        state.pendingCorporateActions,
        state.corporateActionLedger,
        state.tradingProtectionSnapshot,
    ) {
        buildNewsUiProjection(
            currentTime = state.currentTime,
            events = state.newsEvents,
            activeEventIds = state.activeEvents.mapTo(linkedSetOf()) { it.id },
            readEventIds = state.readEventIds,
            stocks = state.stocks,
            holdingIds = state.holdings.keys,
            watchlistIds = state.watchlist,
            listingStates = state.listingLifecycleStates,
            listingLifecycleLedger = state.listingLifecycleLedger,
            pendingCorporateActions = state.pendingCorporateActions,
            corporateActionLedger = state.corporateActionLedger,
            tradingProtectionSnapshot = state.tradingProtectionSnapshot,
        )
    }
    val openStock: (String) -> Unit = { stockId ->
        viewModel.selectStock(stockId)
        viewModel.selectScreen(Screen.STOCK_DETAIL)
    }
    val openNews: (String?) -> Unit = { eventId ->
        eventNewsFilterState = eventNewsFilterState.copy(
            tab = NewsBrowseTab.BRIEFING,
            groupKey = "all",
            selectedEventId = eventId,
        )
        viewModel.selectScreen(Screen.EVENTS)
    }
    val selectedMetrics = remember(
        state.selectedStockId,
        state.stocks,
        state.quotes,
        state.corporateFundamentals,
        state.fundFinancialStates,
    ) {
        val stockId = state.selectedStockId ?: state.stocks.firstOrNull()?.id ?: return@remember null
        val stock = state.stocks.firstOrNull { it.id == stockId } ?: return@remember null
        val quote = state.quotes[stockId] ?: return@remember null
        InstrumentMetricsProjection.project(
            stock = stock,
            quote = quote,
            corporateState = state.corporateFundamentals[stockId],
            fundState = state.fundFinancialStates[stockId],
        )
    }

    when (state.screen) {
        Screen.HOME -> HomeDashboardScreen(
            snapshot = state.currentPortfolio,
            history = portfolioHistory,
            stocks = state.stocks,
            marketIndices = state.marketIndices,
            news = newsProjection,
            estimatedTaxKrw = estimatedTax,
            usdKrw = state.macro.usdKrw,
            maxDrawdown = state.maximumDrawdown,
            onOpenStock = openStock,
            onOpenEvents = openNews,
            onOpenTax = { viewModel.selectScreen(Screen.TAX_REPORT) },
        )

        Screen.MARKET,
        Screen.STOCK_DETAIL,
        -> MarketTradingScreen(
            stocks = state.stocks,
            quotes = state.quotes,
            chartPriceHistory = state.chartPriceHistory,
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
            protectionBadges = protectionProjection.symbolBadges,
            selectedProtectionDetail = protectionProjection.selectedSymbolDetail,
            selectedMetrics = selectedMetrics,
            orderUnavailableReason = { stockId, orderType ->
                state.orderSubmissionBlockReason(stockId, orderType)
            },
            relatedNews = newsProjection.stories,
            readStockNewsEventIds = state.readStockNewsEventIds,
            onRelatedNewsListViewed = viewModel::markStockNewsListViewed,
            onOpenEvent = { _, eventId -> openNews(eventId) },
        )

        Screen.ORDER -> OrdersScreen(
            orders = state.orders,
            trades = state.trades,
            stocks = state.stocks,
            grossTurnoverKrw = state.grossTradeTurnoverKrw,
            totalCostsKrw = state.totalTransactionCostKrw,
            protectionPendingLabels = state.orderProtectionPendingLabels(),
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
            projection = newsProjection,
            upcomingEvents = state.upcomingScheduledEvents,
            onOpenStock = openStock,
            onEventViewed = viewModel::markEventRead,
            onMarkAllEventsRead = viewModel::markAllEventsRead,
            filterState = eventNewsFilterState,
            onFilterStateChange = { eventNewsFilterState = it },
        )

        Screen.ANALYTICS -> AnalyticsScreen(
            snapshot = state.currentPortfolio,
            history = portfolioHistory,
            trades = state.trades,
            stocks = state.stocks,
            grossTurnoverKrw = state.grossTradeTurnoverKrw,
            totalCostsKrw = state.totalTransactionCostKrw,
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
                externalMarketForcesTarget = state.externalMarketForcesTarget,
                marketDynamicsSnapshot = state.marketDynamicsSnapshot,
            ),
            onAutoExchangeChanged = viewModel::setAutoExchange,
            onExternalMarketForcesChanged = viewModel::setExternalMarketForces,
            onExchangeKrwToUsd = { viewModel.exchange(Currency.KRW, Currency.USD, it) },
            onExchangeUsdToKrw = { viewModel.exchange(Currency.USD, Currency.KRW, it) },
            saves = saves,
            saveDirectory = saveDirectory,
            saveStatus = saveStatus,
            onSaveGame = onSaveGame,
            onLoadGame = onLoadGame,
            onDeleteSave = onDeleteSave,
            onResetGame = onReturnToLobby,
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

private fun SimulatorUiState.orderSubmissionBlockReason(stockId: String, orderType: OrderType): String? {
    val stock = stocks.firstOrNull { it.id == stockId } ?: return "존재하지 않는 종목이에요."
    val listing = listingLifecycleStates[stockId]
    if (listing != null && !listing.isOrderAllowed) {
        return when {
            listing.isTerminal -> "거래가 종료된 종목이에요."
            listing.isSettlementPending -> "청산금 지급 절차가 진행 중이에요."
            else -> "상장 유지 심사 또는 상장폐지 절차로 거래가 멈췄어요."
        }
    }
    val snapshot = tradingProtectionSnapshot
    val decision = TradingProtectionEngine.permission(
        snapshot,
        TradingProtectionRequest(
            market = stock.market,
            action = TradingProtectionAction.SUBMIT_ORDER,
            stockId = stockId,
            isAuctionEligibleOrder = orderType == OrderType.LIMIT,
        ),
        currentTime,
    )
    return decision.controllingRestriction?.message.takeUnless { decision.allowed }
}

private fun SimulatorUiState.orderProtectionPendingLabels(): Map<String, String> {
    val snapshot = tradingProtectionSnapshot
    val stocksById = stocks.associateBy { it.id }
    return orders.asSequence()
        .filter { it.isOpen }
        .mapNotNull { order ->
            val stock = stocksById[order.stockId] ?: return@mapNotNull null
            val listing = listingLifecycleStates[order.stockId]
            val listingLabel = if (listing != null && !listing.isTradable) "상장절차 대기" else null
            val decision = TradingProtectionEngine.permission(
                snapshot,
                TradingProtectionRequest(
                    market = stock.market,
                    action = TradingProtectionAction.EXECUTE_TRADE,
                    stockId = stock.id,
                    proposedExecutionPrice = order.limitPrice ?: quotes[stock.id]?.price,
                ),
                currentTime,
            )
            val protectionLabel = decision.controllingRestriction?.source?.toPendingLabel()
            (listingLabel ?: protectionLabel)?.let { order.id to it }
        }
        .toMap()
}

private fun TradingRestrictionSource.toPendingLabel(): String = when (this) {
    TradingRestrictionSource.KRX_MARKET_CIRCUIT_BREAKER,
    TradingRestrictionSource.US_MARKET_WIDE_CIRCUIT_BREAKER,
    -> "시장정지 대기"
    TradingRestrictionSource.KRX_VOLATILITY_INTERRUPTION -> "VI 대기"
    TradingRestrictionSource.US_LIMIT_UP_LIMIT_DOWN -> "변동성정지 대기"
    TradingRestrictionSource.INSTRUMENT_TRADING_HALT -> "거래정지 대기"
    TradingRestrictionSource.KRX_SIDECAR -> "프로그램매매 대기"
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
