package com.amond.kmpbook.domain.model.trading

import kotlin.math.abs
import kotlin.time.Instant

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
        require(quantity.isFinite() && quantity > 0.0) { "주문 수량은 유한한 양수여야 합니다." }
        require(limitPrice == null || limitPrice.isFinite() && limitPrice > 0.0) {
            "지정가는 유한한 양수여야 합니다."
        }
        require(type != OrderType.LIMIT || limitPrice != null) { "지정가 주문에는 가격이 필요합니다." }
        require(type == OrderType.LIMIT || limitPrice == null) { "시장가 주문에는 지정가를 지정할 수 없습니다." }
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
