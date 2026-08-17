package com.amond.kmpbook.ui.charts.trading

import androidx.compose.ui.Modifier

internal expect fun isNativeChartRuntimeAvailable(): Boolean

internal expect fun nativeChartDataDirectory(): String?

internal expect fun Modifier.consumeNativeChartOverlayPointerEvents(): Modifier
