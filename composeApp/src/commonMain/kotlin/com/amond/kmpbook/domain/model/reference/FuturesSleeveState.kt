package com.amond.kmpbook.domain.model.reference

import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** Current portfolio weight and front/deferred contract mix for one independently rolled sleeve. */
data class FuturesSleeveState(
    val sleeveId: String,
    val curveId: String,
    val assetClass: CommodityAssetClass,
    val rollCalendar: FuturesRollCalendar,
    val priceReturnConvention: FuturesPriceReturnConvention,
    val fixedPriceReturnNotional: Double?,
    val currentWeight: Double,
    val targetWeight: Double,
    val currentSpotLevel: Double,
    val frontContractId: String,
    val frontExpiryDate: LocalDate,
    val frontPrice: Double,
    val frontContractWeight: Double,
    val nextContractId: String,
    val nextExpiryDate: LocalDate,
    val nextPrice: Double,
    val nextContractWeight: Double,
    val lastRollTradingDate: LocalDate?,
) {
    init {
        require(ID_PATTERN.matches(sleeveId) && ID_PATTERN.matches(curveId))
        require(ID_PATTERN.matches(frontContractId) && ID_PATTERN.matches(nextContractId))
        require(frontContractId != nextContractId && frontExpiryDate < nextExpiryDate)
        require(currentWeight.isFinite() && currentWeight in 0.0..1.0)
        require(targetWeight.isFinite() && targetWeight in 0.0..1.0)
        require(currentSpotLevel.isFinite() && currentSpotLevel in MIN_LEVEL..MAX_LEVEL)
        require(frontPrice.isFinite() && frontPrice in MIN_PRICE..MAX_PRICE)
        require(nextPrice.isFinite() && nextPrice in MIN_PRICE..MAX_PRICE)
        require(frontContractWeight.isFinite() && frontContractWeight in 0.0..1.0)
        require(nextContractWeight.isFinite() && nextContractWeight in 0.0..1.0)
        require(abs(frontContractWeight + nextContractWeight - 1.0) <= WEIGHT_EPSILON)
        require(frontContractWeight > 0.0) { "완료된 roll은 deferred contract를 즉시 front로 승격해야 합니다." }
        when (priceReturnConvention) {
            FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO -> {
                require(frontPrice > 0.0 && nextPrice > 0.0)
                require(fixedPriceReturnNotional == null)
            }
            FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL -> {
                requireNotNull(fixedPriceReturnNotional)
                require(fixedPriceReturnNotional.isFinite() && fixedPriceReturnNotional > 0.0)
            }
        }
        require(lastRollTradingDate == null || rollCalendar.isTradingDate(lastRollTradingDate))
    }

    companion object {
        private const val WEIGHT_EPSILON: Double = 1e-10
        private const val MIN_LEVEL: Double = 1e-12
        private const val MAX_LEVEL: Double = 1e24
        private const val MIN_PRICE: Double = -1e12
        private const val MAX_PRICE: Double = 1e12
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
