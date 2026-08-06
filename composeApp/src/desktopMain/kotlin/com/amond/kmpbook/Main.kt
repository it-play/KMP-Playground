package com.amond.kmpbook

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import java.awt.Dimension

fun main() = application {
    val windowState = rememberWindowState(
        width = 1_500.dp,
        height = 940.dp,
        position = WindowPosition(Alignment.Center),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Market Ledger 2040 · Stock Simulator",
        state = windowState,
    ) {
        window.minimumSize = Dimension(1_180, 720)
        App()
    }
}
