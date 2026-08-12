package com.amond.kmpbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.amond.kmpbook.domain.data.DesktopInstrumentPackParser
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.data.InstrumentPack
import com.amond.kmpbook.ui.theme.MarketDesignSystem
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketSimulatorTheme
import java.awt.Dimension
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.market_ledger_icon
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource

private const val BASE_INSTRUMENT_CATALOG_PATH: String = "files/instruments/base-catalog.json"
private const val BASE_INSTRUMENT_SOURCE_ID: String = "builtin:base"

private fun loadBaseInstrumentCatalog(): Result<InstrumentCatalogSnapshot> = runCatching {
    val bytes = runBlocking { Res.readBytes(BASE_INSTRUMENT_CATALOG_PATH) }
    val pack = DesktopInstrumentPackParser.parse(
        bytes = bytes,
        sourceId = BASE_INSTRUMENT_SOURCE_ID,
        maxInstruments = InstrumentPack.MAX_INSTRUMENTS,
    )
    InstrumentCatalogSnapshot.fromPacks(listOf(pack))
}

fun main() {
    val baseCatalogResult = loadBaseInstrumentCatalog()
    application {
        var escapeRequest by remember { mutableIntStateOf(0) }
        var debugConsoleToggleRequest by remember { mutableIntStateOf(0) }
        var isDebugConsoleAvailable by remember { mutableStateOf(false) }
        var isGravePressed by remember { mutableStateOf(false) }
        var isEscapePressed by remember { mutableStateOf(false) }
        var isExitBlocked by remember { mutableStateOf(false) }
        val windowState = rememberWindowState(
            width = MarketLayout.defaultWindowWidth,
            height = MarketLayout.defaultWindowHeight,
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = { if (!isExitBlocked) exitApplication() },
            title = "${MarketDesignSystem.NAME} · Stock Simulator",
            icon = painterResource(Res.drawable.market_ledger_icon),
            state = windowState,
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
            window.minimumSize = Dimension(
                MarketLayout.minimumWindowWidthPx,
                MarketLayout.minimumWindowHeightPx,
            )
            val baseCatalog = baseCatalogResult.getOrNull()
            if (baseCatalog == null) {
                MarketSimulatorTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(MarketLayout.screenPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "기본 종목 카탈로그를 불러오지 못했습니다. 앱을 다시 설치해 주세요.\n" +
                                    (baseCatalogResult.exceptionOrNull()?.message ?: "알 수 없는 오류"),
                            )
                        }
                    }
                }
            } else {
                App(
                    baseInstrumentCatalog = baseCatalog,
                    onExitRequest = ::exitApplication,
                    onExitBlockedChanged = { blocked -> isExitBlocked = blocked },
                    escapeRequest = escapeRequest,
                    debugConsoleToggleRequest = debugConsoleToggleRequest,
                    onDebugConsoleAvailabilityChanged = { available ->
                        isDebugConsoleAvailable = available
                        if (!available) isGravePressed = false
                    },
                )
            }
        }
    }
}
