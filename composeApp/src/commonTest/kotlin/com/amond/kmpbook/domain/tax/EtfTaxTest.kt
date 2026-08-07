package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EtfTaxCategory
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtfTaxTest {
    private val date = LocalDate(2026, 8, 7)

    @Test
    fun domesticEquityEtfSaleHasNoTransactionOrHoldingPeriodTax() {
        val result = DomesticEtfSaleTaxCalculator().calculate(
            DomesticEtfSaleTaxRequest(
                taxCategory = EtfTaxCategory.KOREAN_DOMESTIC_EQUITY,
                grossProceedsKrw = 12_000_000L,
                acquisitionValueKrw = 10_000_000L,
                taxableStandardGainKrw = 2_000_000L,
                soldOn = date,
            ),
        )

        assertEquals(0L, result.taxableBase.minorUnits)
        assertEquals(0L, result.totalTax.minorUnits)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun otherKoreanEtfWithholdsOnSmallerOfTradingAndTaxBaseGain() {
        val result = DomesticEtfSaleTaxCalculator().calculate(
            DomesticEtfSaleTaxRequest(
                taxCategory = EtfTaxCategory.KOREAN_OTHER,
                grossProceedsKrw = 13_000_000L,
                acquisitionValueKrw = 10_000_000L,
                taxableStandardGainKrw = 2_000_000L,
                soldOn = date,
            ),
        )

        assertEquals(2_000_000L, result.taxableBase.minorUnits)
        assertEquals(280_000L, result.items.single { it.jurisdiction == TaxJurisdiction.KOREA_NATIONAL }.amount.minorUnits)
        assertEquals(28_000L, result.items.single { it.jurisdiction == TaxJurisdiction.KOREA_LOCAL }.amount.minorUnits)
        assertEquals(308_000L, result.totalTax.minorUnits)
    }

    @Test
    fun usRicDistributionUsesTreatyRateAndKeepsReclassificationWarning() {
        val result = DividendTaxCalculator().calculate(
            DividendTaxRequest(
                taxClass = DividendTaxClass.US_RIC_ETF_DISTRIBUTION,
                grossAmount = MoneyAmount(10_000L, Currency.USD),
                paidOn = date,
                taxExchangeRateToKrw = 1_350.0,
                w8BenValid = true,
            ),
        )

        assertEquals(1_500L, result.breakdown.totalTax.minorUnits)
        assertEquals(8_500L, result.netCash.minorUnits)
        assertTrue(result.breakdown.warnings.any { "사후 재분류" in it })
    }

    @Test
    fun holdingPeriodIncomeIsExcludedFromStockGainButIncludedInFinancialIncomeLedger() {
        val ledger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(
                taxYear = 2026,
                gains = listOf(
                    RealizedStockGain(
                        id = "kr-etf-sale",
                        stockId = "KOSPI:360750",
                        realizedOn = date,
                        gainKrw = 3_000_000L,
                        treatment = StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD,
                    ),
                ),
                financialIncomeGrossKrw = 2_000_000L,
                withholdingCreditsKrw = 308_000L,
            ),
        )

        assertEquals(0L, ledger.currentYearNetStockGainKrw)
        assertEquals(0L, ledger.stockTaxableBaseKrw)
        assertEquals(2_000_000L, ledger.financialIncomeGrossKrw)
        assertEquals(308_000L, ledger.withholdingCreditsKrw)
        assertEquals(0L, ledger.totalPayableKrw)
    }
}
