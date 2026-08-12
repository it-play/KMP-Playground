package com.amond.kmpbook.domain.simulation.price

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.model.venue.MarketSession
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class PriceGenerationInput(
    val stock: StockDefinition,
    val startTime: Instant,
    val previousPrice: Double,
    val dailyBasePrice: Double,
    val session: MarketSession,
    val macro: MacroEnvironment = MacroEnvironment(),
    val eventImpulse: PriceImpulse = PriceImpulse(),
    val averageDailyVolume: Long = defaultAverageDailyVolume(stock),
    val dayOpen: Double = previousPrice,
    val dayHigh: Double = max(previousPrice, dayOpen),
    val dayLow: Double = min(previousPrice, dayOpen),
    /** Optional stock-specific sensitivity to a percentage move in USD/KRW. */
    val fxSensitivity: Double = defaultFxSensitivity(stock),
    /**
     * Portion of this wall-clock hour covered by the regular session. Use 0.5
     * for KRX's 15:00-15:30 final half-hour and 1.0 for ordinary full hours.
     */
    val regularTradingFraction: Double = 1.0,
    /**
     * Fair-value clock visible to this bar. Normally the same as the regular fraction;
     * after an intrahour L1/L2 reopening it keeps running for the ordinary session even
     * though volume and idiosyncratic volatility use the reduced trading fraction.
     */
    val fairValueTradingFraction: Double? = null,
    /** 기초자산 시장이 이 시간에 실제로 거래된 비율. 해외 ETF의 개장 갭에 사용한다. */
    val referenceTradingFraction: Double? = null,
    /**
     * 현재 구성 바스켓을 평가한 이번 구간의 비용 전 gross 로그수익률이다.
     *
     * 값이 있으면 편입·편출과 가중치를 소유한 포트폴리오 계층이 지역·섹터·수급
     * 요인을 이미 합성했음을 뜻한다. 가격 엔진은 그 팩터를 다시 더하지 않고, FX·보수·상품
     * 자체 사건·시장가/NAV 괴리만 별도로 반영한다.
     */
    val basketGrossLogReturn: Double? = null,
    /**
     * 상품 운용 엔진이 보수·금융비용까지 반영해 계산한 좌당 공정가치 로그수익률이다.
     * 일일 reset, ETN 계약가치, CEF NAV처럼 상품 상태 자체가 수익률을 소유할 때 사용한다.
     */
    val productFairValueLogReturn: Double? = null,
    /** 구성종목의 현재 지시배당률을 비중으로 합성한 연율. */
    val basketAnnualIncomeYield: Double? = null,
    /** 상장시장 폐장 중 누적된 기초자산·환율 fair-value 로그수익률. 개장 시 한 번 적용한다. */
    val carriedReferenceLogReturn: Double = 0.0,
    /** 상장시장 폐장 중 누적된 상품 자체 사건의 가격 전용 로그수익률. */
    val carriedPriceDislocationLogReturn: Double = 0.0,
    /** 직전 시장가격/NAV(또는 ETN 지표가치)의 로그 괴리. 가격 계층의 평균회귀에만 사용한다. */
    val priceToReferenceLogGap: Double = 0.0,
    /** 이번 봉이 현지 거래일의 첫 정규장 봉인지를 런타임이 지정한다. */
    val isFirstRegularBarOfDay: Boolean = false,
) {
    init {
        require(previousPrice > 0.0 && previousPrice.isFinite()) {
            "Previous price must be positive and finite"
        }
        require(dailyBasePrice > 0.0 && dailyBasePrice.isFinite()) {
            "Daily base price must be positive and finite"
        }
        require(averageDailyVolume >= 0L) { "Average daily volume cannot be negative" }
        val priceBoundaryContext =
            "stock=${stock.id}, startTime=$startTime, previousPrice=$previousPrice, " +
                "dailyBasePrice=$dailyBasePrice, dayOpen=$dayOpen, dayHigh=$dayHigh, " +
                "dayLow=$dayLow, session=$session"
        require(dayOpen > 0.0 && dayHigh > 0.0 && dayLow > 0.0) {
            "Daily OHLC values must be positive: $priceBoundaryContext"
        }
        require(dayHigh >= max(previousPrice, dayOpen)) {
            "Daily high must include previous price and open: $priceBoundaryContext"
        }
        require(dayLow <= min(previousPrice, dayOpen)) {
            "Daily low must include previous price and open: $priceBoundaryContext"
        }
        require(fxSensitivity.isFinite())
        require(regularTradingFraction in 0.0..1.0) {
            "Regular trading fraction must be in [0, 1]"
        }
        require(fairValueTradingFraction == null || fairValueTradingFraction in 0.0..1.0) {
            "Fair-value trading fraction must be in [0, 1]"
        }
        require(referenceTradingFraction == null || referenceTradingFraction in 0.0..1.0) {
            "Reference trading fraction must be in [0, 1]"
        }
        require(basketGrossLogReturn == null || stock.isFundLike && basketGrossLogReturn.isFinite()) {
            "Basket gross return requires a fund-like instrument and a finite value"
        }
        require(
            productFairValueLogReturn == null ||
                stock.isFundLike && productFairValueLogReturn.isFinite(),
        ) { "Product fair-value return requires a fund-like instrument and a finite value" }
        require(basketGrossLogReturn == null || productFairValueLogReturn == null) {
            "A price interval cannot use both a gross benchmark basket and a net product fair value."
        }
        require(
            basketAnnualIncomeYield == null ||
                stock.isFundLike && basketAnnualIncomeYield.isFinite() &&
                basketAnnualIncomeYield in 0.0..1.0,
        ) { "Basket income yield requires a fund-like instrument and a value in [0, 1]" }
        require(carriedReferenceLogReturn.isFinite())
        require(carriedPriceDislocationLogReturn.isFinite())
        require(priceToReferenceLogGap.isFinite())
    }

    companion object {
        fun defaultAverageDailyVolume(stock: StockDefinition): Long =
            (stock.sharesOutstanding.toDouble() * DEFAULT_DAILY_TURNOVER)
                .coerceIn(1_000.0, Long.MAX_VALUE.toDouble() / 4.0)
                .toLong()

        fun defaultFxSensitivity(stock: StockDefinition): Double = when {
            stock.etfProfile != null -> 0.0
            stock.market.isUnitedStates -> -0.10
            stock.sector in EXPORT_HEAVY_SECTORS -> 0.25
            stock.sector in IMPORT_HEAVY_SECTORS -> -0.15
            else -> 0.05
        }

        private val EXPORT_HEAVY_SECTORS = setOf(
            Sector.SEMICONDUCTOR,
            Sector.AUTOMOTIVE,
            Sector.AEROSPACE_DEFENSE,
            Sector.INFORMATION_TECHNOLOGY,
            Sector.ENTERTAINMENT,
            Sector.INDUSTRIALS,
        )
        private val IMPORT_HEAVY_SECTORS = setOf(
            Sector.ENERGY,
            Sector.UTILITIES,
            Sector.TRANSPORTATION_LOGISTICS,
            Sector.RETAIL_ECOMMERCE,
        )
        private const val DEFAULT_DAILY_TURNOVER: Double = 0.004
    }
}
