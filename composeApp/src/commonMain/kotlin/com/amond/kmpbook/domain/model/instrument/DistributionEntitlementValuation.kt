package com.amond.kmpbook.domain.model.instrument

import com.amond.kmpbook.domain.model.market.Currency
import kotlin.math.round

/** 권리·지급 원장이 공유하는 통화 최소단위의 canonical gross 금액이다. */
fun grossReceivableAmount(
    currency: Currency,
    grossPerUnit: Double,
    entitledQuantity: Double,
): Double {
    val minorUnitFactor = if (currency == Currency.KRW) 1.0 else 100.0
    return round(grossPerUnit * entitledQuantity * minorUnitFactor) / minorUnitFactor
}

/** 지급일 원장과 같은 통화 최소단위로 평가한 분배 미수금 총액이다. */
fun PendingDistributionEntitlement.grossReceivableAmount(): Double = grossReceivableAmount(
    currency = currency,
    grossPerUnit = grossPerUnit,
    entitledQuantity = entitledQuantity,
)

/** 주문가능 현금과 분리해 총자산에 포함할 통화별 분배 미수금이다. */
fun Iterable<PendingDistributionEntitlement>.distributionReceivableByCurrency(): Map<Currency, Double> =
    groupBy(PendingDistributionEntitlement::currency)
        .mapValues { (_, entitlements) ->
            entitlements.sumOf(PendingDistributionEntitlement::grossReceivableAmount)
        }
