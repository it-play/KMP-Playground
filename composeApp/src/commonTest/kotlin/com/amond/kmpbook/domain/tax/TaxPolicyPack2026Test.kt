package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaxPolicyPack2026Test {
    private val currentDate = LocalDate(2026, 8, 7)

    @Test
    fun kospiAndKosdaqSaleTaxesAreSeparatedAndTotalPointTwoPercent() {
        val calculator = DomesticSaleTaxCalculator()
        val kospi = calculator.calculate(
            DomesticSaleTaxRequest(Market.KOSPI, 10_000_000L, currentDate),
        )
        val kosdaq = calculator.calculate(
            DomesticSaleTaxRequest(Market.KOSDAQ, 10_000_000L, currentDate),
        )

        assertEquals(5_000L, kospi.items.single { it.category == TaxCategory.SECURITIES_TRANSACTION }.amount.minorUnits)
        assertEquals(15_000L, kospi.items.single { it.category == TaxCategory.SPECIAL_RURAL }.amount.minorUnits)
        assertEquals(20_000L, kospi.totalTax.minorUnits)
        assertEquals(20_000L, kosdaq.totalTax.minorUnits)
        assertEquals(1, kosdaq.items.size)
    }

    @Test
    fun smallShareholderOnExchangeIsExemptAndMajorShareholderIsTaxable() {
        val calculator = MajorShareholderCalculator()
        val below = calculator.assess(
            majorRequest(Market.KOSPI, ownership = 0.0099, marketValueKrw = 4_999_999_999L),
        )
        val byRatio = calculator.assess(
            majorRequest(Market.KOSPI, ownership = 0.01, marketValueKrw = 1_000_000L),
        )
        val byValue = calculator.assess(
            majorRequest(Market.KOSDAQ, ownership = 0.001, marketValueKrw = 5_000_000_000L),
        )
        val kosdaqBelow = calculator.assess(
            majorRequest(Market.KOSDAQ, ownership = 0.0199, marketValueKrw = 4_999_999_999L),
        )
        val kosdaqAtRatio = calculator.assess(
            majorRequest(Market.KOSDAQ, ownership = 0.02, marketValueKrw = 0L),
        )

        assertFalse(below.isMajorShareholder)
        assertEquals(
            DomesticListedSaleTaxTreatment.EXEMPT_SMALL_SHAREHOLDER_ON_EXCHANGE,
            domesticListedSaleTaxTreatment(isOnExchange = true, assessment = below),
        )
        assertTrue(byRatio.isMajorShareholder)
        assertTrue(byValue.isMajorShareholder)
        assertFalse(kosdaqBelow.isMajorShareholder)
        assertTrue(kosdaqAtRatio.isMajorShareholder)
        assertEquals(
            DomesticListedSaleTaxTreatment.TAXABLE_MAJOR_SHAREHOLDER,
            domesticListedSaleTaxTreatment(isOnExchange = true, assessment = byRatio),
        )
    }

    @Test
    fun largestShareholderGroupAggregatesRelatedHoldings() {
        val holdings = listOf(
            ShareholderHoldingSnapshot("player", ShareholderRelation.SELF, 0.006, 2_000_000_000L),
            ShareholderHoldingSnapshot("relative", ShareholderRelation.RELATIVE, 0.004, 1_000_000_000L),
        )
        val calculator = MajorShareholderCalculator()
        val grouped = calculator.assess(
            MajorShareholderAssessmentRequest(Market.KOSPI, currentDate, holdings, true),
        )
        val ungrouped = calculator.assess(
            MajorShareholderAssessmentRequest(Market.KOSPI, currentDate, holdings, false),
        )

        assertTrue(grouped.isMajorShareholder)
        assertFalse(ungrouped.isMajorShareholder)
    }

    @Test
    fun domesticMajorShareholderRatesCoverTwentyTwentyFiveAndThirtyPercent() {
        val calculator = DomesticMajorCapitalGainsCalculator()
        val progressive = calculator.calculate(
            DomesticMajorCapitalGainsRequest(
                taxYear = 2026,
                taxableBaseKrw = 400_000_000L,
                isSmallOrMediumEnterprise = true,
                heldLessThanOneYear = false,
                calculatedOn = currentDate,
            ),
        )
        val shortTerm = calculator.calculate(
            DomesticMajorCapitalGainsRequest(
                taxYear = 2026,
                taxableBaseKrw = 400_000_000L,
                isSmallOrMediumEnterprise = false,
                heldLessThanOneYear = true,
                calculatedOn = currentDate,
            ),
        )

        assertEquals(93_500_000L, progressive.assessedTaxKrw) // 60m + 25m + 8.5m local
        assertEquals(132_000_000L, shortTerm.assessedTaxKrw) // 30% + 3% local
    }

    @Test
    fun foreignStockAnnualGainSharesDeductionAndPaysTwentyTwoPercent() {
        val ledger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(
                taxYear = 2026,
                gains = listOf(foreignGain("us-1", LocalDate(2026, 12, 30), 12_000_000L)),
            ),
        )

        assertEquals(12_000_000L, ledger.foreignGainKrw)
        assertEquals(2_500_000L, ledger.sharedStockBasicDeductionKrw)
        assertEquals(9_500_000L, ledger.stockTaxableBaseKrw)
        assertEquals(2_090_000L, ledger.totalPayableKrw)
        assertEquals(LocalDate(2027, 5, 31), ledger.liabilities.single().dueDate)
    }

    @Test
    fun taxableDomesticAndForeignGainsNetAndUseOneDeduction() {
        val ledger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(
                taxYear = 2026,
                gains = listOf(
                    RealizedStockGain(
                        id = "kr-major",
                        stockId = "KOSPI:005930",
                        realizedOn = LocalDate(2026, 4, 1),
                        gainKrw = 10_000_000L,
                        treatment = StockGainTaxTreatment.DOMESTIC_MAJOR_GENERAL,
                    ),
                    foreignGain("us-loss", LocalDate(2026, 7, 1), -3_000_000L),
                    RealizedStockGain(
                        id = "kr-exempt-loss",
                        stockId = "KOSPI:000660",
                        realizedOn = LocalDate(2026, 9, 1),
                        gainKrw = -100_000_000L,
                        treatment = StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE,
                    ),
                ),
            ),
        )

        assertEquals(7_000_000L, ledger.currentYearNetStockGainKrw)
        assertEquals(2_500_000L, ledger.sharedStockBasicDeductionKrw)
        assertEquals(4_500_000L, ledger.stockTaxableBaseKrw)
        assertEquals(990_000L, ledger.totalPayableKrw)
    }

    @Test
    fun currentYearForeignLossExpiresInsteadOfCarryingForward() {
        val ledger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(
                taxYear = 2026,
                gains = listOf(foreignGain("loss", currentDate, -1_000_000L)),
            ),
        )
        assertEquals(1_000_000L, ledger.expiredStockLossKrw)
        assertEquals(0L, ledger.stockTaxableBaseKrw)
        assertTrue(ledger.liabilities.isEmpty())
    }

    @Test
    fun usYearEndTradeUsesSettlementDateTaxYear() {
        val settlementDateGain = foreignGain("year-boundary", LocalDate(2027, 1, 4), 3_000_000L)
        assertFailsWith<IllegalArgumentException> {
            AnnualStockTaxRequest(taxYear = 2026, gains = listOf(settlementDateGain))
        }
        val ledger = AnnualStockTaxCalculator().calculate(
            AnnualStockTaxRequest(taxYear = 2027, gains = listOf(settlementDateGain)),
        )
        assertEquals(2027, ledger.taxYear)
        assertEquals(500_000L, ledger.stockTaxableBaseKrw)
    }

    @Test
    fun fifoConsumesOldestLotsAndPreservesRemainingBasis() {
        val book = FifoCostBasisBook()
            .addPurchase("lot-1", "NASDAQ:TEST", LocalDate(2026, 1, 2), 10.0, 100_000L)
            .addPurchase("lot-2", "NASDAQ:TEST", LocalDate(2026, 2, 2), 10.0, 200_000L)
        val sale = book.sell(
            stockId = "NASDAQ:TEST",
            soldOn = currentDate,
            quantity = 15.0,
            grossProceedsKrw = 300_000L,
        )

        assertEquals(listOf("lot-1", "lot-2"), sale.consumedLots.map { it.lotId })
        assertEquals(200_000L, sale.allocatedCostBasisKrw)
        assertEquals(100_000L, sale.realizedGainKrw)
        assertEquals(5.0, sale.updatedBook.lots.single().remainingQuantity)
        assertEquals(100_000L, sale.updatedBook.lots.single().remainingCostBasisKrw)
    }

    @Test
    fun domesticAndUsDividendWithholdingProduceExplainableNetCash() {
        val calculator = DividendTaxCalculator()
        val korean = calculator.calculate(
            DividendTaxRequest(
                taxClass = DividendTaxClass.KOREAN_ORDINARY_CASH,
                grossAmount = MoneyAmount(1_000_000L, Currency.KRW),
                paidOn = currentDate,
            ),
        )
        val unitedStates = calculator.calculate(
            DividendTaxRequest(
                taxClass = DividendTaxClass.US_ORDINARY_CORPORATION,
                grossAmount = MoneyAmount(10_000L, Currency.USD), // USD 100.00
                paidOn = currentDate,
                taxExchangeRateToKrw = 1_300.0,
                w8BenValid = true,
            ),
        )

        assertEquals(154_000L, korean.breakdown.totalTax.minorUnits)
        assertEquals(846_000L, korean.netCash.minorUnits)
        assertEquals(1_500L, unitedStates.breakdown.totalTax.minorUnits)
        assertEquals(8_500L, unitedStates.netCash.minorUnits)
        assertEquals(130_000L, unitedStates.grossIncomeKrw)
        assertEquals(0L, unitedStates.breakdown.items.filter { it.jurisdiction != TaxJurisdiction.UNITED_STATES_FEDERAL }.sumOf { it.amount.minorUnits })
    }

    @Test
    fun financialIncomeWarningUsesGrossTwentyMillionThreshold() {
        val estimator = FinancialIncomeEstimator()
        assertFalse(
            estimator.assess(20_000_000L, 0L, false, false).exceedsComprehensiveThreshold,
        )
        assertTrue(
            estimator.assess(20_000_001L, 0L, false, false).exceedsComprehensiveThreshold,
        )
        val elected = estimator.assess(
            ordinaryFinancialIncomeGrossKrw = 15_000_000L,
            electedHighDividendIncomeKrw = 30_000_000L,
            highDividendElectionApplied = true,
            otherIncomeInformationComplete = false,
        )
        assertEquals(15_000_000L, elected.amountCountedForThresholdKrw)
        assertFalse(elected.exceedsComprehensiveThreshold)
    }

    @Test
    fun highDividendElectionUsesFourProgressiveBandsAndLocalTax() {
        val result = HighDividendElectionCalculator().calculate(
            HighDividendElectionRequest(
                taxYear = 2026,
                paidOn = currentDate,
                grossEligibleDividendKrw = 400_000_000L,
                isKrxKindEligibleCompany = true,
                electionRequested = true,
            ),
        )

        assertTrue(result.isApplied)
        assertEquals(400_000_000L, result.excludedFromFinancialIncomeThresholdKrw)
        assertEquals(92_180_000L, result.liability?.assessedTaxKrw) // national 83.8m + local 8.38m
        assertFalse(
            HighDividendElectionCalculator().calculate(
                HighDividendElectionRequest(
                    taxYear = 2030,
                    paidOn = LocalDate(2030, 1, 1),
                    grossEligibleDividendKrw = 10_000_000L,
                    isKrxKindEligibleCompany = true,
                    electionRequested = true,
                ),
            ).isApplied,
        )
    }

    @Test
    fun usRegulatoryFeesAreSeparateFromTaxAndUseCurrentRateAndCap() {
        val fees = BrokerFeeCalculator().calculate(
            BrokerFeeRequest(
                market = Market.NASDAQ,
                side = OrderSide.SELL,
                grossAmount = MoneyAmount(100_000_000L, Currency.USD), // USD 1,000,000.00
                quantity = 100_000.0,
                tradedOn = currentDate,
            ),
        )

        assertEquals(2_060L, fees.items.single { it.category == FeeCategory.SEC_SECTION_31 }.amount.minorUnits)
        assertEquals(979L, fees.items.single { it.category == FeeCategory.FINRA_TAF }.amount.minorUnits)
        assertEquals(3_039L, fees.totalFees.minorUnits)
    }

    private fun majorRequest(
        market: Market,
        ownership: Double,
        marketValueKrw: Long,
    ) = MajorShareholderAssessmentRequest(
        market = market,
        assessedOn = currentDate,
        priorBusinessYearEndHoldings = listOf(
            ShareholderHoldingSnapshot(
                ownerId = "player",
                relation = ShareholderRelation.SELF,
                ownershipRatio = ownership,
                marketValueKrw = marketValueKrw,
            ),
        ),
        isLargestShareholderGroup = false,
    )

    private fun foreignGain(id: String, date: LocalDate, amountKrw: Long) = RealizedStockGain(
        id = id,
        stockId = "NASDAQ:TEST",
        realizedOn = date,
        gainKrw = amountKrw,
        treatment = StockGainTaxTreatment.FOREIGN_STANDARD,
        instrumentTaxClass = ForeignInstrumentTaxClass.US_COMMON_STOCK,
    )
}
