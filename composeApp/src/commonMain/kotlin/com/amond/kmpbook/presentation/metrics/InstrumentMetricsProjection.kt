package com.amond.kmpbook.presentation.metrics

import com.amond.kmpbook.domain.model.corporateaction.CorporateFundamentalState
import com.amond.kmpbook.domain.model.instrument.FundFinancialState
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.fundstructure.ClosedEndFundState
import com.amond.kmpbook.domain.model.fundstructure.EtnState
import com.amond.kmpbook.domain.model.pricing.Quote

/** 저장된 원시 상태와 현재 시세를 결합하되 파생 비율 자체는 저장하지 않는다. */
object InstrumentMetricsProjection {
    fun project(
        stock: StockDefinition,
        quote: Quote,
        corporateState: CorporateFundamentalState?,
        fundState: FundFinancialState?,
        etnState: EtnState?,
        closedEndFundState: ClosedEndFundState?,
    ): InstrumentMetricsSnapshot? = when {
        stock.hasCorporateEarnings -> projectCorporate(
            stock,
            quote,
            requireNotNull(corporateState) { "${stock.id} 기업 재무 상태가 없습니다." },
        )
        fundState != null -> projectOpenEndFund(
            stock,
            quote,
            fundState,
        )
        etnState != null -> projectEtn(stock, quote, etnState)
        closedEndFundState != null -> projectClosedEndFund(stock, quote, closedEndFundState)
        stock.isFundLike -> error("${stock.id} 상품의 법적 구조 상태가 없습니다.")
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

    private fun projectOpenEndFund(
        stock: StockDefinition,
        quote: Quote,
        state: FundFinancialState,
    ): FundMetricsSnapshot {
        require(state.stockId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val referenceValue = state.navPerUnit
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
            assetsUnderManagement = referenceNotional,
            outstandingNotional = null,
            premiumDiscountRate = referenceValue.takeIf { it > 0.0 }
                ?.let { quote.price / it - 1.0 },
            lastNetFlow = state.lastNetFlow,
        )
    }

    private fun projectEtn(
        stock: StockDefinition,
        quote: Quote,
        state: EtnState,
    ): FundMetricsSnapshot {
        require(state.productId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val indicativeValue = state.feeAdjustedIndicativeValuePerNote.coerceAtLeast(MIN_DISPLAY_VALUE)
        val notes = state.notesOutstanding.toDouble().coerceAtLeast(1.0)
        return FundMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = quote.price * notes,
            targetLeverage = profile.leverage,
            annualExpenseRatio = profile.annualExpenseRatio,
            unitsOrNotesOutstanding = notes,
            navPerUnit = indicativeValue,
            indicativeValuePerUnit = indicativeValue,
            assetsUnderManagement = null,
            outstandingNotional = indicativeValue * notes,
            premiumDiscountRate = quote.price / indicativeValue - 1.0,
            lastNetFlow = 0.0,
        )
    }

    private fun projectClosedEndFund(
        stock: StockDefinition,
        quote: Quote,
        state: ClosedEndFundState,
    ): FundMetricsSnapshot {
        require(state.fundId == stock.id && quote.stockId == stock.id)
        val profile = requireNotNull(stock.etfProfile)
        val commonNetAssets = state.navPerCommonShare * state.commonSharesOutstanding
        return FundMetricsSnapshot(
            stockId = stock.id,
            asOf = maxOf(state.asOf, quote.timestamp),
            currency = stock.currency,
            marketCapitalization = quote.price * state.commonSharesOutstanding,
            targetLeverage = profile.leverage,
            annualExpenseRatio = profile.annualExpenseRatio,
            unitsOrNotesOutstanding = state.commonSharesOutstanding,
            navPerUnit = state.navPerCommonShare,
            indicativeValuePerUnit = state.navPerCommonShare,
            assetsUnderManagement = commonNetAssets,
            outstandingNotional = null,
            premiumDiscountRate = quote.price / state.navPerCommonShare - 1.0,
            lastNetFlow = 0.0,
        )
    }

    private const val MIN_DISPLAY_VALUE = 1e-9
}
