package com.amond.kmpbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import com.amond.kmpbook.debug.console.DebugConsoleCommandProcessor
import com.amond.kmpbook.debug.console.DebugConsoleSession
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
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
import com.amond.kmpbook.modding.builtin.debug.DebugMod
import com.amond.kmpbook.modding.model.ActiveModConfiguration
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModCatalog
import com.amond.kmpbook.modding.model.ModLoadIssue
import com.amond.kmpbook.modding.storage.ModStorage
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
import com.amond.kmpbook.presentation.news.NewsUiProjection
import com.amond.kmpbook.presentation.news.buildNewsUiProjection
import com.amond.kmpbook.presentation.protection.ProtectionUiProjection
import com.amond.kmpbook.presentation.protection.buildProtectionUiProjection
import com.amond.kmpbook.presentation.settings.AppSettingsStorage
import com.amond.kmpbook.presentation.settings.AudioSettings
import com.amond.kmpbook.presentation.simulator.NewGameOptions
import com.amond.kmpbook.presentation.simulator.SimulatorUiState
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import com.amond.kmpbook.ui.components.MarketProtectionDetailSurface
import com.amond.kmpbook.ui.components.MarketProtectionStrip
import com.amond.kmpbook.ui.screens.dashboard.HomeDashboardScreen
import com.amond.kmpbook.ui.screens.debug.DebugConsoleOverlay
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.toLocalDateTime

private const val GAME_MESSAGE_DISPLAY_MILLIS = 3_000L
private const val APP_SETTINGS_SAVE_DEBOUNCE_MILLIS = 250L

private fun activeModCompatibilityError(
    required: List<ActiveModConfiguration>,
    installed: List<InstalledMod>,
): String? {
    if (required.isEmpty()) return null
    val installedById = installed.associateBy(InstalledMod::id)
    val missing = required.firstOrNull { it.id !in installedById }
    if (missing != null) {
        return "이 저장 게임에 필요한 ${missing.id} 모드가 설치되어 있지 않습니다."
    }
    val incompatible = required.firstOrNull { saved ->
        installedById.getValue(saved.id).version != saved.version
    }
    if (incompatible != null) {
        val currentVersion = installedById.getValue(incompatible.id).version
        return "${incompatible.id} 모드 버전이 저장 게임과 다릅니다. " +
            "필요 ${incompatible.version} · 설치 $currentVersion"
    }
    val changedContent = required.firstOrNull { saved ->
        saved.contentFingerprint != installedById.getValue(saved.id).instrumentPack?.fingerprint
    }
    if (changedContent != null) {
        return "${changedContent.id} 모드의 종목 콘텐츠가 저장 게임을 시작할 때와 다릅니다."
    }
    val invalidConfiguration = required.firstOrNull { saved ->
        val current = installedById.getValue(saved.id)
        val definitions = current.settings.associateBy { it.key }
        saved.settings.keys != definitions.keys || saved.settings.any { (key, value) ->
            definitions[key]?.validate(value) != null
        }
    }
    if (invalidConfiguration != null) {
        return "${invalidConfiguration.id} 모드 설정이 현재 manifest와 일치하지 않습니다."
    }
    return null
}

private fun resolveInstrumentCatalog(
    baseCatalog: InstrumentCatalogSnapshot,
    mods: Iterable<InstalledMod>,
): InstrumentCatalogSnapshot = baseCatalog.withAdditionalPacks(
    mods
        .sortedBy(InstalledMod::id)
        .mapNotNull(InstalledMod::instrumentPack),
)

@Composable
fun App(
    baseInstrumentCatalog: InstrumentCatalogSnapshot,
    onExitRequest: () -> Unit = {},
    onExitBlockedChanged: (Boolean) -> Unit = {},
    escapeRequest: Int = 0,
    debugConsoleToggleRequest: Int = 0,
    onDebugConsoleAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val viewModel = remember(baseInstrumentCatalog) { SimulatorViewModel(baseInstrumentCatalog) }
    val debugConsoleProcessor = remember(viewModel) { DebugConsoleCommandProcessor(viewModel) }
    val debugConsoleSession = remember { DebugConsoleSession() }
    val storage = remember { GameSaveStorage() }
    val appSettingsStorage = remember { AppSettingsStorage() }
    val modStorage = remember { ModStorage() }
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var entryDestination by remember { mutableStateOf(GameEntryDestination.LOBBY) }
    var saves by remember { mutableStateOf(emptyList<GameSaveEntry>()) }
    var saveStatus by remember { mutableStateOf("저장된 게임을 확인하고 있습니다.") }
    var isSavingGame by remember { mutableStateOf(false) }
    var isLoadingGame by remember { mutableStateOf(false) }
    var installedMods by remember { mutableStateOf(emptyList<InstalledMod>()) }
    var modLoadIssues by remember { mutableStateOf(emptyList<ModLoadIssue>()) }
    var modStatusMessage by remember { mutableStateOf("모드 폴더를 확인하고 있습니다.") }
    var isScanningMods by remember { mutableStateOf(true) }
    var activeModMutations by remember { mutableStateOf(0) }
    var isStartingNewGame by remember { mutableStateOf(false) }
    var isDebugConsoleVisible by remember { mutableStateOf(false) }
    val modMutationMutex = remember { Mutex() }
    val isModCatalogBusy = isScanningMods || activeModMutations > 0 || isStartingNewGame
    val areModControlsBusy = isScanningMods || activeModMutations > 0
    var audioSettings by remember(appSettingsStorage) {
        mutableStateOf(appSettingsStorage.loadAudioSettings())
    }
    val activeDebugMod = state.options.activeMods.firstOrNull { activeMod ->
        DebugMod.isCompatible(activeMod.id, activeMod.version)
    }
    val isDebugConsoleAvailable =
        state.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED) &&
            activeDebugMod != null &&
            !isSavingGame &&
            !isLoadingGame

    SideEffect {
        onExitBlockedChanged(activeModMutations > 0)
        onDebugConsoleAvailabilityChanged(isDebugConsoleAvailable)
    }

    LaunchedEffect(storage) {
        val catalog = storage.list()
        saves = catalog.entries
        saveStatus = catalog.error?.message ?: if (saves.isEmpty()) {
            "저장된 게임이 없습니다."
        } else {
            "저장된 게임 ${saves.size}개를 찾았습니다."
        }
    }
    fun publishModCatalog(catalog: ModCatalog) {
        installedMods = catalog.mods
        modLoadIssues = catalog.issues
        modStatusMessage = when {
            catalog.mods.isEmpty() && catalog.issues.isEmpty() -> "설치된 모드가 없습니다."
            catalog.mods.count(InstalledMod::enabled) > NewGameOptions.MAX_ACTIVE_MODS ->
                "활성 모드는 ${NewGameOptions.MAX_ACTIVE_MODS}개까지만 새 게임에 적용할 수 있습니다."
            catalog.issues.isNotEmpty() ->
                "모드 ${catalog.mods.size}개를 찾았고 ${catalog.issues.size}개는 불러오지 못했습니다."
            else -> "모드 ${catalog.mods.size}개를 불러왔습니다."
        }
    }
    suspend fun refreshMods(): ModCatalog? {
        isScanningMods = true
        return try {
            val catalog = modStorage.scan()
            publishModCatalog(catalog)
            catalog
        } catch (_: RuntimeException) {
            installedMods = emptyList()
            modLoadIssues = listOf(ModLoadIssue("mods", "모드 폴더를 안전하게 읽지 못했습니다."))
            modStatusMessage = "모드 폴더를 확인하지 못했습니다. 다시 새로고침하세요."
            null
        } finally {
            isScanningMods = false
        }
    }
    suspend fun persistModChange(
        operation: suspend () -> String?,
        successMessage: String,
    ) {
        activeModMutations++
        try {
            val (error, catalog) = modMutationMutex.withLock {
                operation() to modStorage.scan()
            }
            publishModCatalog(catalog)
            modStatusMessage = error ?: successMessage
        } catch (_: RuntimeException) {
            installedMods = emptyList()
            modLoadIssues = listOf(ModLoadIssue("mods", "변경 후 모드 폴더를 안전하게 읽지 못했습니다."))
            modStatusMessage = "모드 변경 결과를 확인하지 못했습니다. 다시 새로고침하세요."
        } finally {
            activeModMutations--
        }
    }
    LaunchedEffect(modStorage) {
        refreshMods()
    }
    LaunchedEffect(debugConsoleToggleRequest) {
        if (debugConsoleToggleRequest > 0 && isDebugConsoleAvailable) {
            isDebugConsoleVisible = !isDebugConsoleVisible
        }
    }
    LaunchedEffect(isDebugConsoleAvailable) {
        if (!isDebugConsoleAvailable) isDebugConsoleVisible = false
    }
    LaunchedEffect(activeDebugMod?.settings) {
        activeDebugMod?.let { debugMod -> debugConsoleSession.configure(debugMod.settings) }
    }
    LaunchedEffect(escapeRequest) {
        if (escapeRequest <= 0) return@LaunchedEffect
        if (isDebugConsoleVisible) {
            isDebugConsoleVisible = false
        } else if (
            escapeRequest > 0 &&
            !state.isAdvancing &&
            state.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED)
        ) {
            viewModel.selectScreen(Screen.SETTINGS)
        }
    }
    LaunchedEffect(appSettingsStorage, audioSettings) {
        delay(APP_SETTINGS_SAVE_DEBOUNCE_MILLIS)
        withContext(Dispatchers.IO) {
            appSettingsStorage.saveAudioSettings(audioSettings)
        }
    }

    val refreshSaves: suspend () -> Unit = {
        val catalog = storage.list()
        saves = catalog.entries
        catalog.error?.let { saveStatus = it.message }
    }
    val saveGame: (String) -> Unit = saveGameAction@{ name ->
        if (isSavingGame || isLoadingGame) return@saveGameAction
        isSavingGame = true
        scope.launch {
            try {
                when (val result = storage.save(viewModel.currentState, name)) {
                    is GameSaveSuccess -> {
                        saveStatus = "${result.path.substringAfterLast('/').substringAfterLast('\\')} 파일로 저장했습니다."
                        refreshSaves()
                    }
                    is GameSaveFailure -> saveStatus = result.error.message
                }
            } finally {
                isSavingGame = false
            }
        }
    }
    val loadGame: (GameSaveEntry) -> Unit = loadGameAction@{ save ->
        if (isSavingGame || isLoadingGame) return@loadGameAction
        if (isModCatalogBusy) {
            saveStatus = "모드 목록을 확인한 뒤 게임을 불러올 수 있습니다."
            return@loadGameAction
        }
        if (
            viewModel.currentState.options.ironmanMode &&
            viewModel.currentState.phase in setOf(GamePhase.PLAYING, GamePhase.PAUSED)
        ) {
            saveStatus = "철인 모드에서는 게임을 불러올 수 없습니다."
            return@loadGameAction
        }
        isLoadingGame = true
        scope.launch {
            try {
                when (val result = storage.load(save.fileName)) {
                    is GameLoadSuccess -> {
                        val currentCatalog = refreshMods()
                        if (currentCatalog == null) {
                            saveStatus = "현재 모드 목록을 확인하지 못해 게임을 불러오지 않았습니다."
                        } else {
                            val modCompatibilityError = activeModCompatibilityError(
                                required = result.state.options.activeMods,
                                installed = currentCatalog.mods,
                            )
                            if (modCompatibilityError != null) {
                                saveStatus = modCompatibilityError
                            } else {
                                val installedById = currentCatalog.mods.associateBy(InstalledMod::id)
                                val requiredMods = result.state.options.activeMods.map { required ->
                                    installedById.getValue(required.id)
                                }
                                val resolvedCatalog = runCatching {
                                    resolveInstrumentCatalog(baseInstrumentCatalog, requiredMods)
                                }.getOrElse { error ->
                                    saveStatus = "저장 게임의 종목 카탈로그를 구성하지 못했습니다: " +
                                        (error.message ?: "알 수 없는 오류")
                                    return@launch
                                }
                                if (viewModel.restoreGame(result.state, resolvedCatalog)) {
                                    saveStatus = "${save.name} 게임을 불러왔습니다."
                                } else {
                                    saveStatus = "저장된 게임을 확인할 수 없습니다."
                                }
                            }
                        }
                    }
                    is GameLoadNotFound -> saveStatus = result.message
                    is GameLoadFailure -> saveStatus = result.error.message
                }
            } finally {
                isLoadingGame = false
            }
        }
    }
    val deleteSave: (GameSaveEntry) -> Unit = deleteSaveAction@{ save ->
        if (isSavingGame || isLoadingGame) return@deleteSaveAction
        scope.launch {
            when (val result = storage.delete(save.fileName)) {
                is GameSaveDeleted -> {
                    saveStatus = "${save.name} 게임을 삭제했습니다."
                    refreshSaves()
                }
                is GameSaveDeleteNotFound -> saveStatus = "삭제할 게임이 없습니다."
                is GameSaveDeleteFailure -> saveStatus = result.error.message
            }
        }
    }
    val openSaveDirectory: () -> Unit = {
        scope.launch {
            val error = storage.openSaveDirectory()
            saveStatus = error ?: "저장 폴더를 탐색기로 열었습니다."
        }
    }
    val refreshModsAction: () -> Unit = {
        if (!isModCatalogBusy) scope.launch { refreshMods() }
    }
    val toggleMod: (InstalledMod, Boolean) -> Unit = toggleModAction@{ mod, enabled ->
        if (isModCatalogBusy) {
            modStatusMessage = "진행 중인 모드 작업이 끝난 뒤 활성 상태를 변경할 수 있습니다."
            return@toggleModAction
        }
        if (
            enabled &&
            !mod.enabled &&
            installedMods.count(InstalledMod::enabled) >= NewGameOptions.MAX_ACTIVE_MODS
        ) {
            modStatusMessage = "활성 모드는 ${NewGameOptions.MAX_ACTIVE_MODS}개까지 선택할 수 있습니다."
            return@toggleModAction
        }
        installedMods = installedMods.map { installed ->
            if (installed.id == mod.id) installed.copy(enabled = enabled) else installed
        }
        modStatusMessage = "${mod.name} 모드 상태를 저장하는 중입니다."
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            persistModChange(
                operation = { modStorage.setEnabled(mod.id, enabled) },
                successMessage = if (enabled) {
                    "${mod.name} 모드를 활성화했습니다. 다음 새 게임부터 적용됩니다."
                } else {
                    "${mod.name} 모드를 비활성화했습니다."
                },
            )
        }
    }
    val updateModSetting: (InstalledMod, String, String) -> Unit = updateModSettingAction@{ mod, key, value ->
        if (areModControlsBusy) {
            modStatusMessage = "진행 중인 모드 작업이 끝난 뒤 설정을 변경할 수 있습니다."
            return@updateModSettingAction
        }
        installedMods = installedMods.map { installed ->
            if (installed.id == mod.id) {
                installed.copy(configuration = installed.configuration + (key to value))
            } else {
                installed
            }
        }
        modStatusMessage = "${mod.name} 설정을 저장하는 중입니다."
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            persistModChange(
                operation = { modStorage.setSetting(mod.id, key, value) },
                successMessage = "${mod.name} 설정을 저장했습니다.",
            )
        }
    }
    val openModsDirectory: () -> Unit = {
        scope.launch {
            modStorage.openModsDirectory()?.let { error ->
                modStatusMessage = error
            }
        }
    }

    MarketSimulatorTheme {
        Box(Modifier.fillMaxSize()) {
            when (state.phase) {
                GamePhase.SETUP -> when (entryDestination) {
                    GameEntryDestination.LOBBY -> GameLobbyScreen(
                        saves = saves,
                        saveStatus = saveStatus,
                        mods = installedMods,
                        modIssues = modLoadIssues,
                        modStatusMessage = modStatusMessage,
                        isModCatalogBusy = isModCatalogBusy,
                        areModControlsBusy = areModControlsBusy,
                        onContinue = loadGame,
                        onLoad = loadGame,
                        onStartNewGame = startNewGame@{ options ->
                            if (isModCatalogBusy || isStartingNewGame) return@startNewGame
                            isStartingNewGame = true
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    val currentCatalog = refreshMods() ?: return@launch
                                    val enabledMods = currentCatalog.mods
                                        .filter(InstalledMod::enabled)
                                        .sortedBy(InstalledMod::id)
                                    if (enabledMods.size > NewGameOptions.MAX_ACTIVE_MODS) return@launch
                                    val resolvedCatalog = runCatching {
                                        resolveInstrumentCatalog(baseInstrumentCatalog, enabledMods)
                                    }.getOrElse { error ->
                                        modStatusMessage = "활성 모드의 종목 카탈로그를 구성하지 못했습니다: " +
                                            (error.message ?: "알 수 없는 오류")
                                        return@launch
                                    }
                                    viewModel.newGame(
                                        options.copy(
                                            activeMods = enabledMods.map { mod ->
                                                ActiveModConfiguration(
                                                    id = mod.id,
                                                    version = mod.version,
                                                    settings = mod.settings.associate { definition ->
                                                        definition.key to (
                                                            mod.settingValue(definition.key) ?: definition.defaultValue
                                                        )
                                                    },
                                                    contentFingerprint = mod.instrumentPack?.fingerprint,
                                                )
                                            },
                                        ),
                                        resolvedCatalog,
                                    )?.let { errorMessage ->
                                        modStatusMessage = errorMessage
                                        return@launch
                                    }
                                } finally {
                                    isStartingNewGame = false
                                }
                            }
                        },
                        onToggleMod = toggleMod,
                        onModSettingChanged = updateModSetting,
                        onRefreshMods = refreshModsAction,
                        onOpenModsDirectory = openModsDirectory,
                        onSettings = { entryDestination = GameEntryDestination.SETTINGS },
                        onExit = {
                            if (activeModMutations == 0) onExitRequest()
                        },
                    )
                    GameEntryDestination.SETTINGS -> LobbySettingsScreen(
                        saveDirectory = storage.saveDirectory,
                        saveCount = saves.size,
                        audioSettings = audioSettings,
                        onAudioSettingsChanged = { audioSettings = it },
                        onOpenSaveDirectory = openSaveDirectory,
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
                        if (state.phase == GamePhase.SETTLEMENT) {
                            viewModel.finishSettlement()
                        }
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
                    isSavingGame = isSavingGame,
                    isLoadingGame = isLoadingGame,
                    audioSettings = audioSettings,
                    onAudioSettingsChanged = { audioSettings = it },
                    onSaveGame = saveGame,
                    onLoadGame = loadGame,
                    onDeleteSave = deleteSave,
                    onOpenSaveDirectory = openSaveDirectory,
                    onReturnToLobby = {
                        viewModel.resetGame()
                        entryDestination = GameEntryDestination.LOBBY
                    },
                )
            }

            DebugConsoleOverlay(
                visible = isDebugConsoleVisible,
                session = debugConsoleSession,
                onExecute = {
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        debugConsoleSession.execute(debugConsoleProcessor)
                    }
                },
                onDismiss = { isDebugConsoleVisible = false },
            )

            if (isSavingGame || isLoadingGame) {
                GameSaveLoadingDialog(isSaving = isSavingGame)
            }
        }
    }
}

@Composable
private fun GameSaveLoadingDialog(isSaving: Boolean) {
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.width(380.dp),
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
                        text = if (isSaving) "게임 저장 중" else "게임 불러오는 중",
                        style = MarketType.heading,
                        color = Color.White,
                    )
                    Text(
                        text = if (isSaving) "현재 게임 상태를 파일에 기록하고 있습니다." else "저장된 게임 상태를 확인하고 복원하고 있습니다.",
                        style = MarketType.body,
                        color = MarketColors.Grey200,
                    )
                }
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
    isSavingGame: Boolean,
    isLoadingGame: Boolean,
    audioSettings: AudioSettings,
    onAudioSettingsChanged: (AudioSettings) -> Unit,
    onSaveGame: (String) -> Unit,
    onLoadGame: (GameSaveEntry) -> Unit,
    onDeleteSave: (GameSaveEntry) -> Unit,
    onOpenSaveDirectory: () -> Unit,
    onReturnToLobby: () -> Unit,
) {
    val needsInstrumentProtection = state.screen == Screen.MARKET || state.screen == Screen.STOCK_DETAIL
    val protectionListingStates = if (needsInstrumentProtection) {
        state.listingLifecycleStates
    } else {
        emptyMap()
    }
    val protectionStockId = if (needsInstrumentProtection) state.selectedStockId else null
    val selectedMarket = if (needsInstrumentProtection) state.selectedStock?.market else null
    val protectionProjection = remember(
        state.tradingProtectionSnapshot,
        protectionListingStates,
        protectionStockId,
        selectedMarket,
        state.currentTime,
    ) {
        buildProtectionUiProjection(
            snapshot = state.tradingProtectionSnapshot,
            listingStates = protectionListingStates,
            selectedStockId = protectionStockId,
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
                        isSavingGame = isSavingGame,
                        isLoadingGame = isLoadingGame,
                        audioSettings = audioSettings,
                        onAudioSettingsChanged = onAudioSettingsChanged,
                        onSaveGame = onSaveGame,
                        onLoadGame = onLoadGame,
                        onDeleteSave = onDeleteSave,
                        onOpenSaveDirectory = onOpenSaveDirectory,
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
            TurnAdvanceLoadingOverlay(
                hours = state.selectedTurnStep.hours,
                detailMessage = state.lastMessage,
                onCancel = viewModel::cancelAdvance,
            )
        }
    }
}

@Composable
private fun TurnAdvanceLoadingOverlay(
    hours: Int,
    detailMessage: String?,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MarketColors.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(420.dp),
            color = MarketColors.NavyRaised,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Row(
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
                            text = detailMessage
                                ?: "${hours}시간 동안의 시세·주문·이벤트를 순서대로 반영합니다.",
                            style = MarketType.body,
                            color = MarketColors.Grey200,
                        )
                    }
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("진행 취소", style = MarketType.label, color = MarketColors.Grey200)
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
    isSavingGame: Boolean,
    isLoadingGame: Boolean,
    audioSettings: AudioSettings,
    onAudioSettingsChanged: (AudioSettings) -> Unit,
    onSaveGame: (String) -> Unit,
    onLoadGame: (GameSaveEntry) -> Unit,
    onDeleteSave: (GameSaveEntry) -> Unit,
    onOpenSaveDirectory: () -> Unit,
    onReturnToLobby: () -> Unit,
) {
    var eventNewsFilterState by remember { mutableStateOf(EventNewsFilterState()) }
    val newsProjection = rememberNewsUiProjection(state)
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

    when (state.screen) {
        Screen.HOME -> {
            HomeDashboardScreen(
                snapshot = state.currentPortfolio,
                history = state.portfolioSnapshots.ifEmpty { listOf(state.currentPortfolio) },
                stocks = state.stocks,
                marketIndices = state.marketIndices,
                news = newsProjection.value,
                estimatedTaxKrw = state.annualTaxSummary?.totalPayableKrw?.toDouble() ?: 0.0,
                usdKrw = state.macro.usdKrw,
                maxDrawdown = state.maximumDrawdown,
                onOpenStock = openStock,
                onOpenEvents = openNews,
                onOpenTax = { viewModel.selectScreen(Screen.TAX_REPORT) },
            )
        }

        Screen.MARKET,
        Screen.STOCK_DETAIL,
        -> {
            val selectedMetrics = rememberSelectedInstrumentMetrics(state)
            MarketTradingScreen(
                stocks = state.stocks,
                quotes = state.quotes,
                hourlyPriceHistory = state.priceHistory,
                chartPriceHistory = state.chartPriceHistory,
                trades = state.trades,
                isAdvancing = state.isAdvancing,
                selectedStockId = state.selectedStockId,
                holding = state.selectedHolding,
                orderBook = state.selectedOrderBook?.toOrderBook(),
                cashKrw = state.cashByCurrency[Currency.KRW] ?: 0.0,
                cashUsd = state.cashByCurrency[Currency.USD] ?: 0.0,
                usdKrw = state.macro.usdKrw,
                onExchangeKrwToUsd = { viewModel.exchange(Currency.KRW, Currency.USD, it) },
                onExchangeUsdToKrw = { viewModel.exchange(Currency.USD, Currency.KRW, it) },
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
                relatedNews = newsProjection.value.stories,
                readStockNewsEventIds = state.readStockNewsEventIds,
                onRelatedNewsListViewed = viewModel::markStockNewsListViewed,
                onOpenEvent = { _, eventId -> openNews(eventId) },
            )
        }

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
            history = state.portfolioSnapshots.ifEmpty { listOf(state.currentPortfolio) },
            stocks = state.stocks,
            onOpenStock = openStock,
        )

        Screen.EVENTS -> {
            EventsScreen(
                projection = newsProjection.value,
                upcomingEvents = state.upcomingScheduledEvents,
                onOpenStock = openStock,
                onEventViewed = viewModel::markEventRead,
                onMarkAllEventsRead = viewModel::markAllEventsRead,
                filterState = eventNewsFilterState,
                onFilterStateChange = { eventNewsFilterState = it },
            )
        }

        Screen.ANALYTICS -> AnalyticsScreen(
            snapshot = state.currentPortfolio,
            history = state.portfolioSnapshots.ifEmpty { listOf(state.currentPortfolio) },
            trades = state.trades,
            stocks = state.stocks,
            grossTurnoverKrw = state.grossTradeTurnoverKrw,
            totalCostsKrw = state.totalTransactionCostKrw,
            maxDrawdown = state.maximumDrawdown,
        )

        Screen.TAX_REPORT -> TaxCenterScreen(state.toTaxCenterData())

        Screen.SETTINGS -> SettingsScreen(
            settings = GameSettingsDisplay(
                scenarioName = state.options.scenarioName,
                difficultyName = state.options.difficultyName,
                initialCapitalKrw = state.initialCapitalKrw,
                seed = state.seed,
                fractionalUsTrading = state.usFractionalTrading,
                ironmanMode = state.options.ironmanMode,
                externalMarketForcesTarget = state.externalMarketForcesTarget,
                marketDynamicsSnapshot = state.marketDynamicsSnapshot,
            ),
            onExternalMarketForcesChanged = viewModel::setExternalMarketForces,
            saves = saves,
            saveDirectory = saveDirectory,
            saveStatus = saveStatus,
            isSaving = isSavingGame,
            isLoading = isLoadingGame,
            audioSettings = audioSettings,
            onAudioSettingsChanged = onAudioSettingsChanged,
            onSaveGame = onSaveGame,
            onLoadGame = onLoadGame,
            onDeleteSave = onDeleteSave,
            onOpenSaveDirectory = onOpenSaveDirectory,
            onResetGame = onReturnToLobby,
        )

        Screen.ENDING -> EndingScreen(
            snapshot = state.currentPortfolio,
            history = state.portfolioSnapshots.ifEmpty { listOf(state.currentPortfolio) },
            tradeCount = state.trades.size,
            eventCount = state.newsEvents.size,
            totalTaxKrw = state.totalSaleTaxKrw +
                (state.annualTaxSummary?.totalPayableKrw?.toDouble() ?: 0.0),
            maxDrawdown = state.maximumDrawdown,
            onNewGame = viewModel::resetGame,
        )
    }
}

@Composable
private fun rememberNewsUiProjection(state: SimulatorUiState): Lazy<NewsUiProjection> = remember(
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
    lazy(LazyThreadSafetyMode.NONE) {
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
}

@Composable
private fun rememberSelectedInstrumentMetrics(state: SimulatorUiState) = remember(
    state.selectedStockId,
    state.stocks,
    state.quotes,
    state.corporateFundamentals,
    state.fundFinancialStates,
    state.etnStates,
    state.closedEndFundStates,
) {
    val stockId = state.selectedStockId ?: state.stocks.firstOrNull()?.id ?: return@remember null
    val stock = state.stocks.firstOrNull { it.id == stockId } ?: return@remember null
    val quote = state.quotes[stockId] ?: return@remember null
    InstrumentMetricsProjection.project(
        stock = stock,
        quote = quote,
        corporateState = state.corporateFundamentals[stockId],
        fundState = state.fundFinancialStates[stockId],
        etnState = state.etnStates[stockId],
        closedEndFundState = state.closedEndFundStates[stockId],
    )
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
