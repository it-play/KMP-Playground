package com.amond.kmpbook.ui.charts.trading

import androidx.compose.ui.Modifier

internal data class NativeChartRuntimeConfiguration(
    val isAvailable: Boolean,
    val dataDirectory: String?,
)

internal expect suspend fun prepareNativeChartRuntime(): NativeChartRuntimeConfiguration

internal expect fun Modifier.consumeNativeChartOverlayPointerEvents(): Modifier
