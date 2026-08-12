package com.amond.kmpbook.domain.model.reference

/** One independently rolled futures exposure inside a shared reference portfolio. */
class FuturesSleeveTerms(
    val sleeveId: String,
    val curveId: String,
    val assetClass: CommodityAssetClass,
    val targetWeight: Double,
    val notionalExposureRatio: Double,
    val rollCalendar: FuturesRollCalendar,
    eligibleDeliveryMonths: Set<Int>,
    val rollStartTradingDaysBeforeExpiry: Int,
    val rollWindowTradingDays: Int,
    val priceReturnConvention: FuturesPriceReturnConvention,
    val fixedPriceReturnNotional: Double?,
) {
    val eligibleDeliveryMonths: Set<Int> = eligibleDeliveryMonths.toList().sorted().toSet()

    init {
        require(ID_PATTERN.matches(sleeveId))
        require(ID_PATTERN.matches(curveId))
        require(targetWeight.isFinite() && targetWeight in MIN_POSITIVE_WEIGHT..1.0)
        require(notionalExposureRatio.isFinite() && notionalExposureRatio in 0.0..MAX_EXPOSURE)
        require(this.eligibleDeliveryMonths.isNotEmpty())
        require(this.eligibleDeliveryMonths.all { it in 1..12 })
        require(rollStartTradingDaysBeforeExpiry in 1..MAX_ROLL_START_DAYS)
        require(rollWindowTradingDays in 1..rollStartTradingDaysBeforeExpiry)
        when (priceReturnConvention) {
            FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO -> require(fixedPriceReturnNotional == null)
            FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL -> {
                requireNotNull(fixedPriceReturnNotional)
                require(
                    fixedPriceReturnNotional.isFinite() &&
                        fixedPriceReturnNotional in MIN_RETURN_NOTIONAL..MAX_RETURN_NOTIONAL,
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FuturesSleeveTerms &&
            sleeveId == other.sleeveId &&
            curveId == other.curveId &&
            assetClass == other.assetClass &&
            targetWeight == other.targetWeight &&
            notionalExposureRatio == other.notionalExposureRatio &&
            rollCalendar == other.rollCalendar &&
            eligibleDeliveryMonths == other.eligibleDeliveryMonths &&
            rollStartTradingDaysBeforeExpiry == other.rollStartTradingDaysBeforeExpiry &&
            rollWindowTradingDays == other.rollWindowTradingDays &&
            priceReturnConvention == other.priceReturnConvention &&
            fixedPriceReturnNotional == other.fixedPriceReturnNotional

    override fun hashCode(): Int {
        var result = sleeveId.hashCode()
        result = 31 * result + curveId.hashCode()
        result = 31 * result + assetClass.hashCode()
        result = 31 * result + targetWeight.hashCode()
        result = 31 * result + notionalExposureRatio.hashCode()
        result = 31 * result + rollCalendar.hashCode()
        result = 31 * result + eligibleDeliveryMonths.hashCode()
        result = 31 * result + rollStartTradingDaysBeforeExpiry
        result = 31 * result + rollWindowTradingDays
        result = 31 * result + priceReturnConvention.hashCode()
        result = 31 * result + (fixedPriceReturnNotional?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "FuturesSleeveTerms(sleeveId=$sleeveId, curveId=$curveId, assetClass=$assetClass, " +
            "targetWeight=$targetWeight, notionalExposureRatio=$notionalExposureRatio, " +
            "rollCalendar=$rollCalendar, eligibleDeliveryMonths=$eligibleDeliveryMonths, " +
            "rollStartTradingDaysBeforeExpiry=$rollStartTradingDaysBeforeExpiry, " +
            "rollWindowTradingDays=$rollWindowTradingDays, " +
            "priceReturnConvention=$priceReturnConvention, " +
            "fixedPriceReturnNotional=$fixedPriceReturnNotional)"

    companion object {
        private const val MIN_POSITIVE_WEIGHT: Double = 1e-9
        private const val MAX_EXPOSURE: Double = 5.0
        private const val MAX_ROLL_START_DAYS: Int = 90
        private const val MIN_RETURN_NOTIONAL: Double = 1e-9
        private const val MAX_RETURN_NOTIONAL: Double = 1e12
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
