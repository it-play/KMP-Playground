package com.amond.kmpbook.domain.model.fundproduct

/** One pure mark-to-market interval, including an optional close-and-reopen roll. */
data class OptionStrategyAdvance(
    val state: OptionStrategyState,
    val productLogReturn: Double,
    val rolled: Boolean,
    val grossPremiumReceived: Double,
    val grossPremiumPaid: Double,
    val settlementCashFlow: Double,
    val implementationCost: Double,
) {
    init {
        require(productLogReturn.isFinite())
        require(grossPremiumReceived.isFinite() && grossPremiumReceived >= 0.0)
        require(grossPremiumPaid.isFinite() && grossPremiumPaid >= 0.0)
        require(settlementCashFlow.isFinite())
        require(implementationCost.isFinite() && implementationCost >= 0.0)
        if (!rolled) {
            require(grossPremiumReceived == 0.0)
            require(grossPremiumPaid == 0.0)
            require(implementationCost == 0.0)
            if (settlementCashFlow != 0.0) {
                require(state.lifecycle == OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION)
            }
        } else {
            require(state.lifecycle == OptionStrategyLifecycle.ACTIVE)
        }
        if (state.lifecycle == OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION) {
            require(!rolled)
        }
    }
}
