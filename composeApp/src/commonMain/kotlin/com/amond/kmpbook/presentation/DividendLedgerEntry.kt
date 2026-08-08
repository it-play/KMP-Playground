package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.CorporateActionRecord
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GamePhase
import com.amond.kmpbook.domain.model.Holding
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MarketIndexId
import com.amond.kmpbook.domain.model.MarketIndexSnapshot
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.MarketSession
import com.amond.kmpbook.domain.model.Order
import com.amond.kmpbook.domain.model.OrderSide
import com.amond.kmpbook.domain.model.OrderType
import com.amond.kmpbook.domain.model.PortfolioSnapshot
import com.amond.kmpbook.domain.model.PendingCorporateAction
import com.amond.kmpbook.domain.model.PriceBar
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.Screen
import com.amond.kmpbook.domain.model.ScheduledEventOccurrence
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.TimeInForce
import com.amond.kmpbook.domain.model.Trade
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.TurnStep
import com.amond.kmpbook.domain.simulation.EventEngineSnapshot
import com.amond.kmpbook.domain.simulation.MacroEnvironment
import com.amond.kmpbook.domain.simulation.OrderBookSnapshot
import com.amond.kmpbook.domain.simulation.DeterministicRandom
import com.amond.kmpbook.domain.simulation.ScheduledEventEngine
import com.amond.kmpbook.domain.tax.AnnualTaxLedger
import com.amond.kmpbook.domain.tax.FeeBreakdown
import com.amond.kmpbook.domain.tax.FifoCostBasisBook
import com.amond.kmpbook.domain.tax.TaxBreakdown
import com.amond.kmpbook.domain.tax.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.StockGainTaxTreatment
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlin.time.Instant

data class DividendLedgerEntry(
    val id: String,
    val stockId: String,
    val paidAt: Instant,
    val currency: Currency,
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
