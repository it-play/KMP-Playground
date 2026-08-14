package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioBookAdvance
import com.amond.kmpbook.domain.model.reference.CommodityReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.EquityReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceBookAdvance

/** Immutable result of the mutually independent base-reference calculations for one pricing pass. */
internal data class BaseReferenceAdvanceFrame(
    val referencePortfolio: ReferencePortfolioBookAdvance?,
    val equity: EquityReferenceBookAdvance?,
    val fixedIncome: FixedIncomeReferenceBookAdvance?,
    val commodity: CommodityReferenceBookAdvance?,
)
