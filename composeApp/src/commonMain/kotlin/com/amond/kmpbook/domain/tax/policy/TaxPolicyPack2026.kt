package com.amond.kmpbook.domain.tax.policy

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.ProgressiveTaxBracket
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxRate
import com.amond.kmpbook.domain.tax.dividend.DividendWithholdingRule
import com.amond.kmpbook.domain.tax.dividend.HighDividendSeparateTaxRule
import com.amond.kmpbook.domain.tax.domestic.DomesticMajorShareholderCapitalGainsRule
import com.amond.kmpbook.domain.tax.domestic.DomesticTransactionTaxRule
import com.amond.kmpbook.domain.tax.foreign.ForeignStockCapitalGainsRule
import com.amond.kmpbook.domain.tax.shareholder.MajorShareholderThresholdRule
import kotlinx.datetime.LocalDate

/**
 * Korean resident individual, ordinary brokerage account, law frozen at campaign start 2026-08-01.
 * Rules without a statutory sunset are intentionally held constant through 2040 for gameplay;
 * that is a simulation assumption, not a statement that future law will remain unchanged.
 */
object TaxPolicyPack2026 {
    val FROZEN_AS_OF: LocalDate = LocalDate(2026, 8, 1)

    val SECURITIES_TRANSACTION_TAX_SOURCE = RuleSource(
        title = "증권거래세법 시행령 제5조 (2026 시행)",
        url = "https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0005&lsiSeq=280901&urlMode=lsScJoRltInfoR",
    )
    val SPECIAL_RURAL_TAX_SOURCE = RuleSource(
        title = "농어촌특별세법 제5조",
        url = "https://www.law.go.kr/LSW/lsLinkCommonInfo.do?chrClsCd=010202&lsJoLnkSeq=1032879999",
    )
    val MAJOR_SHAREHOLDER_SOURCE = RuleSource(
        title = "국세청 2026년 주식 양도소득세 예정신고 안내",
        url = "https://b.nts.go.kr/jongno/na/ntt/selectNttInfo.do?bbsId=1028&mi=2201&nttSn=1348384",
    )
    val CAPITAL_GAINS_SOURCE = RuleSource(
        title = "국세청 양도소득세 세액계산 흐름",
        url = "https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=7709&mi=2314",
    )
    val OVERSEAS_STOCK_SOURCE = RuleSource(
        title = "국세청 해외주식과 세금 안내",
        url = "https://taxlaw.nts.go.kr/downloadPDFFile.do?fleId=300000000001047678&fleSn=923559",
    )
    val FINANCIAL_INCOME_SOURCE = RuleSource(
        title = "국세청 금융(이자·배당)소득 안내",
        url = "https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=7914&mi=40359",
    )
    val US_TREATY_SOURCE = RuleSource(
        title = "대한민국-미국 조세조약 제12조",
        url = "https://www.irs.gov/pub/irs-trty/korea.pdf",
    )
    val HIGH_DIVIDEND_SOURCE = RuleSource(
        title = "조세특례제한법 제104조의27 및 국세청 고배당 분리과세 안내",
        url = "https://www.law.go.kr/LSW/lsLinkCommonInfo.do?lsJoLnkSeq=1032247693",
    )

    private val regular2026Range = EffectiveDateRange(LocalDate(2026, 1, 1))
    private const val MAJOR_SHAREHOLDER_MARKET_VALUE_KRW = 5_000_000_000L

    val POLICY: TaxPolicyPack = TaxPolicyPack(
        id = "kr-resident-ordinary-account-2026-08-01-frozen",
        title = "대한민국 거주 개인 일반계좌 세법 2026-08-01 동결",
        frozenAsOf = FROZEN_AS_OF,
        frozenScenarioRange = EffectiveDateRange(
            validFrom = LocalDate(2026, 1, 1),
            validThrough = LocalDate(2040, 12, 31),
        ),
        domesticTransactionTaxes = mapOf(
            Market.KOSPI to DomesticTransactionTaxRule(
                market = Market.KOSPI,
                securitiesTransactionTaxRate = TaxRate(500L),
                specialRuralTaxRate = TaxRate(1_500L),
                effectiveRange = regular2026Range,
                transactionTaxSource = SECURITIES_TRANSACTION_TAX_SOURCE,
                specialRuralTaxSource = SPECIAL_RURAL_TAX_SOURCE,
            ),
            Market.KOSDAQ to DomesticTransactionTaxRule(
                market = Market.KOSDAQ,
                securitiesTransactionTaxRate = TaxRate(2_000L),
                specialRuralTaxRate = TaxRate.ZERO,
                effectiveRange = regular2026Range,
                transactionTaxSource = SECURITIES_TRANSACTION_TAX_SOURCE,
                specialRuralTaxSource = null,
            ),
        ),
        majorShareholderThresholds = mapOf(
            Market.KOSPI to MajorShareholderThresholdRule(
                market = Market.KOSPI,
                minimumOwnershipRatio = 0.01,
                minimumMarketValueKrw = MAJOR_SHAREHOLDER_MARKET_VALUE_KRW,
                effectiveRange = regular2026Range,
                source = MAJOR_SHAREHOLDER_SOURCE,
            ),
            Market.KOSDAQ to MajorShareholderThresholdRule(
                market = Market.KOSDAQ,
                minimumOwnershipRatio = 0.02,
                minimumMarketValueKrw = MAJOR_SHAREHOLDER_MARKET_VALUE_KRW,
                effectiveRange = regular2026Range,
                source = MAJOR_SHAREHOLDER_SOURCE,
            ),
        ),
        domesticMajorCapitalGains = DomesticMajorShareholderCapitalGainsRule(
            generalLowerRate = TaxRate.PERCENT_20,
            generalUpperRate = TaxRate.PERCENT_25,
            upperRateStartsAboveKrw = 300_000_000L,
            nonSmeShortTermRate = TaxRate.PERCENT_30,
            localIncomeTaxRateOnNationalTax = TaxRate.PERCENT_10,
            effectiveRange = regular2026Range,
            source = CAPITAL_GAINS_SOURCE,
        ),
        foreignStockCapitalGains = ForeignStockCapitalGainsRule(
            nationalRate = TaxRate.PERCENT_20,
            localRate = TaxRate.PERCENT_2,
            annualBasicDeductionKrw = 2_500_000L,
            lossCarryForwardYears = 0,
            effectiveRange = regular2026Range,
            source = OVERSEAS_STOCK_SOURCE,
        ),
        koreanDividendWithholding = DividendWithholdingRule(
            nationalRate = TaxRate.PERCENT_14,
            localRate = TaxRate.PERCENT_1_4,
            effectiveRange = regular2026Range,
            source = FINANCIAL_INCOME_SOURCE,
        ),
        usTreatyDividendWithholding = DividendWithholdingRule(
            nationalRate = TaxRate.PERCENT_15,
            localRate = TaxRate.ZERO,
            effectiveRange = regular2026Range,
            source = US_TREATY_SOURCE,
        ),
        financialIncomeComprehensiveThresholdKrw = 20_000_000L,
        highDividendSeparateTax = HighDividendSeparateTaxRule(
            paymentDateRange = EffectiveDateRange(
                validFrom = LocalDate(2026, 1, 1),
                validThrough = LocalDate(2029, 12, 31),
            ),
            brackets = listOf(
                ProgressiveTaxBracket(20_000_000L, TaxRate.PERCENT_14),
                ProgressiveTaxBracket(300_000_000L, TaxRate.PERCENT_20),
                ProgressiveTaxBracket(5_000_000_000L, TaxRate.PERCENT_25),
                ProgressiveTaxBracket(null, TaxRate.PERCENT_30),
            ),
            localIncomeTaxRateOnNationalTax = TaxRate.PERCENT_10,
            source = HIGH_DIVIDEND_SOURCE,
        ),
        sources = listOf(
            SECURITIES_TRANSACTION_TAX_SOURCE,
            SPECIAL_RURAL_TAX_SOURCE,
            MAJOR_SHAREHOLDER_SOURCE,
            CAPITAL_GAINS_SOURCE,
            OVERSEAS_STOCK_SOURCE,
            FINANCIAL_INCOME_SOURCE,
            US_TREATY_SOURCE,
            HIGH_DIVIDEND_SOURCE,
        ),
    )
}
