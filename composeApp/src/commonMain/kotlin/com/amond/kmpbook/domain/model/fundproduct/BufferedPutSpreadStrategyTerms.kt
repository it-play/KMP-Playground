package com.amond.kmpbook.domain.model.fundproduct

/** Outcome-period long-put/short-put protection financed in part by a short call. */
data class BufferedPutSpreadStrategyTerms(
    val outcomeNotionalRatio: Double,
    val longPutStrikeMoneyness: Double,
    val downsideBufferFraction: Double,
    val downsideParticipationBeyondBuffer: Double,
    val upsideCapFraction: Double,
) {
    init {
        require(outcomeNotionalRatio.isFinite() && outcomeNotionalRatio in MIN_POSITIVE_RATIO..1.0)
        require(longPutStrikeMoneyness.isFinite() && longPutStrikeMoneyness in 0.50..1.50)
        require(downsideBufferFraction.isFinite() && downsideBufferFraction in 0.001..0.95)
        require(longPutStrikeMoneyness - downsideBufferFraction > MIN_STRIKE_MONEYNESS)
        require(
            downsideParticipationBeyondBuffer.isFinite() &&
                downsideParticipationBeyondBuffer in 0.0..1.0,
        )
        require(upsideCapFraction.isFinite() && upsideCapFraction in 0.001..2.0)
    }

    companion object {
        private const val MIN_POSITIVE_RATIO: Double = 1e-9
        private const val MIN_STRIKE_MONEYNESS: Double = 0.05
    }
}
