package com.amond.kmpbook.domain.tax.policy

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.dividend.DividendWithholdingRule
import com.amond.kmpbook.domain.tax.dividend.HighDividendSeparateTaxRule
import com.amond.kmpbook.domain.tax.domestic.DomesticMajorShareholderCapitalGainsRule
import com.amond.kmpbook.domain.tax.domestic.DomesticTransactionTaxRule
import com.amond.kmpbook.domain.tax.foreign.ForeignStockCapitalGainsRule
import com.amond.kmpbook.domain.tax.shareholder.MajorShareholderThresholdRule
import kotlinx.datetime.LocalDate

data class TaxPolicyPack(
    val id: String,
    val title: String,
    val frozenAsOf: LocalDate,
    /** Dates the game may simulate using this deliberately frozen scenario. */
    val frozenScenarioRange: EffectiveDateRange,
    val domesticTransactionTaxes: Map<Market, DomesticTransactionTaxRule>,
    val majorShareholderThresholds: Map<Market, MajorShareholderThresholdRule>,
    val domesticMajorCapitalGains: DomesticMajorShareholderCapitalGainsRule,
    val foreignStockCapitalGains: ForeignStockCapitalGainsRule,
    val koreanDividendWithholding: DividendWithholdingRule,
    val usTreatyDividendWithholding: DividendWithholdingRule,
    val financialIncomeComprehensiveThresholdKrw: Long,
    val highDividendSeparateTax: HighDividendSeparateTaxRule,
    val sources: List<RuleSource>,
) {
    init {
        require(id.isNotBlank() && title.isNotBlank()) { "A policy pack needs an id and title." }
        require(financialIncomeComprehensiveThresholdKrw > 0L) {
            "The comprehensive financial-income threshold must be positive."
        }
        require(domesticTransactionTaxes.keys.containsAll(listOf(Market.KOSPI, Market.KOSDAQ))) {
            "The frozen pack must contain KOSPI and KOSDAQ transaction taxes."
        }
        require(majorShareholderThresholds.keys.containsAll(listOf(Market.KOSPI, Market.KOSDAQ))) {
            "The frozen pack must contain KOSPI and KOSDAQ major-shareholder thresholds."
        }
    }

    fun requireSimulationDate(date: LocalDate) {
        require(date in frozenScenarioRange) {
            "$date is outside the frozen scenario range $frozenScenarioRange."
        }
    }
}
