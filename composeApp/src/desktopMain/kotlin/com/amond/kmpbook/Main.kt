package com.amond.kmpbook

import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.amond.kmpbook.ui.theme.MarketDesignSystem
import com.amond.kmpbook.ui.theme.MarketLayout
import java.awt.Dimension
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.market_ledger_icon
import org.jetbrains.compose.resources.painterResource

fun main() = application {
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
        App(
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
