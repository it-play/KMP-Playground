package com.amond.kmpbook.domain.tax.domestic

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.core.TaxCategory
import com.amond.kmpbook.domain.tax.core.TaxJurisdiction
import com.amond.kmpbook.domain.tax.liability.TaxLiability
import com.amond.kmpbook.domain.tax.liability.TaxLiabilityStatus
import com.amond.kmpbook.domain.tax.liability.TaxLineItem
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack2026

class DomesticMajorCapitalGainsCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: DomesticMajorCapitalGainsRequest): TaxLiability {
        policy.requireSimulationDate(request.calculatedOn)
        val rule = policy.domesticMajorCapitalGains
        require(request.calculatedOn in rule.effectiveRange)

        val nationalTax = if (!request.isSmallOrMediumEnterprise && request.heldLessThanOneYear) {
            rule.nonSmeShortTermRate.apply(
                request.taxableBaseKrw,
                Currency.KRW,
                request.roundingPolicy,
            ).minorUnits
        } else {
            progressiveNationalTax(
                taxableBaseKrw = request.taxableBaseKrw,
                lowerBandUpperKrw = rule.upperRateStartsAboveKrw,
                lowerRate = rule.generalLowerRate,
                upperRate = rule.generalUpperRate,
                rounding = request.roundingPolicy,
            )
        }
        val localTax = rule.localIncomeTaxRateOnNationalTax.apply(
            nationalTax,
            Currency.KRW,
            request.roundingPolicy,
        ).minorUnits
        val effectiveRange = rule.effectiveRange
        val items = listOf(
            TaxLineItem(
                id = "kr-domestic-major-cgt-national",
                label = "국내 상장주식 대주주 양도소득세",
                amount = MoneyAmount(nationalTax, Currency.KRW),
                jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                category = TaxCategory.CAPITAL_GAINS,
                source = rule.source,
                effectiveRange = effectiveRange,
            ),
            TaxLineItem(
                id = "kr-domestic-major-cgt-local",
                label = "국내 상장주식 양도 지방소득세",
                amount = MoneyAmount(localTax, Currency.KRW),
                jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                category = TaxCategory.LOCAL_INCOME,
                source = rule.source,
                effectiveRange = effectiveRange,
            ),
        )

        return TaxLiability(
            id = "domestic-major-cgt-${request.taxYear}",
            label = "${request.taxYear}년 국내 대주주 주식 양도세",
            taxYear = request.taxYear,
            assessedTaxKrw = nationalTax + localTax,
            status = TaxLiabilityStatus.ESTIMATED,
            items = items,
            warnings = listOf("기본공제와 국내·국외 주식 손익통산이 끝난 과세표준을 입력해야 합니다."),
        )
    }
}
