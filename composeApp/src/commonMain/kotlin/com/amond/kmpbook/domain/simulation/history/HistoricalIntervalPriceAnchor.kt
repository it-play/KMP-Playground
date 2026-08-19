package com.amond.kmpbook.domain.simulation.history

import kotlinx.datetime.LocalDate

/**
 * 실제 일봉을 한 게임 시간 구간으로 결정론적으로 분해한 가격 기준점이다.
 *
 * 기준 OHLCV 위에 플레이어 체결의 제한된 편차가 이미 합성되어 있으므로 가격 엔진은
 * 이 값을 다시 확률화하지 않는다.
 */
data class HistoricalIntervalPriceAnchor(
    val tradingDate: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val playerDeviationAtOpen: Double = 0.0,
    val playerDeviationAtClose: Double = 0.0,
) {
    init {
        require(listOf(open, high, low, close).all { it.isFinite() && it > 0.0 }) {
            "역사 구간 OHLC는 유한한 양수여야 합니다."
        }
        require(low <= minOf(open, close) && high >= maxOf(open, close)) {
            "역사 구간 OHLC 범위가 모순됩니다."
        }
        require(volume >= 0L) { "역사 구간 거래량은 음수일 수 없습니다." }
        require(playerDeviationAtOpen.isFinite() && playerDeviationAtClose.isFinite()) {
            "플레이어 가격 편차는 유한해야 합니다."
        }
    }
}
