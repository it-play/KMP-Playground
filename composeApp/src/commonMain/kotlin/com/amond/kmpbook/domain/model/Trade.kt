package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

/** 한 번의 실제 체결. 한 주문은 여러 Trade로 나뉠 수 있다. */
data class Trade(
    val id: String,
    val orderId: String,
    val stockId: String,
    val side: OrderSide,
    val quantity: Double,
    val price: Double,
    val currency: Currency,
    val executedAt: Instant,
    val commission: Double = 0.0,
    val tax: Double = 0.0,
    val settlementKind: TradeSettlementKind = TradeSettlementKind.EXCHANGE_TRADE,
    /** 계약상 지급은 이미 지급일에 기록되므로 T+ 결제일을 다시 계산하지 않는다. */
    val settlementDateOverride: kotlinx.datetime.LocalDate? = null,
    /** 같은 시각의 체결·분배·기업행동을 저장 전과 동일한 순서로 재생하기 위한 전역 순번. */
    val accountingSequence: Long,
) {
    init {
        require(id.isNotBlank() && orderId.isNotBlank() && stockId.isNotBlank()) {
            "체결·주문·종목 ID는 비어 있을 수 없습니다."
        }
        require(quantity > 0.0) { "체결 수량은 0보다 커야 합니다." }
        require(
            price > 0.0 || settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT && price == 0.0,
        ) { "체결 가격은 양수여야 하며, 무가치 계약상 현금정산만 0일 수 있습니다." }
        require(commission >= 0.0 && tax >= 0.0) { "수수료와 세금은 음수일 수 없습니다." }
        require(
            settlementKind == TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT || settlementDateOverride == null,
        ) { "일반 거래소 체결에는 결제일을 임의 지정할 수 없습니다." }
        require(
            settlementKind != TradeSettlementKind.CONTRACTUAL_CASH_SETTLEMENT || settlementDateOverride != null,
        ) { "계약상 현금정산에는 실제 지급일이 필요합니다." }
        require(accountingSequence > 0L) {
            "회계 순번은 양수여야 합니다."
        }
    }

    val grossAmount: Double get() = quantity * price

    /** 매수는 음수, 매도는 양수인 계좌 현금 변화량. */
    val netCashFlow: Double
        get() = when (side) {
            OrderSide.BUY -> -(grossAmount + commission + tax)
            OrderSide.SELL -> grossAmount - commission - tax
        }
}
