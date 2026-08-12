package com.amond.kmpbook.domain.tax.liability

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.CheckedMonetaryArithmetic
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.core.RuleSource
import com.amond.kmpbook.domain.tax.core.TaxCategory
import com.amond.kmpbook.domain.tax.core.TaxJurisdiction
import com.amond.kmpbook.domain.tax.domestic.DomesticMajorCapitalGainsCalculator
import com.amond.kmpbook.domain.tax.domestic.DomesticMajorCapitalGainsRequest
import com.amond.kmpbook.domain.tax.lot.RealizedStockGain
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack2026
import kotlinx.datetime.LocalDate

class AnnualStockTaxCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: AnnualStockTaxRequest): AnnualTaxLedger {
        val closingDate = LocalDate(request.taxYear, 12, 31)
        policy.requireSimulationDate(closingDate)
        val taxableEntries = request.gains
            .filter {
                it.treatment != StockGainTaxTreatment.DOMESTIC_EXEMPT_SMALL_ON_EXCHANGE &&
                    it.treatment != StockGainTaxTreatment.DOMESTIC_ETF_HOLDING_PERIOD_WITHHELD
            }
            .sortedBy { it.realizedOn }

        val domesticGain = CheckedMonetaryArithmetic.sum(
            taxableEntries.asSequence()
                .filter { it.treatment != StockGainTaxTreatment.FOREIGN_STANDARD }
                .map { it.gainKrw },
            "Domestic realized gains",
        )
        val foreignGain = CheckedMonetaryArithmetic.sum(
            taxableEntries.asSequence()
                .filter { it.treatment == StockGainTaxTreatment.FOREIGN_STANDARD }
                .map { it.gainKrw },
            "Foreign realized gains",
        )
        val netGain = CheckedMonetaryArithmetic.add(
            domesticGain,
            foreignGain,
            "Net realized gains",
        )
        val positiveNet = netGain.coerceAtLeast(0L)
        val deduction = minOf(positiveNet, policy.foreignStockCapitalGains.annualBasicDeductionKrw)
        val taxableBase = positiveNet - deduction
        require(netGain != Long.MIN_VALUE) { "Expired stock loss exceeds the monetary range" }
        val expiredLoss = (-netGain).coerceAtLeast(0L)

        val allocatedBases = allocateTaxableBaseByTreatment(
            entries = taxableEntries,
            finalTaxableBaseKrw = taxableBase,
        )
        val liabilities = buildList {
            allocatedBases[StockGainTaxTreatment.FOREIGN_STANDARD]
                ?.takeIf { it > 0L }
                ?.let { add(foreignStockLiability(request, it)) }
            allocatedBases[StockGainTaxTreatment.DOMESTIC_MAJOR_GENERAL]
                ?.takeIf { it > 0L }
                ?.let { add(domesticMajorLiability(request, it, shortTerm = false)) }
            allocatedBases[StockGainTaxTreatment.DOMESTIC_MAJOR_NON_SME_SHORT_TERM]
                ?.takeIf { it > 0L }
                ?.let { add(domesticMajorLiability(request, it, shortTerm = true)) }
        }

        val crossRateWarning = taxableEntries.map { it.treatment }.distinct().size > 1 &&
            taxableEntries.any { it.gainKrw < 0L }
        val warnings = buildList {
            if (expiredLoss > 0L) {
                add("주식 양도차손 ${expiredLoss}원은 현재 정책에서 다음 연도로 이월되지 않습니다.")
            }
            if (crossRateWarning) {
                add("서로 다른 세율의 주식 손익이 섞여 있어 신고서의 법정 통산 순서를 확인해야 합니다.")
            }
            if (request.financialIncomeGrossKrw > policy.financialIncomeComprehensiveThresholdKrw) {
                add("금융소득이 2,000만원을 초과했습니다. 다른 종합소득 정보가 없어 종합과세는 추정입니다.")
            }
            add("2026-08-07 세법 동결 시나리오이며 2027년 이후 실제 개정은 자동 반영되지 않습니다.")
        }

        return AnnualTaxLedger(
            taxYear = request.taxYear,
            policyId = policy.id,
            taxableDomesticGainKrw = domesticGain,
            foreignGainKrw = foreignGain,
            currentYearNetStockGainKrw = netGain,
            sharedStockBasicDeductionKrw = deduction,
            stockTaxableBaseKrw = taxableBase,
            expiredStockLossKrw = expiredLoss,
            financialIncomeGrossKrw = request.financialIncomeGrossKrw,
            highDividendIncomeKrw = request.highDividendIncomeKrw,
            foreignTaxPaidKrw = request.foreignTaxPaidKrw,
            withholdingCreditsKrw = request.withholdingCreditsKrw,
            liabilities = liabilities,
            warnings = warnings,
        )
    }

    private fun foreignStockLiability(request: AnnualStockTaxRequest, baseKrw: Long): TaxLiability {
        val rule = policy.foreignStockCapitalGains
        val national = rule.nationalRate.apply(baseKrw, Currency.KRW, request.roundingPolicy).minorUnits
        val local = rule.localRate.apply(baseKrw, Currency.KRW, request.roundingPolicy).minorUnits
        val items = listOf(
            annualCapitalGainLine(
                id = "foreign-stock-cgt-national-${request.taxYear}",
                label = "국외주식 양도소득세",
                amountKrw = national,
                jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                category = TaxCategory.CAPITAL_GAINS,
                source = rule.source,
                range = rule.effectiveRange,
            ),
            annualCapitalGainLine(
                id = "foreign-stock-cgt-local-${request.taxYear}",
                label = "국외주식 양도 지방소득세",
                amountKrw = local,
                jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                category = TaxCategory.LOCAL_INCOME,
                source = rule.source,
                range = rule.effectiveRange,
            ),
        )
        return TaxLiability(
            id = "foreign-stock-cgt-${request.taxYear}",
            label = "${request.taxYear}년 국외주식 양도세",
            taxYear = request.taxYear,
            assessedTaxKrw = CheckedMonetaryArithmetic.add(
                national,
                local,
                "Foreign stock capital-gains tax",
            ),
            dueDate = LocalDate(request.taxYear + 1, 5, 31),
            status = TaxLiabilityStatus.DUE,
            items = items,
            warnings = listOf("법정 신고일이 휴일이면 다음 영업일 조정이 필요합니다."),
        )
    }

    private fun domesticMajorLiability(
        request: AnnualStockTaxRequest,
        baseKrw: Long,
        shortTerm: Boolean,
    ): TaxLiability {
        return DomesticMajorCapitalGainsCalculator(policy).calculate(
            DomesticMajorCapitalGainsRequest(
                taxYear = request.taxYear,
                taxableBaseKrw = baseKrw,
                isSmallOrMediumEnterprise = !shortTerm,
                heldLessThanOneYear = shortTerm,
                calculatedOn = LocalDate(request.taxYear, 12, 31),
                roundingPolicy = request.roundingPolicy,
            ),
        )
    }

    /**
     * Same-year losses and the one shared deduction are applied chronologically. This preserves the
     * statutory "earlier transfer first" basis-deduction rule. Mixed-rate loss cases remain flagged.
     */
    private fun allocateTaxableBaseByTreatment(
        entries: List<RealizedStockGain>,
        finalTaxableBaseKrw: Long,
    ): Map<StockGainTaxTreatment, Long> {
        if (finalTaxableBaseKrw == 0L) return emptyMap()
        val positiveByTreatment = entries
            .groupBy { it.treatment }
            .mapValues { (_, values) ->
                CheckedMonetaryArithmetic.sum(
                    values.asSequence().map { it.gainKrw },
                    "Taxable gains by treatment",
                ).coerceAtLeast(0L)
            }
            .filterValues { it > 0L }
        if (positiveByTreatment.isEmpty()) return emptyMap()

        val earliest = entries
            .filter { it.gainKrw > 0L }
            .groupBy { it.treatment }
            .mapValues { (_, values) -> values.minOf { it.realizedOn } }
        var remaining = finalTaxableBaseKrw
        val result = linkedMapOf<StockGainTaxTreatment, Long>()
        positiveByTreatment.keys.sortedBy { earliest.getValue(it) }.forEach { treatment ->
            val assigned = minOf(positiveByTreatment.getValue(treatment), remaining)
            if (assigned > 0L) result[treatment] = assigned
            remaining -= assigned
        }
        require(remaining == 0L) { "Taxable-base allocation failed by $remaining won." }
        return result
    }

    private fun annualCapitalGainLine(
        id: String,
        label: String,
        amountKrw: Long,
        jurisdiction: TaxJurisdiction,
        category: TaxCategory,
        source: RuleSource,
        range: EffectiveDateRange,
    ) = TaxLineItem(
        id = id,
        label = label,
        amount = MoneyAmount(amountKrw, Currency.KRW),
        jurisdiction = jurisdiction,
        category = category,
        source = source,
        effectiveRange = range,
    )
}
