package com.amond.kmpbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.amond.kmpbook.domain.data.DesktopInstrumentPackParser
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.data.InstrumentPack
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketDesignSystem
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketSimulatorTheme
import com.amond.kmpbook.ui.theme.MarketType
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.market_ledger_icon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private const val BASE_INSTRUMENT_CATALOG_PATH: String = "files/instruments/base-catalog.json"
private const val BASE_INSTRUMENT_SOURCE_ID: String = "builtin:base"

private suspend fun loadBaseInstrumentCatalog(): InstrumentCatalogSnapshot {
    val bytes = withContext(Dispatchers.IO) {
        Res.readBytes(BASE_INSTRUMENT_CATALOG_PATH)
    }
    return withContext(Dispatchers.Default) {
        val pack = DesktopInstrumentPackParser.parse(
            bytes = bytes,
            sourceId = BASE_INSTRUMENT_SOURCE_ID,
            maxInstruments = InstrumentPack.MAX_INSTRUMENTS,
        )
        InstrumentCatalogSnapshot.fromPacks(listOf(pack))
    }
}

fun main() {
    nucleusApplication(backend = NucleusBackend.Tao) {
        var escapeRequest by remember { mutableIntStateOf(0) }
        var debugConsoleToggleRequest by remember { mutableIntStateOf(0) }
        var isDebugConsoleAvailable by remember { mutableStateOf(false) }
        var isGravePressed by remember { mutableStateOf(false) }
        var isEscapePressed by remember { mutableStateOf(false) }
        var isExitBlocked by remember { mutableStateOf(false) }
        var bootstrapAttempt by remember { mutableIntStateOf(0) }
        var bootstrapStage by remember { mutableIntStateOf(0) }
        var bootstrapStatus by remember { mutableStateOf("기본 종목 카탈로그를 읽고 있습니다.") }
        var bootstrapError by remember { mutableStateOf<String?>(null) }
        var baseCatalog by remember { mutableStateOf<InstrumentCatalogSnapshot?>(null) }
        var simulatorViewModel by remember { mutableStateOf<SimulatorViewModel?>(null) }
        val windowState = rememberWindowState(
            width = MarketLayout.defaultWindowWidth,
            height = MarketLayout.defaultWindowHeight,
            position = WindowPosition(Alignment.Center),
        )
        DecoratedWindow(
            onCloseRequest = { if (!isExitBlocked) exitApplication() },
            title = "${MarketDesignSystem.NAME} · Stock Simulator",
            icon = painterResource(Res.drawable.market_ledger_icon),
            state = windowState,
            minimumSize = DpSize(
                MarketLayout.minimumWindowWidthPx.dp,
                MarketLayout.minimumWindowHeightPx.dp,
            ),
            nativePopupLayers = true,
            onPreviewKeyEvent = { event ->
                val isUnmodifiedGrave = event.key == Key.Grave &&
                    !event.isAltPressed &&
                    !event.isCtrlPressed &&
                    !event.isMetaPressed &&
                    !event.isShiftPressed
                when {
                    event.key == Key.Grave &&
                        isDebugConsoleAvailable &&
                        (isGravePressed || isUnmodifiedGrave) -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> if (!isGravePressed) {
                                isGravePressed = true
                                debugConsoleToggleRequest += 1
                            }
                            KeyEventType.KeyUp -> isGravePressed = false
                        }
                        true
                    }
                    event.key == Key.Escape -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> if (!isEscapePressed) {
                                isEscapePressed = true
                                escapeRequest += 1
                            }
                            KeyEventType.KeyUp -> isEscapePressed = false
                        }
                        true
                    }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(bootstrapAttempt) {
                bootstrapStage = 0
                bootstrapStatus = "기본 종목 카탈로그를 읽고 있습니다."
                bootstrapError = null
                baseCatalog = null
                simulatorViewModel = null
                try {
                    val catalog = loadBaseInstrumentCatalog()
                    bootstrapStage = 1
                    bootstrapStatus = "시장 엔진과 거래 원장을 준비하고 있습니다."
                    val viewModel = withContext(Dispatchers.Default) {
                        SimulatorViewModel(catalog)
                    }
                    bootstrapStage = 2
                    bootstrapStatus = "화면을 준비하고 있습니다."
                    baseCatalog = catalog
                    simulatorViewModel = viewModel
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    bootstrapError = error.message?.take(300)?.takeIf(String::isNotBlank)
                        ?: "원인을 확인할 수 없는 초기화 오류가 발생했습니다."
                }
            }

            val readyCatalog = baseCatalog
            val readyViewModel = simulatorViewModel
            if (readyCatalog != null && readyViewModel != null) {
                App(
                    baseInstrumentCatalog = readyCatalog,
                    viewModel = readyViewModel,
                    onExitRequest = ::exitApplication,
                    onExitBlockedChanged = { blocked -> isExitBlocked = blocked },
                    escapeRequest = escapeRequest,
                    debugConsoleToggleRequest = debugConsoleToggleRequest,
                    onDebugConsoleAvailabilityChanged = { available ->
                        isDebugConsoleAvailable = available
                        if (!available) isGravePressed = false
                    },
                )
            } else {
                BootstrapLoadingScreen(
                    stage = bootstrapStage,
                    status = bootstrapStatus,
                    error = bootstrapError,
                    onRetry = { bootstrapAttempt++ },
                )
            }
        }
    }
}

@Composable
private fun BootstrapLoadingScreen(
    stage: Int,
    status: String,
    error: String?,
    onRetry: () -> Unit,
) {
    MarketSimulatorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MarketColors.Ledger,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(MarketLayout.screenPadding),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.width(560.dp),
                    color = MarketColors.NavyRaised,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = "MARKET LEDGER",
                            style = MarketType.caption,
                            color = MarketColors.SignalLine,
                        )
                        Text(
                            text = if (error == null) "시뮬레이션 장부를 여는 중" else "초기화를 완료하지 못했습니다",
                            style = MarketType.heading,
                            color = Color.White,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("카탈로그", "시장 엔진", "화면 준비").forEachIndexed { index, label ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = when {
                                        error != null && index == stage -> MarketColors.Rise.copy(alpha = 0.28f)
                                        index <= stage -> MarketColors.SignalLine.copy(alpha = 0.22f)
                                        else -> MarketColors.Navy
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        style = MarketType.caption,
                                        color = if (index <= stage) Color.White else MarketColors.Grey400,
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (error == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp),
                                    color = MarketColors.SignalLine,
                                    strokeWidth = 3.dp,
                                )
                            }
                            Text(
                                text = error ?: status,
                                modifier = Modifier.weight(1f),
                                style = MarketType.body,
                                color = if (error == null) MarketColors.Grey200 else MarketColors.Rise,
                            )
                        }
                        if (error != null) {
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("다시 시도", style = MarketType.label, color = MarketColors.SignalLine)
                            }
                        }
                    }
                }
            }
        }
    }
}
