package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.TradeSettlementKind
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** 변경 불가능한 체결 원장 항목이다. */
data class ModTradeSnapshot(
    val id: String,
    val orderId: String,
    val instrumentId: String,
    val side: OrderSide,
    val quantity: Double,
    val price: Double,
    val grossAmount: Double,
    val currency: Currency,
    val executedAt: Instant,
    val commission: Double,
    val tax: Double,
    val settlementKind: TradeSettlementKind,
    /** 계약상 현금정산의 실제 지급일이며, 일반 거래소 체결에서는 null이다. */
    val settlementDateOverride: LocalDate?,
)
