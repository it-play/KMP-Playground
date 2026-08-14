package com.amond.kmpbook.presentation.simulator

import com.amond.kmpbook.domain.model.reference.ReferenceCurrencyPair

/** Pass-invariant pricing inputs shared by provisional and finalized generation. */
internal class TurnPricingSharedInputs(
    val openingReferencedInstrumentPrices: Map<String, Double>,
    val instrumentSourceIncomeYields: Map<String, Double>,
    val instrumentSourceDurations: Map<String, Double>,
    val instrumentSourceAvailability: Map<String, Boolean>,
    val sourceFxLogReturns: Map<ReferenceCurrencyPair, Double>,
)
