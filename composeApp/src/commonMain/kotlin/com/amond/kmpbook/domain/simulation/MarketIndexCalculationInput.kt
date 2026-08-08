package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexCatalog
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.StockDefinition
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Instant

/**
 * [MarketIndexEngine] 한 시간 계산에 필요한 모든 입력.
 *
 * [barsByStockId]는 `StockDefinition.id`를 키로 하는 해당 시간의 봉이다.
 * [previousCloseByStockId]는 같은 구성종목의 직전 종가로, 시가 갭을 지수에 이어
 * 붙이는 기준이다. 특정 종목의 입력 값이 없을 때만 해당 봉의
 * 시가를 기준으로 사용해 갭이 없는 봉으로 처리한다.
 * [usTradingFraction]은 미국 정규장이 해당 벽시간에 차지하는 비율로, 폐장 0·일반 1을
 * 사용한다. 입력이 동일하면 결과도 항상 동일하다.
 */
data class MarketIndexCalculationInput(
    val timestamp: Instant,
    val stocks: List<StockDefinition>,
    val barsByStockId: Map<String, PriceBar>,
    val previousCloseByStockId: Map<String, Double> = emptyMap(),
    val previousIndices: Map<MarketIndexId, MarketIndexSnapshot> = emptyMap(),
    val macro: MacroEnvironment = MacroEnvironment(),
    val usTradingFraction: Double,
) {
    init {
        require(usTradingFraction in 0.0..1.0) { "미국 정규장 비율은 0에서 1 사이여야 합니다." }
        require(previousIndices.all { (id, snapshot) -> id == snapshot.id }) {
            "이전 지수 맵의 키와 스냅샷 ID가 일치해야 합니다."
        }
        require(previousCloseByStockId.values.all { it > 0.0 && it.isFinite() }) {
            "구성종목의 직전 종가는 유한한 양수여야 합니다."
        }
    }
}
