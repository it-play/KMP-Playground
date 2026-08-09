package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.index.MarketIndexId
import com.amond.kmpbook.domain.model.index.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.pricing.PriceBar
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
        val stockIds = stocks.mapTo(linkedSetOf(), StockDefinition::id)
        require(stockIds.size == stocks.size) { "지수 구성종목 ID는 중복될 수 없습니다." }
        require(barsByStockId.keys.all(stockIds::contains)) {
            "시간 봉에는 지수 계산 대상이 아닌 종목을 포함할 수 없습니다."
        }
        require(previousCloseByStockId.keys.all(stockIds::contains)) {
            "직전 종가에는 지수 계산 대상이 아닌 종목을 포함할 수 없습니다."
        }
        require(usTradingFraction in 0.0..1.0) { "미국 정규장 비율은 0에서 1 사이여야 합니다." }
        require(previousIndices.all { (id, snapshot) -> id == snapshot.id }) {
            "이전 지수 맵의 키와 스냅샷 ID가 일치해야 합니다."
        }
        require(previousCloseByStockId.values.all { it > 0.0 && it.isFinite() }) {
            "구성종목의 직전 종가는 유한한 양수여야 합니다."
        }
    }
}
