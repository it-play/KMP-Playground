package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadLifecycle
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadState
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadTerms
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Independent cash-benchmark and option-underlying inputs for one valuation interval. */
data class CashCollateralizedPutSpreadAdvanceInput(
    val state: CashCollateralizedPutSpreadState,
    val terms: CashCollateralizedPutSpreadTerms,
    val cashBenchmarkTotalLogReturn: Double,
    val optionUnderlyingTotalLogReturn: Double,
    val optionDiscountRateAnnual: Double,
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
        require(state.cashBenchmarkRef == terms.cashBenchmarkRef)
        require(state.optionReference == terms.optionReference)
        require(state.rollCalendar == terms.rollCalendar)
        require(to > state.asOf)
        require(cashBenchmarkTotalLogReturn.isFinite() && cashBenchmarkTotalLogReturn in -5.0..5.0)
        require(optionUnderlyingTotalLogReturn.isFinite() && optionUnderlyingTotalLogReturn in -5.0..5.0)
        require(optionDiscountRateAnnual.isFinite() && optionDiscountRateAnnual in -0.10..1.0)
        require(annualizedImpliedVolatility.isFinite() && annualizedImpliedVolatility in 0.0..5.0)
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
        if (elapsedYearFraction == 0.0) {
            require(cashBenchmarkTotalLogReturn == 0.0)
            require(optionUnderlyingTotalLogReturn == 0.0)
        }
        require(referenceTradingDate >= state.cycleStartedOn)
        require(
            state.lastProcessedTradingDate == null ||
                referenceTradingDate >= state.lastProcessedTradingDate,
        )
        require(!forceRollAtEnd || tradingCloseAtEnd)
        if (state.lifecycle == CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION) {
            require(!allowOpeningNewCycle && !forceRollAtEnd)
        }
        require(
            state.lifecycle == CashCollateralizedPutSpreadLifecycle.ACTIVE ||
                !forceRollAtEnd,
        )
        require(!tradingCloseAtEnd || terms.rollCalendar.isTradingDate(referenceTradingDate)) {
            "풋스프레드 tenor는 지정된 roll calendar의 정규 거래일 종가에서만 소진할 수 있습니다."
        }
        require(!forceRollAtEnd || state.lastProcessedTradingDate != referenceTradingDate) {
            "같은 거래일 종가에 강제 roll을 두 번 적용할 수 없습니다."
        }
    }
}
