package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.trading.Trade
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/** Gross cash persisted by Runtime, including the legal minimum paid for a merger fraction. */
fun canonicalTradeGrossCash(trade: Trade): Double {
    val rounded = roundCurrencyForAccounting(trade.grossAmount, trade.currency)
    return if (trade.orderId.startsWith("cash-in-lieu-order-")) {
        max(if (trade.currency == Currency.KRW) 1.0 else 0.01, rounded)
    } else {
        rounded
    }
}

fun tradeCashBalanceAfter(
    currentBalance: Double,
    side: com.amond.kmpbook.domain.model.trading.OrderSide,
    grossCash: Double,
    commission: Double,
    saleTax: Double,
    currency: Currency,
): Double {
    require(currentBalance.isFinite() && currentBalance >= 0.0)
    require(grossCash.isFinite() && grossCash >= 0.0)
    require(commission.isFinite() && commission >= 0.0)
    require(saleTax.isFinite() && saleTax >= 0.0)
    require(side != com.amond.kmpbook.domain.model.trading.OrderSide.BUY || saleTax == 0.0)
    val next = when (side) {
        com.amond.kmpbook.domain.model.trading.OrderSide.BUY ->
            currentBalance - grossCash - commission
        com.amond.kmpbook.domain.model.trading.OrderSide.SELL ->
            currentBalance + grossCash - commission - saleTax
    }
    return roundCurrencyForAccounting(next, currency).also { rounded ->
        require(rounded >= 0.0) { "현금 원장에 음수 잔액을 만들 수 없습니다." }
    }
}

const val FOREIGN_EXCHANGE_SPREAD_RATE: Double = 0.001

fun canonicalForeignExchangeReceivedAmount(
    sourceAmount: Double,
    from: Currency,
    to: Currency,
    usdKrwRate: Double,
): Double {
    require(sourceAmount.isFinite() && sourceAmount > 0.0)
    require(usdKrwRate.isFinite() && usdKrwRate > 0.0)
    require(setOf(from, to) == setOf(Currency.KRW, Currency.USD))
    val roundedSource = roundCurrencyForAccounting(sourceAmount, from)
    return if (from == Currency.KRW) {
        roundCurrencyForAccounting(
            roundedSource / usdKrwRate * (1.0 - FOREIGN_EXCHANGE_SPREAD_RATE),
            Currency.USD,
        )
    } else {
        roundCurrencyForAccounting(
            roundedSource * usdKrwRate * (1.0 - FOREIGN_EXCHANGE_SPREAD_RATE),
            Currency.KRW,
        )
    }
}

fun canonicalForeignExchangeSpreadCostKrw(
    sourceAmount: Double,
    from: Currency,
    usdKrwRate: Double,
): Double {
    val roundedSource = roundCurrencyForAccounting(sourceAmount, from)
    return if (from == Currency.KRW) {
        roundedSource * FOREIGN_EXCHANGE_SPREAD_RATE
    } else {
        roundedSource * usdKrwRate * FOREIGN_EXCHANGE_SPREAD_RATE
    }
}

/** Smallest whole-KRW source whose rounded USD receipt covers [targetUsdReceipt]. */
fun minimumKrwSourceForUsdReceipt(
    targetUsdReceipt: Double,
    usdKrwRate: Double,
): Double? {
    val target = roundCurrencyForAccounting(targetUsdReceipt, Currency.USD)
    if (!target.isFinite() || target <= 0.0 || !usdKrwRate.isFinite() || usdKrwRate <= 0.0) {
        return null
    }
    val conservativeLowerBound = floor(
        (target - USD_MINOR_UNIT).coerceAtLeast(0.0) * usdKrwRate /
            (1.0 - FOREIGN_EXCHANGE_SPREAD_RATE),
    )
    if (!conservativeLowerBound.isFinite() || conservativeLowerBound > MAX_EXACT_WHOLE_NUMBER) {
        return null
    }
    var candidate = conservativeLowerBound.toLong().coerceAtLeast(1L).toDouble()
    repeat(MAX_MINIMUM_SOURCE_SEARCH_STEPS) {
        val received = canonicalForeignExchangeReceivedAmount(
            candidate,
            Currency.KRW,
            Currency.USD,
            usdKrwRate,
        )
        if (received >= target) {
            while (candidate > 1.0 && canonicalForeignExchangeReceivedAmount(
                    candidate - 1.0,
                    Currency.KRW,
                    Currency.USD,
                    usdKrwRate,
                ) >= target
            ) {
                candidate -= 1.0
            }
            return candidate
        }
        candidate += 1.0
    }
    return null
}

/** Runtime's native-currency cash rounding (Kotlin ties-to-even), shared by canonical replay. */
fun roundCurrencyForAccounting(amount: Double, currency: Currency): Double {
    val factor = if (currency == Currency.KRW) 1.0 else 100.0
    return round(amount * factor) / factor
}

private const val USD_MINOR_UNIT: Double = 0.01
private const val MAX_EXACT_WHOLE_NUMBER: Double = 9_007_199_254_740_992.0
private const val MAX_MINIMUM_SOURCE_SEARCH_STEPS: Int = 10_000
