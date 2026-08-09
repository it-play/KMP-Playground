package com.amond.kmpbook.ui.theme

import androidx.compose.ui.unit.dp

/** Desktop shell measurements. Screens consume these instead of redefining the application frame. */
object MarketLayout {
    val defaultWindowWidth = 1_800.dp
    val defaultWindowHeight = 1_080.dp
    const val minimumWindowWidthPx: Int = 1_720
    const val minimumWindowHeightPx: Int = 980

    val sidebarWidth = 208.dp
    val marketPulseRailHeight = 96.dp
    val marketExplorerWidth = 272.dp
    val marketOrderBookWidth = 260.dp
    val marketOrderTicketWidth = 348.dp
    val newsGroupRailWidth = 208.dp
    val newsStoryListWidth = 400.dp
    val detailRailWidth = 360.dp
    val settingsRailWidth = 420.dp
    val screenPadding = MarketSpacing.sm
    val screenGap = MarketSpacing.sm
    val panelPadding = MarketSpacing.md
}
