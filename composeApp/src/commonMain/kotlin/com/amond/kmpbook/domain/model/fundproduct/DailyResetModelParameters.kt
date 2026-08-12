package com.amond.kmpbook.domain.model.fundproduct

/** 공식 목표배율과 분리된 게임 내 자금조달·담보수익 가정이다. */
data class DailyResetModelParameters(
    val annualFinancingSpread: Double,
    val collateralYieldParticipation: Double,
    val origin: DailyResetModelParameterOrigin,
    val sourceUrl: String?,
) {
    init {
        require(annualFinancingSpread.isFinite() && annualFinancingSpread in 0.0..1.0)
        require(collateralYieldParticipation.isFinite() && collateralYieldParticipation in 0.0..1.5)
        when (origin) {
            DailyResetModelParameterOrigin.VERIFIED_DISCLOSURE -> requireValidUrl(sourceUrl)
            DailyResetModelParameterOrigin.CALIBRATED_ASSUMPTION -> require(sourceUrl == null)
        }
    }

    private fun requireValidUrl(value: String?) {
        requireNotNull(value)
        require(value.startsWith("https://") && value.length <= 2_048)
        require(value.none(Char::isISOControl))
    }
}
