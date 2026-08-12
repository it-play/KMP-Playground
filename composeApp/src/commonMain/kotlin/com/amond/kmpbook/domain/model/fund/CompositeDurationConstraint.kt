package com.amond.kmpbook.domain.model.fund

/** Portfolio effective-duration target and band, including legitimate negative-duration strategies. */
data class CompositeDurationConstraint(
    val targetYears: Double?,
    val minimumYears: Double,
    val maximumYears: Double,
    val origin: CompositeParameterOrigin,
) {
    init {
        require(minimumYears.isFinite() && maximumYears.isFinite())
        require(minimumYears in -MAX_ABSOLUTE_DURATION_YEARS..MAX_ABSOLUTE_DURATION_YEARS)
        require(maximumYears in -MAX_ABSOLUTE_DURATION_YEARS..MAX_ABSOLUTE_DURATION_YEARS)
        require(minimumYears <= maximumYears)
        targetYears?.let { require(it.isFinite() && it in minimumYears..maximumYears) }
    }

    companion object {
        const val MAX_ABSOLUTE_DURATION_YEARS: Double = 50.0
    }
}
