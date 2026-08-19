package com.amond.kmpbook.domain.model.history

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.LocalDate
import kotlin.math.max
import kotlin.math.min

/** 특정 상장 종목의 시장 현지 거래일 기준 역사 일봉이다. */
data class HistoricalDailyBar(
    val instrumentId: String,
    val tradingDate: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val adjustedClose: Double? = null,
    /** 게임 시작 전에 끝난 분할만 반영해 과거 차트를 시작시점 주식 단위로 잇는 가격 계수다. */
    val pregameSplitAdjustedPriceFactor: Double = 1.0,
    val volume: Long,
    val priceBasis: HistoricalPriceBasis,
    val sourceId: String,
) {
    val market: Market = instrumentId.substringBefore(':').let { marketName ->
        requireNotNull(Market.entries.find { it.name == marketName }) {
            "역사 일봉 종목 ID의 시장을 알 수 없습니다: $instrumentId"
        }
    }

    init {
        require(INSTRUMENT_ID_PATTERN.matches(instrumentId)) {
            "역사 일봉 종목 ID 형식이 올바르지 않습니다: $instrumentId"
        }
        require(listOf(open, high, low, close).all { it.isFinite() && it > 0.0 }) {
            "역사 일봉 OHLC는 유한한 양수여야 합니다: $instrumentId/$tradingDate"
        }
        require(low <= min(open, close) && high >= max(open, close) && low <= high) {
            "역사 일봉 OHLC 범위가 모순됩니다: $instrumentId/$tradingDate"
        }
        require(adjustedClose == null || adjustedClose.isFinite() && adjustedClose > 0.0) {
            "역사 일봉 조정종가는 유한한 양수여야 합니다."
        }
        require(
            pregameSplitAdjustedPriceFactor.isFinite() &&
                pregameSplitAdjustedPriceFactor > 0.0,
        ) { "게임 시작 전 분할 조정 가격 계수는 유한한 양수여야 합니다." }
        require(volume >= 0L) { "역사 일봉 거래량은 음수일 수 없습니다." }
        require(sourceId.isNotBlank() && sourceId == sourceId.trim()) {
            "역사 일봉 출처 ID는 비어 있거나 앞뒤 공백을 가질 수 없습니다."
        }
    }

    companion object {
        private val INSTRUMENT_ID_PATTERN: Regex = Regex("[A-Z_]+:[^\\s:]+")
    }
}
