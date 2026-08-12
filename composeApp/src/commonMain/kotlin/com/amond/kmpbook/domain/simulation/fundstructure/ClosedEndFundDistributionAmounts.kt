package com.amond.kmpbook.domain.simulation.fundstructure

internal data class ClosedEndFundDistributionAmounts(
    val income: Double,
    val realizedGain: Double,
    val returnOfCapital: Double,
) {
    val total: Double = income + realizedGain + returnOfCapital
}
