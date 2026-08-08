package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/**
 * ETF 가격·세금 시뮬레이션에 필요한 정적 상품 정보다.
 *
 * 미래 과표기준가격은 실재할 수 없으므로 [taxablePriceGainRatio]는 국내상장 기타 ETF의
 * 게임 과표기준가격 증가분을 산정하는 정책값이다. 실제 원천징수는 증권사가 제공하는
 * 매수·매도 시점 과표기준가격을 사용해야 한다.
 */
data class EtfProfile(
    val benchmark: String,
    val assetClass: EtfAssetClass,
    val taxCategory: EtfTaxCategory,
    val annualExpenseRatio: Double,
    /** 상장통화 대비 기초자산의 구조화된 다중통화 환노출. */
    val fxProfile: EtfFxProfile,
    val leverage: Double = 1.0,
    val taxablePriceGainRatio: Double = 1.0,
    val exposureRegion: EtfExposureRegion = EtfExposureRegion.KOREA,
) {
    init {
        require(benchmark.isNotBlank()) { "ETF 기초지수·전략은 비어 있을 수 없습니다." }
        require(annualExpenseRatio in 0.0..0.05) { "ETF 연 보수는 0% 이상 5% 이하여야 합니다." }
        require(leverage in -3.0..3.0 && leverage != 0.0) { "ETF 배율은 -3배 이상 3배 이하의 0이 아닌 값이어야 합니다." }
        require(taxablePriceGainRatio in 0.0..1.0) { "ETF 게임 과표 반영률은 0 이상 1 이하여야 합니다." }
    }

    fun isExposedTo(market: Market): Boolean = when (exposureRegion) {
        EtfExposureRegion.KOREA -> market.isKorean
        EtfExposureRegion.UNITED_STATES -> market.isUnitedStates
        EtfExposureRegion.GLOBAL -> true
        EtfExposureRegion.DEVELOPED_EX_US,
        EtfExposureRegion.EMERGING_MARKETS,
        -> false // 별도 지역 이벤트가 추가되기 전에는 글로벌 이벤트만 직접 적용한다.
    }
}
