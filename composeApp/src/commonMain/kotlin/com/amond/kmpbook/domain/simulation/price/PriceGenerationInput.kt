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
        require(dayOpen > 0.0 && dayHigh > 0.0 && dayLow > 0.0)
        require(dayHigh >= max(previousPrice, dayOpen))
        require(dayLow <= min(previousPrice, dayOpen))
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
