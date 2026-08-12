package com.amond.kmpbook.domain.model.fund

/** Numeric IO/prepayment calibration, kept separate from the disclosed legal investment strategy. */
data class MbsInterestOnlyModelParameters(
    val effectiveDurationYears: Double,
    val baseConditionalPrepaymentRateAnnual: Double,
    val cprIncreasePerOnePercentMortgageRateDecline: Double,
    val annualConditionalPrepaymentRateVolatility: Double,
    val couponStripYieldAnnual: Double,
    val origin: MbsInterestOnlyModelParameterOrigin,
    val sourceUrl: String?,
    val calibrationId: String?,
) {
    init {
        require(effectiveDurationYears.isFinite() && effectiveDurationYears in -50.0..<0.0)
        require(baseConditionalPrepaymentRateAnnual.isUnitRate())
        require(cprIncreasePerOnePercentMortgageRateDecline.isUnitRate())
        require(annualConditionalPrepaymentRateVolatility.isUnitRate())
        require(couponStripYieldAnnual.isUnitRate())
        when (origin) {
            MbsInterestOnlyModelParameterOrigin.OFFICIAL_DISCLOSURE -> {
                requireValidHttpsUrl(requireNotNull(sourceUrl))
                require(calibrationId == null)
            }
            MbsInterestOnlyModelParameterOrigin.CALIBRATED_ASSUMPTION -> {
                require(sourceUrl == null)
                requireNotNull(calibrationId)
                require(ASSUMPTION_ID_PATTERN.matches(calibrationId))
            }
        }
    }

    private fun Double.isUnitRate(): Boolean = isFinite() && this in 0.0..1.0

    private fun requireValidHttpsUrl(value: String) {
        require(value.length in 9..2_048 && value.startsWith("https://") && value.none(Char::isISOControl))
    }

    companion object {
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
