package com.amond.kmpbook.domain.model.instrument

import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/**
 * ETF의 통화 바스켓. 각 통화 수익률에서 상장통화 수익률을 빼므로 원화 상장과 USD 상장을
 * 같은 공식으로 처리하고, 포트폴리오 원화 환산과 중복되지 않는다.
 */
data class EtfFxProfile(
    val legs: List<CurrencyExposureLeg>,
    val annualHedgeCostRate: Double = 0.0,
) {
    init {
        require(legs.isNotEmpty()) { "ETF 통화 바스켓은 비어 있을 수 없습니다." }
        require(legs.map(CurrencyExposureLeg::currency).distinct().size == legs.size) {
            "ETF 통화 바스켓에 같은 통화를 두 번 넣을 수 없습니다."
        }
        require(legs.sumOf(CurrencyExposureLeg::grossNotional) in 0.0..3.0) {
            "ETF 통화 명목 노출 합계는 3 이하여야 합니다."
        }
        require(annualHedgeCostRate in 0.0..0.05) { "연 환헤지 비용률은 0% 이상 5% 이하여야 합니다." }
    }

    val isFullyHedged: Boolean
        get() = legs.any { it.currency != ReferenceCurrency.KRW } &&
            legs.filter { it.currency != ReferenceCurrency.KRW }
                .all { it.hedgeRatioToListingCurrency >= 0.95 }
}
