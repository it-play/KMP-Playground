package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyState
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyTerms
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyLifecycle
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Market inputs for one pure option-overlay mark-to-market interval. */
data class OptionStrategyAdvanceInput(
    val state: OptionStrategyState,
    val terms: OptionStrategyTerms,
    val underlyingTotalLogReturn: Double,
    val cashRateAnnual: Double,
    val annualizedImpliedVolatility: Double,
    val elapsedYearFraction: Double,
    val referenceTradingDate: LocalDate,
    val tradingCloseAtEnd: Boolean,
    val forceRollAtEnd: Boolean,
    val allowOpeningNewCycle: Boolean = true,
    val to: Instant,
) {
    init {
        require(state.productId == terms.productId)
        require(state.strategyKind == terms.kind)
        require(state.rollCalendar == terms.rollCalendar)
        require(to > state.asOf)
        require(underlyingTotalLogReturn.isFinite() && underlyingTotalLogReturn in -5.0..5.0)
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
        require(annualizedImpliedVolatility.isFinite() && annualizedImpliedVolatility in 0.0..5.0)
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
        require(referenceTradingDate >= state.cycleStartedOn)
        require(
            state.lastProcessedTradingDate == null ||
                referenceTradingDate >= state.lastProcessedTradingDate,
        )
        require(!forceRollAtEnd || tradingCloseAtEnd)
        if (state.lifecycle == OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION) {
            require(!allowOpeningNewCycle && !forceRollAtEnd)
        }
        require(!tradingCloseAtEnd || terms.rollCalendar.isTradingDate(referenceTradingDate)) {
            "옵션 tenor는 지정된 roll calendar의 정규 거래일 종가에서만 소진할 수 있습니다."
        }
        require(
            !forceRollAtEnd || state.lastProcessedTradingDate != referenceTradingDate,
        ) { "같은 거래일 종가에 강제 roll을 두 번 적용할 수 없습니다." }
    }
}
