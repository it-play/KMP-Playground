package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Market

/**
 * KRX 일별 시장감시가 사용하는 업종/시장 시가총액 프록시와 상위 100위 분류를 계산한다.
 * 런타임 생성과 저장 검증이 같은 정렬·합산 순서를 사용해야 부동소수점 계보가 흔들리지 않는다.
 */
object KrxDailySurveillanceProjection {
    data class Result(
        val marketProxyByStockId: Map<String, Double>,
        val marketCapRankByStockId: Map<String, Int>,
    )

    fun project(
        stocks: Collection<StockDefinition>,
        closeByStockId: Map<String, Double>,
        indexEligibleStockIds: Set<String>,
        top100MarketCapProxyKrw: Double,
    ): Result {
        val orderedStocks = stocks.asSequence()
            .filter { stock -> stock.market.isKorean }
            .sortedBy(StockDefinition::id)
            .toList()
        val eligibleMarketCaps = orderedStocks.asSequence()
            .filter { stock -> stock.hasCorporateEarnings && stock.id in indexEligibleStockIds }
            .mapNotNull { stock ->
                closeByStockId[stock.id]
                    ?.takeIf { close -> close.isFinite() && close > 0.0 }
                    ?.let { close -> stock to stock.sharesOutstanding.toDouble() * close }
            }
            .toList()
        val marketTotals = eligibleMarketCaps.groupBy { (stock) -> stock.market }
            .mapValues { (_, values) -> values.sumOf { (_, cap) -> cap } }
        val sectorTotals = eligibleMarketCaps.groupBy { (stock) -> stock.market to stock.sector }
            .mapValues { (_, values) ->
                values.takeIf { it.size >= 2 }?.sumOf { (_, cap) -> cap }
            }

        val proxyByStockId = buildMap {
            orderedStocks.forEach { stock ->
                val marketProxy = marketTotals[stock.market] ?: return@forEach
                val proxy = if (stock.market == Market.KOSPI) {
                    sectorTotals[stock.market to stock.sector] ?: marketProxy
                } else {
                    marketProxy
                }
                put(stock.id, proxy)
            }
        }

        val orderedCaps = eligibleMarketCaps.sortedWith(
            compareByDescending<Pair<StockDefinition, Double>> { (_, cap) -> cap }
                .thenBy { (stock) -> stock.id },
        )
        val top100Proxy = orderedCaps.filter { (_, cap) -> cap >= top100MarketCapProxyKrw }
        val outsideTop100Proxy = orderedCaps.filter { (_, cap) -> cap < top100MarketCapProxyKrw }
        val rankByStockId = buildMap {
            top100Proxy.forEachIndexed { index, (stock, _) -> put(stock.id, index + 1) }
            outsideTop100Proxy.forEachIndexed { index, (stock, _) -> put(stock.id, 101 + index) }
        }
        return Result(proxyByStockId, rankByStockId)
    }
}
