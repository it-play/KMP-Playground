package com.amond.kmpbook

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment
import com.amond.kmpbook.ui.theme.MarketDesignSystem
import com.amond.kmpbook.ui.theme.MarketLayout
import java.awt.Dimension
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.market_ledger_icon
import org.jetbrains.compose.resources.painterResource

fun main() = application {
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
    ) {
        window.minimumSize = Dimension(
            MarketLayout.minimumWindowWidthPx,
            MarketLayout.minimumWindowHeightPx,
        )
        App()
    }
}
