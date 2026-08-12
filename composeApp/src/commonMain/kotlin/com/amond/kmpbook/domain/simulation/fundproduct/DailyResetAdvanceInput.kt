package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fundproduct.DailyResetState
import com.amond.kmpbook.domain.model.fundproduct.DailyResetTerms
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** 기준수익률과 확정된 장마감 reset 경계를 받는 순수 계산 입력이다. */
data class DailyResetAdvanceInput(
    val state: DailyResetState,
    val terms: DailyResetTerms,
    val referenceLogReturn: Double,
    val elapsedYearFraction: Double,
    val cashRateAnnual: Double,
    val shortBorrowRateAnnual: Double,
    /** 종목의 EtfProfile에서 가져온 연 보수로, terms에 중복 저장하지 않는다. */
    val productExpenseRateAnnual: Double,
    val referenceTradingDate: LocalDate,
    val resetAtEnd: Boolean,
    val to: Instant,
) {
    init {
        require(state.productId == terms.productId)
        require(to > state.asOf)
        require(referenceLogReturn.isFinite())
        require(elapsedYearFraction.isFinite() && elapsedYearFraction in 0.0..1.0)
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
        require(shortBorrowRateAnnual.isFinite() && shortBorrowRateAnnual in 0.0..2.0)
        require(productExpenseRateAnnual.isFinite() && productExpenseRateAnnual in 0.0..1.0)
        require(referenceTradingDate >= state.resetTradingDate)
        require(!resetAtEnd || referenceTradingDate > state.resetTradingDate) {
            "같은 기준 거래일의 일일 reset을 두 번 적용할 수 없습니다."
        }
    }
}
