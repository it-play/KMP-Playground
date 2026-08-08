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

data class RealizedGainRecord(
    val tradeId: String,
    val stockId: String,
    val market: Market,
    val soldAt: Instant,
    val settlementDate: LocalDate,
    val quantity: Double,
    val proceeds: Double,
    val costBasis: Double,
    val commission: Double,
    val saleTax: Double,
    val currency: Currency,
    val exchangeRateToKrw: Double,
    val taxTreatment: StockGainTaxTreatment = StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE,
    /** 외부계좌·관계인 정보가 없는 게임 계좌 기준 대주주 추정 설명. */
    val assessmentNotes: List<String> = emptyList(),
    /** 세법상 양도가액: 매도 결제 환율을 적용한 원화 금액. */
    val taxGrossProceedsKrw: Long = round(proceeds * exchangeRateToKrw).toLong(),
    /** FIFO 취득 lot의 취득 결제 환율·직접비용을 반영한 원화 취득가액. */
    val taxCostBasisKrw: Long = round(costBasis * exchangeRateToKrw).toLong(),
    val taxDirectSellingCostsKrw: Long = round((commission + saleTax) * exchangeRateToKrw).toLong(),
    val taxGainKrw: Long = taxGrossProceedsKrw - taxCostBasisKrw - taxDirectSellingCostsKrw,
    /** 국내상장 기타 ETF 매도 시 배당소득으로 원천징수된 과세표준. */
    val taxableFinancialIncomeKrw: Long = 0L,
) {
    val gain: Double get() = proceeds - costBasis - commission - saleTax
    /** 취득·매도 시점의 서로 다른 세법상 환율을 보존한 원화 실현손익. */
    val gainKrw: Double get() = taxGainKrw.toDouble()
}
