package com.amond.kmpbook.domain.model.fundproduct

/** Explicit execution and valuation assumptions; no product value is inferred from its symbol. */
data class OptionPremiumModelParameters(
    val impliedVolatilityMultiplier: Double,
    val soldPremiumCaptureRatio: Double,
    val purchasedPremiumCostRatio: Double,
    val implementationCostRatePerRoll: Double,
    val origin: OptionPremiumModelParameterOrigin,
    val sourceUrl: String?,
    val calibrationId: String?,
) {
    init {
        require(impliedVolatilityMultiplier.isFinite() && impliedVolatilityMultiplier in 0.25..4.0)
        require(soldPremiumCaptureRatio.isFinite() && soldPremiumCaptureRatio in 0.0..1.5)
        require(purchasedPremiumCostRatio.isFinite() && purchasedPremiumCostRatio in 0.5..2.0)
        require(implementationCostRatePerRoll.isFinite() && implementationCostRatePerRoll in 0.0..0.10)
        when (origin) {
            OptionPremiumModelParameterOrigin.VERIFIED_DISCLOSURE -> {
                requireValidHttpsUrl(sourceUrl)
                require(calibrationId == null)
            }
            OptionPremiumModelParameterOrigin.CALIBRATED_ASSUMPTION -> {
                require(sourceUrl == null)
                requireNotNull(calibrationId)
                require(CALIBRATION_ID.matches(calibrationId))
            }
        }
    }

    private fun requireValidHttpsUrl(value: String?) {
        requireNotNull(value)
        require(value.startsWith("https://") && value.length <= MAX_URL_LENGTH)
        require(value.length > "https://".length && value.none(Char::isISOControl))
    }

    companion object {
        private const val MAX_URL_LENGTH: Int = 2_048
        private val CALIBRATION_ID = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
