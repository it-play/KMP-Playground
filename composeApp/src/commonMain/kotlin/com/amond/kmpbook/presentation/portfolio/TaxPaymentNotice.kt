package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class TaxPaymentNotice(
    val id: String,
    val taxYear: Int,
    val dueDate: LocalDate,
    val currency: Currency,
    val amountKrw: Long,
    val status: TaxLiabilityStatus,
    /** 실제 납부가 원화 현금에서 빠져나간 게임 시각. 납부 전 상태에는 반드시 null이다. */
    val paidAt: Instant?,
    /** 납부를 체결·분배·기업행동과 같은 전역 회계 순서에 고정한다. */
    val accountingSequence: Long?,
    val message: String,
) {
    init {
        require(id.isNotBlank() && taxYear >= 2026)
        require(currency == Currency.KRW && amountKrw > 0L)
        if (status == TaxLiabilityStatus.PAID) {
            require(paidAt != null && accountingSequence != null && accountingSequence > 0L)
        } else {
            require(paidAt == null && accountingSequence == null)
        }
    }
}
