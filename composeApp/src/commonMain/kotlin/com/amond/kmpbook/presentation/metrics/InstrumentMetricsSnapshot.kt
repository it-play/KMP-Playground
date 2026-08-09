package com.amond.kmpbook.presentation.metrics

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

/** 원시 상태와 현재 시세로부터 매번 다시 계산되는 종목 지표의 공통 계약이다. */
sealed interface InstrumentMetricsSnapshot {
    val stockId: String
    val asOf: Instant
    val currency: Currency
    val marketCapitalization: Double
}
