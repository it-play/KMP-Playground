package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.time.Instant

data class ForeignExchangeRecord(
    val id: String,
    val executedAt: Instant,
    val fromCurrency: Currency,
    val toCurrency: Currency,
    val sourceAmount: Double,
    val receivedAmount: Double,
    /** 해당 거래에서 USD 1달러당 적용한 원화 가격. */
    val usdKrwRate: Double,
    val spreadCostKrw: Double,
    val automatic: Boolean,
    /** 체결·분배·세금과 같은 현금 계보에서 환전의 적용 위치를 고정한다. */
    val accountingSequence: Long,
) {
    init {
        require(fromCurrency != toCurrency) { "서로 다른 통화만 환전할 수 있습니다." }
        require(sourceAmount > 0.0 && receivedAmount > 0.0) { "환전 금액은 0보다 커야 합니다." }
        require(usdKrwRate > 0.0 && spreadCostKrw >= 0.0) { "환율과 스프레드 비용이 올바르지 않습니다." }
        require(accountingSequence > 0L) { "회계 순번은 양수여야 합니다." }
    }
}
