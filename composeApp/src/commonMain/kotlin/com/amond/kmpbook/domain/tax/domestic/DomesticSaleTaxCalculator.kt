package com.amond.kmpbook.domain.tax.domestic

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.tax.core.MoneyAmount
import com.amond.kmpbook.domain.tax.core.TaxCategory
import com.amond.kmpbook.domain.tax.core.TaxJurisdiction
import com.amond.kmpbook.domain.tax.liability.TaxBreakdown
import com.amond.kmpbook.domain.tax.liability.TaxLineItem
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack
import com.amond.kmpbook.domain.tax.policy.TaxPolicyPack2026

class DomesticSaleTaxCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: DomesticSaleTaxRequest): TaxBreakdown {
        policy.requireSimulationDate(request.soldOn)
        val rule = requireNotNull(policy.domesticTransactionTaxes[request.market]) {
            "No transaction-tax rule is configured for ${request.market}."
        }
        require(request.soldOn in rule.effectiveRange) {
            "The ${request.market} transaction-tax rule is not effective on ${request.soldOn}."
        }

        val base = MoneyAmount(request.grossProceedsKrw, Currency.KRW)
        val transactionTax = rule.securitiesTransactionTaxRate.apply(
            baseMinorUnits = request.grossProceedsKrw,
            currency = Currency.KRW,
            rounding = request.roundingPolicy,
        )
        val items = buildList {
            add(
                TaxLineItem(
                    id = "kr-stt-${request.market.name.lowercase()}",
                    label = "${request.market.displayName} 증권거래세",
                    amount = transactionTax,
                    jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                    category = TaxCategory.SECURITIES_TRANSACTION,
                    source = rule.transactionTaxSource,
                    effectiveRange = rule.effectiveRange,
                ),
            )
            if (rule.specialRuralTaxRate.numerator > 0L) {
                add(
                    TaxLineItem(
                        id = "kr-special-rural-${request.market.name.lowercase()}",
                        label = "${request.market.displayName} 농어촌특별세",
                        amount = rule.specialRuralTaxRate.apply(
                            baseMinorUnits = request.grossProceedsKrw,
                            currency = Currency.KRW,
                            rounding = request.roundingPolicy,
                        ),
                        jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                        category = TaxCategory.SPECIAL_RURAL,
                        source = requireNotNull(rule.specialRuralTaxSource),
                        effectiveRange = rule.effectiveRange,
                    ),
                )
            }
        }

        return TaxBreakdown(
            policyId = policy.id,
            calculatedOn = request.soldOn,
            taxableBase = base,
            items = items,
        )
    }
}
