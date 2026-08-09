package com.amond.kmpbook

import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
    val windowState = rememberWindowState(
        width = MarketLayout.defaultWindowWidth,
        height = MarketLayout.defaultWindowHeight,
        position = WindowPosition(Alignment.Center),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "${MarketDesignSystem.NAME} · Stock Simulator",
        icon = painterResource(Res.drawable.market_ledger_icon),
        state = windowState,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                escapeRequest += 1
                true
            } else {
                false
            }
        },
    ) {
        window.minimumSize = Dimension(
            MarketLayout.minimumWindowWidthPx,
            MarketLayout.minimumWindowHeightPx,
        )
        App(onExitRequest = ::exitApplication, escapeRequest = escapeRequest)
    }
}
