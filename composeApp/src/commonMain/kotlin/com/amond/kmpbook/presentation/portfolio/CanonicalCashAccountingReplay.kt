package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.model.trading.Trade
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant

/**
 * Rebuilds holder cash from durable accounting facts. Observed FX conversions and explicit debug
 * cash overrides are fact boundaries; every other balance change is independently derived.
 */
object CanonicalCashAccountingReplay {
    fun replay(
        initialCapitalKrw: Double,
        campaignSeed: Long,
        currentTime: Instant,
        trades: List<Trade>,
        transactionCosts: List<TransactionCostRecord>,
        foreignExchanges: List<ForeignExchangeRecord>,
        dividends: List<DividendLedgerEntry>,
        taxPaymentNotices: List<TaxPaymentNotice>,
        cashAdjustments: List<CashAdjustmentRecord>,
    ): Map<Currency, Double> {
        require(initialCapitalKrw.isFinite() && initialCapitalKrw >= 0.0)
        val costsByTradeId = transactionCosts.associateBy(TransactionCostRecord::tradeId)
        require(costsByTradeId.size == transactionCosts.size &&
            costsByTradeId.keys == trades.mapTo(linkedSetOf(), Trade::id)
        ) { "현금 재생에는 체결별 거래비용 원장이 정확히 하나씩 필요합니다." }

        val events = buildList {
            trades.forEach { add(Event(it.accountingSequence, it.executedAt, Kind.TRADE, it.id)) }
            foreignExchanges.forEach { add(Event(it.accountingSequence, it.executedAt, Kind.FX, it.id)) }
            dividends.forEach { add(Event(it.accountingSequence, it.paidAt, Kind.DIVIDEND, it.id)) }
            taxPaymentNotices.filter { it.status == TaxLiabilityStatus.PAID }.forEach { notice ->
                add(
                    Event(
                        requireNotNull(notice.accountingSequence),
                        requireNotNull(notice.paidAt),
                        Kind.TAX_PAYMENT,
                        notice.id,
                    ),
                )
            }
            cashAdjustments.forEach {
                add(Event(it.accountingSequence, it.adjustedAt, Kind.DEBUG_ADJUSTMENT, it.id))
            }
        }.sortedBy(Event::accountingSequence)
        require(events.map(Event::accountingSequence).distinct().size == events.size) {
            "현금 계보의 전역 회계 순번이 중복되었습니다."
        }
        require(events.all { event ->
            event.occurredAt in GameCalendar.startInstant..currentTime
        } && events.zipWithNext().all { (left, right) -> left.occurredAt <= right.occurredAt }) {
            "현금 계보의 회계 순번과 적용 시각이 다릅니다."
        }
        val tradesById = trades.associateBy(Trade::id)
        val fxById = foreignExchanges.associateBy(ForeignExchangeRecord::id)
        val dividendsById = dividends.associateBy(DividendLedgerEntry::id)
        val noticesById = taxPaymentNotices.associateBy(TaxPaymentNotice::id)
        val adjustmentsById = cashAdjustments.associateBy(CashAdjustmentRecord::id)
        require(tradesById.size == trades.size && fxById.size == foreignExchanges.size &&
            dividendsById.size == dividends.size && noticesById.size == taxPaymentNotices.size &&
            adjustmentsById.size == cashAdjustments.size
        ) { "현금 계보 원장 ID가 중복되었습니다." }

        val balances = Currency.entries.associateWithTo(linkedMapOf()) { currency ->
            if (currency == Currency.KRW) roundCurrencyForAccounting(initialCapitalKrw, currency) else 0.0
        }
        events.forEach { event ->
            when (event.kind) {
                Kind.TRADE -> {
                    val trade = tradesById.getValue(event.id)
                    val cost = costsByTradeId.getValue(trade.id)
                    require(cost.currency == trade.currency &&
                        cost.commission.toBits() == trade.commission.toBits() &&
                        cost.saleTax.toBits() == trade.tax.toBits()
                    ) { "체결과 거래비용의 현금 금액이 다릅니다." }
                    require(trade.side != OrderSide.BUY || trade.tax == 0.0) {
                        "매수 현금 계보에는 매도세가 존재할 수 없습니다."
                    }
                    val gross = canonicalTradeGrossCash(trade)
                    balances[trade.currency] = tradeCashBalanceAfter(
                        currentBalance = balances.getValue(trade.currency),
                        side = trade.side,
                        grossCash = gross,
                        commission = trade.commission,
                        saleTax = trade.tax,
                        currency = trade.currency,
                    )
                }

                Kind.FX -> {
                    val fx = fxById.getValue(event.id)
                    require(fx.id == "fx-$campaignSeed-${fx.accountingSequence}")
                    require(
                        setOf(fx.fromCurrency, fx.toCurrency) == setOf(Currency.KRW, Currency.USD),
                    )
                    require(fx.sourceAmount in MIN_ACCOUNTING_AMOUNT..MAX_ACCOUNTING_AMOUNT &&
                        fx.receivedAmount in MIN_ACCOUNTING_AMOUNT..MAX_ACCOUNTING_AMOUNT &&
                        fx.usdKrwRate in MIN_USD_KRW..MAX_USD_KRW &&
                        fx.sourceAmount.toBits() ==
                        roundCurrencyForAccounting(fx.sourceAmount, fx.fromCurrency).toBits()
                    )
                    val expectedReceived = canonicalForeignExchangeReceivedAmount(
                        sourceAmount = fx.sourceAmount,
                        from = fx.fromCurrency,
                        to = fx.toCurrency,
                        usdKrwRate = fx.usdKrwRate,
                    )
                    val expectedSpreadCostKrw = canonicalForeignExchangeSpreadCostKrw(
                        sourceAmount = fx.sourceAmount,
                        from = fx.fromCurrency,
                        usdKrwRate = fx.usdKrwRate,
                    )
                    require(fx.receivedAmount.toBits() == expectedReceived.toBits() &&
                        fx.spreadCostKrw.toBits() == expectedSpreadCostKrw.toBits()
                    ) { "환전 원장의 수령액 또는 스프레드 비용이 canonical 계산과 다릅니다." }
                    applyDelta(balances, fx.fromCurrency, -fx.sourceAmount)
                    applyDelta(balances, fx.toCurrency, fx.receivedAmount)
                }

                Kind.DIVIDEND -> {
                    val dividend = dividendsById.getValue(event.id)
                    applyDelta(balances, dividend.currency, dividend.netAmount)
                }

                Kind.TAX_PAYMENT -> {
                    val notice = noticesById.getValue(event.id)
                    require(notice.status == TaxLiabilityStatus.PAID && notice.amountKrw > 0L)
                    applyDelta(balances, Currency.KRW, -notice.amountKrw.toDouble())
                }

                Kind.DEBUG_ADJUSTMENT -> {
                    val adjustment = adjustmentsById.getValue(event.id)
                    require(
                        adjustment.id ==
                            "cash-adjustment-$campaignSeed-${adjustment.accountingSequence}",
                    )
                    require(adjustment.balanceBefore.toBits() ==
                        roundCurrencyForAccounting(
                            adjustment.balanceBefore,
                            adjustment.currency,
                        ).toBits() && adjustment.balanceAfter.toBits() ==
                        roundCurrencyForAccounting(
                            adjustment.balanceAfter,
                            adjustment.currency,
                        ).toBits())
                    require(
                        balances.getValue(adjustment.currency).toBits() ==
                            adjustment.balanceBefore.toBits(),
                    ) { "디버그 현금 조정의 선행 잔액이 계보와 다릅니다." }
                    balances[adjustment.currency] = roundCurrencyForAccounting(
                        adjustment.balanceAfter,
                        adjustment.currency,
                    )
                }
            }
        }
        return balances
    }

    private fun applyDelta(
        balances: MutableMap<Currency, Double>,
        currency: Currency,
        delta: Double,
    ) {
        require(delta.isFinite())
        val next = roundCurrencyForAccounting(balances.getValue(currency) + delta, currency)
        require(next >= 0.0) { "현금 원장 재생 중 음수 잔액이 발생했습니다." }
        balances[currency] = if (next == -0.0) 0.0 else next
    }

    // Event and Kind exist only for this replay's deterministic accounting order. Keeping them
    // nested preserves that lifecycle and encapsulation; a separate file would widen them to internal.
    private data class Event(
        val accountingSequence: Long,
        val occurredAt: Instant,
        val kind: Kind,
        val id: String,
    )

    private enum class Kind { TRADE, FX, DIVIDEND, TAX_PAYMENT, DEBUG_ADJUSTMENT }

    private const val MIN_ACCOUNTING_AMOUNT: Double = 0.01
    private const val MAX_ACCOUNTING_AMOUNT: Double = 1e18
    private const val MIN_USD_KRW: Double = 800.0
    private const val MAX_USD_KRW: Double = 2_500.0
}
