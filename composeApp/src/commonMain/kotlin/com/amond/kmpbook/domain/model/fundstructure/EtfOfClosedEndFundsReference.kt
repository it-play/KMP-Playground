package com.amond.kmpbook.domain.model.fundstructure

import kotlin.time.Instant

/**
 * Composite exposure metadata for an ETF such as PCEF or YYY that owns CEF shares. This is not a
 * [ClosedEndFundState]: the wrapper keeps ETF creation/redemption, holdings, and methodology state.
 */
data class EtfOfClosedEndFundsReference(
    val etfProductId: String,
    val methodologyReferenceId: String,
    val componentClosedEndFundIds: List<String>,
    val asOf: Instant,
) {
    init {
        requireFundStructureId(etfProductId, "etfProductId")
        requireFundStructureId(methodologyReferenceId, "methodologyReferenceId")
        require(componentClosedEndFundIds.isNotEmpty())
        require(componentClosedEndFundIds.size <= MAX_COMPONENT_FUNDS)
        require(componentClosedEndFundIds.all { id ->
            id.isNotBlank() && id.length <= MAX_FUND_STRUCTURE_ID_LENGTH
        })
        require(componentClosedEndFundIds == componentClosedEndFundIds.distinct().sorted())
        require(etfProductId !in componentClosedEndFundIds)
    }

    companion object {
        private const val MAX_COMPONENT_FUNDS: Int = 10_000
    }
}
