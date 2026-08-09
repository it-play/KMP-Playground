package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.CorporateFundamentalState
import com.amond.kmpbook.domain.model.FundFinancialState
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.Quote
import com.amond.kmpbook.domain.model.StockDefinition

/** 저장된 원시 상태와 현재 시세를 결합하되 파생 비율 자체는 저장하지 않는다. */
object InstrumentMetricsProjection {
    fun project(
        stock: StockDefinition,
        quote: Quote,
        corporateState: CorporateFundamentalState?,
        fundState: FundFinancialState?,
    ): InstrumentMetricsSnapshot? = when {
        stock.hasCorporateEarnings -> projectCorporate(
            stock,
            quote,
            requireNotNull(corporateState) { "${stock.id} 기업 재무 상태가 없습니다." },
        )
        stock.isFundLike -> projectFund(
            stock,
            quote,
            requireNotNull(fundState) { "${stock.id} 상품 재무 상태가 없습니다." },
        )
        else -> null
    }

    private fun projectCorporate(
        stock: StockDefinition,
        quote: Quote,
        state: CorporateFundamentalState,
    ): CorporateMetricsSnapshot {
        require(state.stockId == stock.id && quote.stockId == stock.id)
        val shares = stock.sharesOutstanding.toDouble()
        val marketCapitalization = quote.price * shares
        val trailingRevenue = state.quarters.sumOf { it.revenue }
        val trailingNetIncome = state.quarters.sumOf { it.netIncome }
        val averageEquity = (state.equityAtTtmStart + state.bookEquity) / 2.0
        return CorporateMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = marketCapitalization,
            sharesOutstanding = shares,
            ttmRevenue = trailingRevenue,
            ttmNetIncome = trailingNetIncome,
            bookEquity = state.bookEquity,
            earningsPerShare = trailingNetIncome.takeIf(Double::isFinite)?.div(shares),
            priceEarningsRatio = trailingNetIncome.takeIf { it > 0.0 }
                ?.let { marketCapitalization / it },
            priceSalesRatio = trailingRevenue.takeIf { it > 0.0 }
                ?.let { marketCapitalization / it },
            returnOnEquity = averageEquity.takeIf { it > 0.0 }
                ?.let { trailingNetIncome / it },
            priceToBookRatio = state.bookEquity.takeIf { it > 0.0 }
                ?.let { marketCapitalization / it },
        )
    }

    private fun projectFund(
        stock: StockDefinition,
        quote: Quote,
        state: FundFinancialState,
    ): FundMetricsSnapshot {
        require(state.stockId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val isEtn = stock.instrumentType == InstrumentType.ETN
        val referenceValue = if (isEtn) state.indicativeValuePerUnit else state.navPerUnit
        val referenceNotional = referenceValue * state.unitsOrNotesOutstanding
        return FundMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = quote.price * state.unitsOrNotesOutstanding,
            targetLeverage = profile.leverage,
            annualExpenseRatio = profile.annualExpenseRatio,
            unitsOrNotesOutstanding = state.unitsOrNotesOutstanding,
            navPerUnit = state.navPerUnit,
            indicativeValuePerUnit = state.indicativeValuePerUnit,
            assetsUnderManagement = referenceNotional.takeUnless { isEtn },
            outstandingNotional = referenceNotional.takeIf { isEtn },
            premiumDiscountRate = referenceValue.takeIf { it > 0.0 }
                ?.let { quote.price / it - 1.0 },
            lastNetFlow = state.lastNetFlow,
        )
    }
}
