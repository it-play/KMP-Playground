package com.amond.kmpbook.domain.simulation.fundproduct

/** Internal transaction result kept separate so persisted state never needs an uninvested phase. */
internal data class CashCollateralizedPutSpreadCycleOpening(
    val navAtRoll: Double,
    val units: Double,
    val longPutStrike: Double,
    val shortPutStrike: Double,
    val optionNotionalAtRoll: Double,
    val maximumSettlementLossAtRoll: Double,
    val netOptionMark: Double,
    val grossPremiumReceived: Double,
    val grossPremiumPaid: Double,
    val implementationCost: Double,
    val cashBalance: Double,
    val resultingNav: Double,
)
