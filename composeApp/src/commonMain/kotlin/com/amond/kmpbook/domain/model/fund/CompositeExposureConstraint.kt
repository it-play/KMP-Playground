package com.amond.kmpbook.domain.model.fund

/** A bounded gross, short-gross, or signed net exposure. */
data class CompositeExposureConstraint(
    val target: Double?,
    val minimum: Double,
    val maximum: Double,
    val origin: CompositeParameterOrigin,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite())
        require(minimum in -MAX_ABSOLUTE_EXPOSURE..MAX_ABSOLUTE_EXPOSURE)
        require(maximum in -MAX_ABSOLUTE_EXPOSURE..MAX_ABSOLUTE_EXPOSURE)
        require(minimum <= maximum)
        target?.let {
            require(it.isFinite() && it in minimum..maximum)
        }
    }

    companion object {
        const val MAX_ABSOLUTE_EXPOSURE: Double = 10.0
    }
}
