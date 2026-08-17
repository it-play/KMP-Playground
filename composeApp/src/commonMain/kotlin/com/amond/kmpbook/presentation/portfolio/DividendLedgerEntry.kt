package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.liability.TaxBreakdown
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class DividendLedgerEntry(
    val id: String,
    val stockId: String,
    val exDate: LocalDate,
    val recordDate: LocalDate,
    val paidAt: Instant,
    val currency: Currency,
    /** 분배락일에 확정된 좌당 금액과 권리 수량이다. */
    val grossPerUnit: Double,
    val entitledQuantity: Double,
    val grossAmount: Double,
    val withholdingTax: Double,
    val netAmount: Double,
    val exchangeRateToKrw: Double,
    val taxBreakdown: TaxBreakdown? = null,
    val taxableIncomeAmount: Double,
    /** 미국 펀드의 사후 원금환급 구조를 게임 시점에 분리한 금액. */
    val returnOfCapitalAmount: Double,
    /** ROC가 남은 원가를 초과해 국외주식 양도이득으로 전환된 원화 금액. */
    val excessReturnOfCapitalGainKrw: Long,
    /** 같은 시각의 체결·기업행동과 저장 전 순서를 보존하는 전역 회계 순번. */
    val accountingSequence: Long,
) {
    val grossAmountKrw: Double get() = grossAmount * exchangeRateToKrw
    val withholdingTaxKrw: Double get() = withholdingTax * exchangeRateToKrw
    val netAmountKrw: Double get() = netAmount * exchangeRateToKrw
    val financialIncomeAmount: Double get() = taxableIncomeAmount
    val financialIncomeAmountKrw: Double get() = financialIncomeAmount * exchangeRateToKrw
    val rocAmount: Double get() = returnOfCapitalAmount
}
