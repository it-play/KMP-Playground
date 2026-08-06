package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

enum class DividendTaxClass(val displayName: String) {
    KOREAN_ORDINARY_CASH("국내 일반 현금배당"),
    US_ORDINARY_CORPORATION("미국 일반법인 현금배당"),
}

data class DividendTaxRequest(
    val taxClass: DividendTaxClass,
    val grossAmount: MoneyAmount,
    val paidOn: LocalDate,
    /** Won per one unit of the dividend currency. KRW must use 1.0. */
    val taxExchangeRateToKrw: Double = 1.0,
    val w8BenValid: Boolean = true,
    val otherFinancialIncomeGrossKrw: Long = 0L,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(grossAmount.minorUnits >= 0L) { "A gross dividend cannot be negative." }
        require(taxExchangeRateToKrw > 0.0 && taxExchangeRateToKrw.isFinite()) {
            "A positive tax exchange rate is required."
        }
        require(otherFinancialIncomeGrossKrw >= 0L) { "Other financial income cannot be negative." }
        require(
            (taxClass == DividendTaxClass.KOREAN_ORDINARY_CASH && grossAmount.currency == Currency.KRW) ||
                (taxClass == DividendTaxClass.US_ORDINARY_CORPORATION && grossAmount.currency == Currency.USD),
        ) { "The dividend currency does not match its tax class." }
        require(grossAmount.currency != Currency.KRW || taxExchangeRateToKrw == 1.0) {
            "KRW dividend income must use a 1.0 tax exchange rate."
        }
    }
}

data class FinancialIncomeAssessment(
    val ordinaryFinancialIncomeGrossKrw: Long,
    val electedHighDividendIncomeKrw: Long,
    val amountCountedForThresholdKrw: Long,
    val thresholdKrw: Long,
    val exceedsComprehensiveThreshold: Boolean,
    val isEstimate: Boolean,
    val warnings: List<String>,
)

data class DividendTaxResult(
    val breakdown: TaxBreakdown,
    val netCash: MoneyAmount,
    val grossIncomeKrw: Long,
    val financialIncomeAssessment: FinancialIncomeAssessment,
)

class DividendTaxCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: DividendTaxRequest): DividendTaxResult {
        policy.requireSimulationDate(request.paidOn)
        val breakdown = when (request.taxClass) {
            DividendTaxClass.KOREAN_ORDINARY_CASH -> calculateKorean(request)
            DividendTaxClass.US_ORDINARY_CORPORATION -> calculateUnitedStates(request)
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
                    label = "국내 배당소득세 원천징수",
                    amount = national,
                    jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                    category = TaxCategory.DIVIDEND_WITHHOLDING,
                    source = rule.source,
                    effectiveRange = rule.effectiveRange,
                ),
                TaxLineItem(
                    id = "kr-dividend-withholding-local",
                    label = "국내 배당 지방소득세 원천징수",
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
                    "미국 배당 원천징수 (W-8BEN 조약세율)"
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
                add("미국 ETF·REIT·PTP의 분배금 재분류는 이 일반법인 배당 계산에 포함되지 않습니다.")
            },
        )
    }
}

class FinancialIncomeEstimator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun assess(
        ordinaryFinancialIncomeGrossKrw: Long,
        electedHighDividendIncomeKrw: Long,
        highDividendElectionApplied: Boolean,
        otherIncomeInformationComplete: Boolean,
    ): FinancialIncomeAssessment {
        require(ordinaryFinancialIncomeGrossKrw >= 0L && electedHighDividendIncomeKrw >= 0L) {
            "Financial income cannot be negative."
        }
        val counted = ordinaryFinancialIncomeGrossKrw +
            if (highDividendElectionApplied) 0L else electedHighDividendIncomeKrw
        val exceeds = counted > policy.financialIncomeComprehensiveThresholdKrw
        return FinancialIncomeAssessment(
            ordinaryFinancialIncomeGrossKrw = ordinaryFinancialIncomeGrossKrw,
            electedHighDividendIncomeKrw = electedHighDividendIncomeKrw,
            amountCountedForThresholdKrw = counted,
            thresholdKrw = policy.financialIncomeComprehensiveThresholdKrw,
            exceedsComprehensiveThreshold = exceeds,
            isEstimate = exceeds && !otherIncomeInformationComplete,
            warnings = buildList {
                if (exceeds) {
                    add("금융소득 종합과세 기준 2,000만원을 초과했습니다.")
                }
                if (exceeds && !otherIncomeInformationComplete) {
                    add("근로·사업소득과 공제 정보가 없어 종합소득세는 추정만 가능합니다.")
                }
                if (highDividendElectionApplied) {
                    add("선택한 고배당 특례소득은 2,000만원 기준 판정에서 제외했습니다.")
                }
            },
        )
    }
}

data class HighDividendElectionRequest(
    val taxYear: Int,
    val paidOn: LocalDate,
    val grossEligibleDividendKrw: Long,
    /** Consume the official KRX KIND eligibility flag instead of inferring it from yield. */
    val isKrxKindEligibleCompany: Boolean,
    val electionRequested: Boolean,
    val withholdingCreditsKrw: Long = 0L,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxYear == paidOn.year) { "taxYear must match the dividend payment year." }
        require(grossEligibleDividendKrw >= 0L && withholdingCreditsKrw >= 0L) {
            "Dividend and credits cannot be negative."
        }
    }
}

data class HighDividendElectionResult(
    val isApplied: Boolean,
    val excludedFromFinancialIncomeThresholdKrw: Long,
    val liability: TaxLiability?,
    val reasons: List<String>,
)

class HighDividendElectionCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: HighDividendElectionRequest): HighDividendElectionResult {
        policy.requireSimulationDate(request.paidOn)
        val rule = policy.highDividendSeparateTax
        val withinWindow = request.paidOn in rule.paymentDateRange
        val applied = request.electionRequested && request.isKrxKindEligibleCompany && withinWindow
        if (!applied) {
            return HighDividendElectionResult(
                isApplied = false,
                excludedFromFinancialIncomeThresholdKrw = 0L,
                liability = null,
                reasons = buildList {
                    if (!request.electionRequested) add("납세자가 고배당 분리과세를 선택하지 않았습니다.")
                    if (!request.isKrxKindEligibleCompany) add("KRX KIND 고배당기업 확인 대상이 아닙니다.")
                    if (!withinWindow) add("배당 지급일이 2026~2029년 한시 적용기간 밖입니다.")
                },
            )
        }

        val national = calculateProgressiveTax(
            baseKrw = request.grossEligibleDividendKrw,
            brackets = rule.brackets,
            rounding = request.roundingPolicy,
        )
        val local = rule.localIncomeTaxRateOnNationalTax.apply(
            national,
            Currency.KRW,
            request.roundingPolicy,
        ).minorUnits
        val items = listOf(
            TaxLineItem(
                id = "high-dividend-national-${request.taxYear}",
                label = "고배당기업 배당 분리과세",
                amount = MoneyAmount(national, Currency.KRW),
                jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                category = TaxCategory.HIGH_DIVIDEND_SEPARATE,
                source = rule.source,
                effectiveRange = rule.paymentDateRange,
            ),
            TaxLineItem(
                id = "high-dividend-local-${request.taxYear}",
                label = "고배당기업 배당 지방소득세",
                amount = MoneyAmount(local, Currency.KRW),
                jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                category = TaxCategory.LOCAL_INCOME,
                source = rule.source,
                effectiveRange = rule.paymentDateRange,
            ),
        )
        val liability = TaxLiability(
            id = "high-dividend-election-${request.taxYear}",
            label = "${request.taxYear}년 고배당기업 배당 분리과세",
            taxYear = request.taxYear,
            assessedTaxKrw = national + local,
            withholdingCreditsKrw = request.withholdingCreditsKrw,
            dueDate = LocalDate(request.taxYear + 1, 5, 31),
            status = if (request.withholdingCreditsKrw > national + local) {
                TaxLiabilityStatus.REFUNDABLE
            } else {
                TaxLiabilityStatus.DUE
            },
            items = items,
            warnings = listOf("고배당 분리과세는 자동 적용이 아니며 다음 해 5월 신고 시 신청해야 합니다."),
        )
        return HighDividendElectionResult(
            isApplied = true,
            excludedFromFinancialIncomeThresholdKrw = request.grossEligibleDividendKrw,
            liability = liability,
            reasons = listOf("KRX KIND 적격 플래그와 납세자 선택을 모두 확인했습니다."),
        )
    }

    private fun calculateProgressiveTax(
        baseKrw: Long,
        brackets: List<ProgressiveTaxBracket>,
        rounding: MoneyRoundingPolicy,
    ): Long {
        require(brackets.isNotEmpty() && brackets.last().upperBoundKrw == null) {
            "Progressive brackets need an unbounded final band."
        }
        var lower = 0L
        var remaining = baseKrw
        var exactTax = 0.0
        brackets.forEach { bracket ->
            if (remaining <= 0L) return@forEach
            val bandCapacity = bracket.upperBoundKrw?.minus(lower) ?: remaining
            val bandBase = minOf(remaining, bandCapacity)
            exactTax += bandBase * bracket.rate.fraction
            remaining -= bandBase
            lower = bracket.upperBoundKrw ?: lower
        }
        return rounding.roundMinorUnits(exactTax, Currency.KRW).minorUnits
    }
}
