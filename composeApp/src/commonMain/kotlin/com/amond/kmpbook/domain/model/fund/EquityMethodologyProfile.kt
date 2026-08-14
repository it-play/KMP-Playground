package com.amond.kmpbook.domain.model.fund

import com.amond.kmpbook.domain.model.methodology.EquityMethodologyParameters
import com.amond.kmpbook.domain.model.methodology.EquityMethodologyRef
import kotlinx.datetime.LocalDate

/**
 * Provider-neutral declaration for one executable equity methodology.
 *
 * Product-specific constituent counts, caps, calendars and trigger thresholds belong to the
 * registered provider's typed [parameters], rather than becoming mandatory SCHD-shaped fields.
 */
class EquityMethodologyProfile(
    val methodologyRef: EquityMethodologyRef,
    val effectiveFrom: LocalDate,
    val referenceUniverse: FundReferenceUniverse,
    val parameters: EquityMethodologyParameters,
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is EquityMethodologyProfile &&
            methodologyRef == other.methodologyRef &&
            effectiveFrom == other.effectiveFrom &&
            referenceUniverse == other.referenceUniverse &&
            parameters == other.parameters

    override fun hashCode(): Int {
        var result = methodologyRef.hashCode()
        result = 31 * result + effectiveFrom.hashCode()
        result = 31 * result + referenceUniverse.hashCode()
        result = 31 * result + parameters.hashCode()
        return result
    }

    override fun toString(): String =
        "EquityMethodologyProfile(methodologyRef=$methodologyRef, effectiveFrom=$effectiveFrom, " +
            "referenceUniverse=$referenceUniverse, parameters=$parameters)"
}
