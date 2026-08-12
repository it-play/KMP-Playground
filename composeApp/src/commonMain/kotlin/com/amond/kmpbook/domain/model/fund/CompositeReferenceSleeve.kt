package com.amond.kmpbook.domain.model.fund

/** One signed composite sleeve. All exposure weights are unsigned magnitudes. */
data class CompositeReferenceSleeve(
    val sleeveId: String,
    val source: CompositeReferenceSource,
    val direction: CompositeSleeveDirection,
    val role: CompositeSleeveRole,
    val targetWeight: Double?,
    val minimumWeight: Double?,
    val maximumWeight: Double?,
    val targetWeightOrigin: CompositeParameterOrigin?,
    val weightBandOrigin: CompositeParameterOrigin?,
    val riskBudget: Double?,
    val riskBudgetOrigin: CompositeParameterOrigin?,
    val annualBorrowSpread: Double?,
    val annualBorrowSpreadOrigin: CompositeParameterOrigin?,
    val hedgeRatioToCompositeBaseCurrency: Double?,
    val hedgeRatioOrigin: CompositeParameterOrigin?,
    val mbsInterestOnlyTerms: MbsInterestOnlySleeveTerms?,
) {
    init {
        require(SLEEVE_ID_PATTERN.matches(sleeveId))
        require((minimumWeight == null) == (maximumWeight == null))
        targetWeight?.let(::requireValidWeight)
        minimumWeight?.let(::requireValidWeight)
        maximumWeight?.let(::requireValidWeight)
        if (minimumWeight != null && maximumWeight != null) {
            require(minimumWeight <= maximumWeight)
            targetWeight?.let { require(it in minimumWeight..maximumWeight) }
        }
        require((targetWeight != null) == (targetWeightOrigin != null))
        require((minimumWeight != null) == (weightBandOrigin != null))
        riskBudget?.let { require(it.isFinite() && it > 0.0 && it <= 1.0) }
        require((riskBudget != null) == (riskBudgetOrigin != null))
        require(targetWeight != null || minimumWeight != null || riskBudget != null)
        require((annualBorrowSpread != null) == (annualBorrowSpreadOrigin != null))
        when (direction) {
            CompositeSleeveDirection.LONG -> require(annualBorrowSpread == null)
            CompositeSleeveDirection.SHORT -> {
                requireNotNull(annualBorrowSpread)
                require(annualBorrowSpread.isFinite() && annualBorrowSpread in 0.0..MAX_ANNUAL_RATE)
            }
        }
        require((hedgeRatioToCompositeBaseCurrency != null) == (hedgeRatioOrigin != null))
        hedgeRatioToCompositeBaseCurrency?.let { require(it.isFinite() && it in 0.0..1.0) }
        if (mbsInterestOnlyTerms != null) {
            require(direction == CompositeSleeveDirection.LONG)
            require(role == CompositeSleeveRole.RETURN_SEEKING)
            require(source.kind == CompositeReferenceSourceKind.BENCHMARK)
        }
    }

    val signedTargetWeight: Double? = targetWeight?.let(::signed)

    private fun signed(weight: Double): Double =
        if (direction == CompositeSleeveDirection.LONG) weight else -weight

    private fun requireValidWeight(weight: Double) {
        require(weight.isFinite() && weight in 0.0..MAX_WEIGHT)
    }

    companion object {
        const val MAX_WEIGHT: Double = 10.0
        const val MAX_ANNUAL_RATE: Double = 1.0
        const val MAX_SLEEVE_ID_LENGTH: Int = 120
        private val SLEEVE_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,119}")
    }
}
