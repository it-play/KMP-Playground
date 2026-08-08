package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

class DividendTaxCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: DividendTaxRequest): DividendTaxResult {
        policy.requireSimulationDate(request.paidOn)
        val breakdown = when (request.taxClass) {
            DividendTaxClass.KOREAN_ORDINARY_CASH,
            DividendTaxClass.KOREAN_ETF_DISTRIBUTION,
            -> calculateKorean(request)
            DividendTaxClass.US_ORDINARY_CORPORATION,
            DividendTaxClass.US_RIC_ETF_DISTRIBUTION,
            DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION,
            DividendTaxClass.US_REIT_DISTRIBUTION,
            DividendTaxClass.US_ETN_CONTINGENT_COUPON,
            DividendTaxClass.FOREIGN_ADR_DISTRIBUTION,
            -> calculateUnitedStates(request)
        }
        val netCash = request.grossAmount - breakdown.totalTax
        val grossIncomeKrw = floor(request.grossAmount.amount * request.taxExchangeRateToKrw).toLong()
        val assessment = FinancialIncomeEstimator(policy).assess(
            ordinaryFinancialIncomeGrossKrw = request.otherFinancialIncomeGrossKrw + grossIncomeKrw,
            electedHighDividendIncomeKrw = 0L,
            highDividendElectionApplied = false,
            otherIncomeInformationComplete = false,
        )
        return DividendTaxResult(
            breakdown = breakdown,
            netCash = netCash,
            grossIncomeKrw = grossIncomeKrw,
            financialIncomeAssessment = assessment,
        )
    }

    private fun calculateKorean(request: DividendTaxRequest): TaxBreakdown {
        val rule = policy.koreanDividendWithholding
        require(request.paidOn in rule.effectiveRange)
        val national = rule.nationalRate.apply(
            request.grossAmount.minorUnits,
            Currency.KRW,
            request.roundingPolicy,
        )
        val local = rule.localRate.apply(
            request.grossAmount.minorUnits,
            Currency.KRW,
            request.roundingPolicy,
        )
        return TaxBreakdown(
            policyId = policy.id,
            calculatedOn = request.paidOn,
            taxableBase = request.grossAmount,
            items = listOf(
                TaxLineItem(
                    id = "kr-dividend-withholding-national",
                    label = if (request.taxClass == DividendTaxClass.KOREAN_ETF_DISTRIBUTION) {
                        "국내 ETF 분배금 배당소득세 원천징수"
                    } else {
                        "국내 배당소득세 원천징수"
                    },
                    amount = national,
                    jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                    category = TaxCategory.DIVIDEND_WITHHOLDING,
                    source = rule.source,
                    effectiveRange = rule.effectiveRange,
                ),
                TaxLineItem(
                    id = "kr-dividend-withholding-local",
                    label = if (request.taxClass == DividendTaxClass.KOREAN_ETF_DISTRIBUTION) {
                        "국내 ETF 분배금 지방소득세 원천징수"
                    } else {
                        "국내 배당 지방소득세 원천징수"
                    },
                    amount = local,
                    jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                    category = TaxCategory.DIVIDEND_WITHHOLDING,
                    source = rule.source,
                    effectiveRange = rule.effectiveRange,
                ),
            ),
        )
    }

    private fun calculateUnitedStates(request: DividendTaxRequest): TaxBreakdown {
        val treatyRule = policy.usTreatyDividendWithholding
        require(request.paidOn in treatyRule.effectiveRange)
        val sourceRate = if (request.w8BenValid) TaxRate.PERCENT_15 else TaxRate.PERCENT_30
        val usTax = sourceRate.apply(
            request.grossAmount.minorUnits,
            Currency.USD,
            MoneyRoundingPolicy.MINOR_UNIT_HALF_UP,
        )
        val koreanOrdinaryRate = policy.koreanDividendWithholding.nationalRate
        val additionalKoreanRateNumerator =
            (koreanOrdinaryRate.fraction - sourceRate.fraction).coerceAtLeast(0.0)
        val additionalNational = request.roundingPolicy.roundMinorUnits(
            request.grossAmount.minorUnits * additionalKoreanRateNumerator,
            Currency.USD,
        )
        val additionalLocal = TaxRate.PERCENT_10.apply(
            additionalNational.minorUnits,
            Currency.USD,
            request.roundingPolicy,
        )
        val items = listOf(
            TaxLineItem(
                id = "us-dividend-withholding-federal",
                label = if (request.w8BenValid) {
                    when (request.taxClass) {
                        DividendTaxClass.US_RIC_ETF_DISTRIBUTION -> "미국 ETF 분배금 원천징수 (W-8BEN 조약세율)"
                        DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION -> "미국 폐쇄형펀드 분배금 원천징수(게임 RIC 가정)"
                        DividendTaxClass.US_REIT_DISTRIBUTION -> "미국 REIT 분배금 원천징수(게임 일반배당 가정)"
                        DividendTaxClass.US_ETN_CONTINGENT_COUPON -> "ETN 조건부 쿠폰 원천징수(게임 보수적 가정)"
                        DividendTaxClass.FOREIGN_ADR_DISTRIBUTION -> "ADR 배당 원천징수(본국 세율 단순화)"
                        else -> "미국 배당 원천징수 (W-8BEN 조약세율)"
                    }
                } else {
                    "미국 배당 원천징수 (W-8BEN 미적용)"
                },
                amount = usTax,
                jurisdiction = TaxJurisdiction.UNITED_STATES_FEDERAL,
                category = TaxCategory.DIVIDEND_WITHHOLDING,
                source = treatyRule.source,
                effectiveRange = treatyRule.effectiveRange,
            ),
            TaxLineItem(
                id = "kr-additional-foreign-dividend-national",
                label = "국외배당 국내 추가 원천징수",
                amount = additionalNational,
                jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                category = TaxCategory.DIVIDEND_WITHHOLDING,
                source = policy.koreanDividendWithholding.source,
                effectiveRange = policy.koreanDividendWithholding.effectiveRange,
            ),
            TaxLineItem(
                id = "kr-additional-foreign-dividend-local",
                label = "국외배당 국내 추가 지방소득세",
                amount = additionalLocal,
                jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                category = TaxCategory.DIVIDEND_WITHHOLDING,
                source = policy.koreanDividendWithholding.source,
                effectiveRange = policy.koreanDividendWithholding.effectiveRange,
            ),
        )
        return TaxBreakdown(
            policyId = policy.id,
            calculatedOn = request.paidOn,
            taxableBase = request.grossAmount,
            items = items,
            warnings = buildList {
                if (!request.w8BenValid) {
                    add("W-8BEN 미적용 시 일반적으로 30% 원천징수되므로 서류 상태를 확인해야 합니다.")
                }
                if (request.taxClass in setOf(
                        DividendTaxClass.US_RIC_ETF_DISTRIBUTION,
                        DividendTaxClass.US_RIC_CLOSED_END_DISTRIBUTION,
                    )
                ) {
                    add("게임은 미국 RIC ETF의 정기 현금분배를 일반 배당으로 분류합니다. 자본이득·원금환급 사후 재분류는 반영하지 않습니다.")
                } else if (request.taxClass == DividendTaxClass.US_ETN_CONTINGENT_COUPON) {
                    add("ETN 쿠폰의 실제 원천징수는 상품 약관·소득 성격에 따라 달라질 수 있어 15% 게임 가정으로 표시합니다.")
                } else if (request.taxClass == DividendTaxClass.FOREIGN_ADR_DISTRIBUTION) {
                    add("ADR 배당은 발행사 본국 조세조약·예탁수수료를 종목별로 확인해야 하며, 현재 게임은 15% 보수적 가정을 씁니다.")
                } else {
                    add("미국 ETF·REIT·PTP의 분배금 재분류는 이 일반법인 배당 계산에 포함되지 않습니다.")
                }
            },
        )
    }
}
