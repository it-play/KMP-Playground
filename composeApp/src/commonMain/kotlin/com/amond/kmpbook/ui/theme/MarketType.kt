package com.amond.kmpbook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle

/** Typography accessors backed by the active desktop-installed or bundled font family. */
object MarketType {
    val display: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.display

    val headingLarge: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.headingLarge

    val heading: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.heading

    val body: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.body

    val label: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.label

    val caption: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.caption

    val number: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.number

    val numberLarge: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMarketTypography.current.numberLarge
}
