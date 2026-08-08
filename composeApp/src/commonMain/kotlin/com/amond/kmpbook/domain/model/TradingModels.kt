package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

enum class OrderSide(val displayName: String, val cashFlowSign: Int) {
    BUY("매수", -1),
    SELL("매도", 1),
}

enum class OrderType(val displayName: String) {
    MARKET("시장가"),
    LIMIT("지정가"),
}

enum class OrderStatus(val displayName: String, val isTerminal: Boolean) {
    PENDING("접수 대기", false),
    ACCEPTED("접수", false),
    PARTIALLY_FILLED("일부 체결", false),
    FILLED("체결 완료", true),
    CANCELLED("취소", true),
    REJECTED("거부", true),
    EXPIRED("만료", true),
}

enum class TimeInForce(val displayName: String) {
    DAY("당일 주문"),
    GOOD_TILL_CANCELLED("취소 전까지"),
    IMMEDIATE_OR_CANCEL("즉시 체결 후 잔량 취소"),
    FILL_OR_KILL("전량 즉시 체결"),
}

/** 주문 수량은 소수점 거래 확장을 위해 Double이며 종목의 quantityStep으로 검증한다. */
data class Order(
    val id: String,
    val stockId: String,
    val side: OrderSide,
    val type: OrderType,
    val quantity: Double,
    val createdAt: Instant,
    val limitPrice: Double? = null,
    val status: OrderStatus = OrderStatus.PENDING,
    val filledQuantity: Double = 0.0,
    val averageFilledPrice: Double? = null,
    val updatedAt: Instant = createdAt,
    val timeInForce: TimeInForce = TimeInForce.DAY,
    val rejectionReason: String? = null,
    /** 거래소 주문이 아니라 만기·청산 계약으로 만든 읽기 전용 처분 원장인지 구분한다. */
    val isNonMarketDisposition: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "주문 ID는 비어 있을 수 없습니다." }
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(quantity > 0.0) { "주문 수량은 0보다 커야 합니다." }
        require(limitPrice == null || limitPrice > 0.0) { "지정가는 0보다 커야 합니다." }
        require(type != OrderType.LIMIT || limitPrice != null) { "지정가 주문에는 가격이 필요합니다." }
        require(filledQuantity >= 0.0 && filledQuantity <= quantity + QUANTITY_EPSILON) {
            "체결 수량은 0 이상 주문 수량 이하여야 합니다."
        }
        require(
            averageFilledPrice == null || averageFilledPrice > 0.0 ||
                isNonMarketDisposition && averageFilledPrice == 0.0,
        ) { "평균 체결가는 양수여야 하며, 무가치 계약상 처분만 0일 수 있습니다." }
        require(filledQuantity == 0.0 || averageFilledPrice != null) { "체결된 주문에는 평균 체결가가 필요합니다." }
        require(updatedAt >= createdAt) { "주문 갱신 시각은 생성 시각보다 빠를 수 없습니다." }
    }

    val remainingQuantity: Double get() = (quantity - filledQuantity).coerceAtLeast(0.0)
    val isOpen: Boolean get() = !status.isTerminal
    val canCancel: Boolean get() = status == OrderStatus.ACCEPTED || status == OrderStatus.PARTIALLY_FILLED
    val requestedAmount: Double? get() = limitPrice?.times(quantity)
    val filledAmount: Double get() = (averageFilledPrice ?: 0.0) * filledQuantity

    fun isFullyFilled(): Boolean = abs(quantity - filledQuantity) < QUANTITY_EPSILON

    private companion object {
        const val QUANTITY_EPSILON = 1e-7
    }
}

enum class TradeSettlementKind {
    EXCHANGE_TRADE,
    CONTRACTUAL_CASH_SETTLEMENT,
}

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

/** 매수 또는 매도 호가 한 단계. */
data class OrderBookLevel(
    val price: Double,
    val quantity: Double,
    val orderCount: Int,
) {
    init {
        require(price > 0.0) { "호가 가격은 0보다 커야 합니다." }
        require(quantity >= 0.0) { "호가 수량은 음수일 수 없습니다." }
        require(orderCount >= 0) { "주문 건수는 음수일 수 없습니다." }
    }
}

/** 시가창의 매수·매도 잔량. bids는 고가 우선, asks는 저가 우선으로 공급한다. */
data class OrderBook(
    val stockId: String,
    val timestamp: Instant,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
) {
    init {
        require(stockId.isNotBlank()) { "종목 ID는 비어 있을 수 없습니다." }
        require(bids.zipWithNext().all { (left, right) -> left.price >= right.price }) {
            "매수 호가는 높은 가격부터 정렬되어야 합니다."
        }
        require(asks.zipWithNext().all { (left, right) -> left.price <= right.price }) {
            "매도 호가는 낮은 가격부터 정렬되어야 합니다."
        }
    }

    val bestBid: OrderBookLevel? get() = bids.firstOrNull()
    val bestAsk: OrderBookLevel? get() = asks.firstOrNull()
    val spread: Double? get() = bestBid?.let { bid -> bestAsk?.price?.minus(bid.price) }
    val totalBidQuantity: Double get() = bids.sumOf { it.quantity }
    val totalAskQuantity: Double get() = asks.sumOf { it.quantity }
}
