package com.amond.kmpbook.domain.model.fund

data class AlternativeRiskPremiaDriver(
    val driverId: String,
    val source: CompositeReferenceSource,
    val strategyFamily: AlternativeRiskPremiaStrategyFamily,
    val signalDirectionPolicy: AlternativeRiskPremiaSignalDirectionPolicy,
    val targetRiskBudget: Double?,
    val riskBudgetOrigin: CompositeParameterOrigin?,
    val hedgeRatioToProfileBaseCurrency: Double?,
    val hedgeRatioOrigin: CompositeParameterOrigin?,
) {
    init {
        require(DRIVER_ID_PATTERN.matches(driverId))
        require((targetRiskBudget != null) == (riskBudgetOrigin != null))
        targetRiskBudget?.let { require(it.isFinite() && it > 0.0 && it <= 1.0) }
        require((hedgeRatioToProfileBaseCurrency != null) == (hedgeRatioOrigin != null))
        hedgeRatioToProfileBaseCurrency?.let { require(it.isFinite() && it in 0.0..1.0) }
    }

    companion object {
        const val MAX_DRIVER_ID_LENGTH: Int = 120
        private val DRIVER_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,119}")
    }
}
