package com.amond.kmpbook.presentation.portfolio

/** Market-cap proxy and rank projection used by KRX daily surveillance. */
data class KrxDailySurveillanceResult(
    val marketProxyByStockId: Map<String, Double>,
    val marketCapRankByStockId: Map<String, Int>,
)
