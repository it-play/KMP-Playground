package com.amond.kmpbook.domain.model.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

/** 원화 환산까지 끝난 포트폴리오 시계열 한 점. */
data class PortfolioSnapshot(
    val timestamp: Instant,
    val cashByCurrency: Map<Currency, Double>,
    val holdings: List<Holding>,
    /** 통화 1단위당 원화 가격. KRW는 생략해도 항상 1.0으로 처리한다. */
    val exchangeRatesToKrw: Map<Currency, Double> = emptyMap(),
    val initialCapitalKrw: Double = 0.0,
    val realizedProfitKrw: Double = 0.0,
    val cumulativeCommissionKrw: Double = 0.0,
    val cumulativeTaxKrw: Double = 0.0,
    /** 취득일 환율과 매수 직접비용을 보존한 종목별 잔여 FIFO 원화원가. */
    val holdingCostBasisKrw: Map<String, Double>,
) {
    init {
        require(cashByCurrency.values.all { it >= 0.0 }) { "현금 잔액은 음수일 수 없습니다." }
        require(exchangeRatesToKrw.values.all { it > 0.0 }) { "환율은 0보다 커야 합니다." }
        require(initialCapitalKrw >= 0.0) { "초기 자산은 음수일 수 없습니다." }
        require(cumulativeCommissionKrw >= 0.0 && cumulativeTaxKrw >= 0.0) {
            "누적 수수료와 세금은 음수일 수 없습니다."
        }
        require(holdingCostBasisKrw.values.all { it >= 0.0 && it.isFinite() })
        val usedForeignCurrencies = (cashByCurrency.keys + holdings.map { it.currency }) - Currency.KRW
        require(usedForeignCurrencies.all(exchangeRatesToKrw::containsKey)) {
            "보유 중인 외화의 원화 환율이 모두 필요합니다."
        }
    }

    fun rateToKrw(currency: Currency): Double =
        if (currency == Currency.KRW) 1.0 else requireNotNull(exchangeRatesToKrw[currency]) {
            "${currency.name} 환율이 없습니다."
        }

    fun holdingMarketValueKrw(holding: Holding): Double =
        holding.marketValue * rateToKrw(holding.currency)

    fun holdingCostBasisKrw(holding: Holding): Double =
        holdingCostBasisKrw.getValue(holding.stockId)

    fun holdingUnrealizedProfitKrw(holding: Holding): Double =
        holdingMarketValueKrw(holding) - holdingCostBasisKrw(holding)

    fun holdingReturnRateKrw(holding: Holding): Double {
        val cost = holdingCostBasisKrw(holding)
        return if (cost == 0.0) 0.0 else holdingUnrealizedProfitKrw(holding) / cost
    }

    val cashValueKrw: Double
        get() = cashByCurrency.entries.sumOf { (currency, amount) -> amount * rateToKrw(currency) }

    val stockValueKrw: Double
        get() = holdings.sumOf(::holdingMarketValueKrw)

    val totalAssetValueKrw: Double get() = cashValueKrw + stockValueKrw

    val unrealizedProfitKrw: Double
        get() = holdings.sumOf(::holdingUnrealizedProfitKrw)

    val totalProfitKrw: Double get() = totalAssetValueKrw - initialCapitalKrw
    val totalReturnRate: Double get() = if (initialCapitalKrw == 0.0) 0.0 else totalProfitKrw / initialCapitalKrw
    val cashWeight: Double get() = if (totalAssetValueKrw == 0.0) 0.0 else cashValueKrw / totalAssetValueKrw
    val stockWeight: Double get() = if (totalAssetValueKrw == 0.0) 0.0 else stockValueKrw / totalAssetValueKrw
}
