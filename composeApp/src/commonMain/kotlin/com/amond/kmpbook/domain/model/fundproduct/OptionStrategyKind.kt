package com.amond.kmpbook.domain.model.fundproduct

/** Option overlays with materially different holdings and terminal payoff equations. */
enum class OptionStrategyKind {
    /** Direct ownership of the reference plus a systematically overwritten call notional. */
    COVERED_CALL,

    /** Core equity plus a cash-collateralized equity-linked option-income sleeve. */
    OPTION_INCOME,

    /** Direct reference exposure protected by a long/short put spread and financed by a short call. */
    BUFFERED_PUT_SPREAD,
}
