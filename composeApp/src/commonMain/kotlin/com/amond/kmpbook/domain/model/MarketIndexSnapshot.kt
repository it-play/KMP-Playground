package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 한 시간 계산 후의 지수 스냅샷. [sessionDate]는 폐장 시간에는 바뀌지 않아
 * 다음 미국 정규장에서 previousClose와 일간 OHLC를 올바르게 리셋할 수 있다.
 */
data class MarketIndexSnapshot(
    val id: MarketIndexId,
    val timestamp: Instant,
    val value: Double,
    val previousClose: Double,
    val open: Double = value,
    val high: Double = value,
    val low: Double = value,
    val constituentCount: Int = 0,
    val sessionDate: LocalDate? = null,
    val isSimulationProxy: Boolean = true,
) {
    init {
        require(value > 0.0 && value.isFinite()) { "지수 값은 유한한 양수여야 합니다." }
        require(previousClose > 0.0 && previousClose.isFinite()) { "전일 종가는 유한한 양수여야 합니다." }
        require(open > 0.0 && high > 0.0 && low > 0.0) { "지수 OHLC는 양수여야 합니다." }
        require(open.isFinite() && high.isFinite() && low.isFinite()) { "지수 OHLC는 유한해야 합니다." }
        require(high >= maxOf(open, value, low)) { "지수 고가는 시가·종가·저가 이상이어야 합니다." }
        require(low <= minOf(open, value, high)) { "지수 저가는 시가·종가·고가 이하이어야 합니다." }
        require(constituentCount >= 0) { "지수 편입 종목 수는 음수일 수 없습니다." }
        require(isSimulationProxy) { "현재 스냅샷은 공식 상용 지수가 아닙니다." }
    }

    val change: Double get() = value - previousClose
    val changeRate: Double get() = change / previousClose
}
