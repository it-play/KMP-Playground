package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import kotlinx.datetime.LocalDate
import kotlin.math.floor

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
