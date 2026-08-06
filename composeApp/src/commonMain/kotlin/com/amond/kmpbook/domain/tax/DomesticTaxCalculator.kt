package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

data class DomesticSaleTaxRequest(
    val market: Market,
    val grossProceedsKrw: Long,
    val soldOn: LocalDate,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ) {
            "Immediate Korean transaction tax only supports KOSPI and KOSDAQ."
        }
        require(grossProceedsKrw >= 0L) { "Gross sale proceeds cannot be negative." }
    }
}

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

enum class ShareholderRelation {
    SELF,
    RELATIVE,
    CONTROLLED_ENTITY,
}

/** Snapshot values are per issuer, across every brokerage account. */
data class ShareholderHoldingSnapshot(
    val ownerId: String,
    val relation: ShareholderRelation,
    val ownershipRatio: Double,
    val marketValueKrw: Long,
) {
    init {
        require(ownerId.isNotBlank()) { "A shareholder snapshot needs an owner id." }
        require(ownershipRatio in 0.0..1.0) { "Ownership must be a ratio from zero to one." }
        require(marketValueKrw >= 0L) { "Market value cannot be negative." }
    }
}

data class MajorShareholderAssessmentRequest(
    val market: Market,
    val assessedOn: LocalDate,
    val priorBusinessYearEndHoldings: List<ShareholderHoldingSnapshot>,
    /** Related persons/entities are aggregated only when the taxpayer belongs to the largest group. */
    val isLargestShareholderGroup: Boolean,
    /** Ratio immediately after a post-year-end acquisition; null means no acquisition crossing test. */
    val ownershipRatioAfterCurrentYearAcquisition: Double? = null,
) {
    init {
        require(market == Market.KOSPI || market == Market.KOSDAQ) {
            "Major-shareholder assessment only supports KOSPI and KOSDAQ."
        }
        require(priorBusinessYearEndHoldings.any { it.relation == ShareholderRelation.SELF }) {
            "At least one SELF holding snapshot is required."
        }
        require(
            ownershipRatioAfterCurrentYearAcquisition == null ||
                ownershipRatioAfterCurrentYearAcquisition in 0.0..1.0,
        ) { "Post-acquisition ownership must be a ratio from zero to one." }
    }
}

data class MajorShareholderAssessment(
    val isMajorShareholder: Boolean,
    val market: Market,
    val assessedOwnershipRatio: Double,
    val assessedMarketValueKrw: Long,
    val ownershipThreshold: Double,
    val marketValueThresholdKrw: Long,
    val metByPriorYearEndOwnership: Boolean,
    val metByPriorYearEndMarketValue: Boolean,
    val metByCurrentYearAcquisition: Boolean,
    val source: RuleSource,
    val notes: List<String>,
)

class MajorShareholderCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun assess(request: MajorShareholderAssessmentRequest): MajorShareholderAssessment {
        policy.requireSimulationDate(request.assessedOn)
        val rule = requireNotNull(policy.majorShareholderThresholds[request.market])
        require(request.assessedOn in rule.effectiveRange) {
            "The major-shareholder rule is not effective on ${request.assessedOn}."
        }

        val included = if (request.isLargestShareholderGroup) {
            request.priorBusinessYearEndHoldings
        } else {
            request.priorBusinessYearEndHoldings.filter { it.relation == ShareholderRelation.SELF }
        }
        val priorRatio = included.sumOf { it.ownershipRatio }
        val priorMarketValue = included.sumOf { it.marketValueKrw }
        val byPriorRatio = priorRatio >= rule.minimumOwnershipRatio
        val byPriorMarketValue = priorMarketValue >= rule.minimumMarketValueKrw
        val byAcquisition = request.ownershipRatioAfterCurrentYearAcquisition
            ?.let { it >= rule.minimumOwnershipRatio }
            ?: false

        val notes = buildList {
            add("시가총액은 직전 사업연도 말 보유분으로 판정합니다.")
            if (request.isLargestShareholderGroup) {
                add("최대주주 그룹이므로 본인·친족·경영지배관계법인 보유분을 합산했습니다.")
            } else {
                add("최대주주 그룹이 아니므로 본인 보유분만 사용했습니다.")
            }
            if (request.ownershipRatioAfterCurrentYearAcquisition != null) {
                add("사업연도 중 취득 직후 지분율 기준을 별도로 판정했습니다.")
            }
        }

        return MajorShareholderAssessment(
            isMajorShareholder = byPriorRatio || byPriorMarketValue || byAcquisition,
            market = request.market,
            assessedOwnershipRatio = priorRatio,
            assessedMarketValueKrw = priorMarketValue,
            ownershipThreshold = rule.minimumOwnershipRatio,
            marketValueThresholdKrw = rule.minimumMarketValueKrw,
            metByPriorYearEndOwnership = byPriorRatio,
            metByPriorYearEndMarketValue = byPriorMarketValue,
            metByCurrentYearAcquisition = byAcquisition,
            source = rule.source,
            notes = notes,
        )
    }
}

enum class DomesticListedSaleTaxTreatment(val displayName: String) {
    EXEMPT_SMALL_SHAREHOLDER_ON_EXCHANGE("국내 장내 소액주주 비과세"),
    TAXABLE_MAJOR_SHAREHOLDER("국내 상장주식 대주주 과세"),
    TAXABLE_OFF_EXCHANGE("국내 상장주식 장외거래 과세"),
}

fun domesticListedSaleTaxTreatment(
    isOnExchange: Boolean,
    assessment: MajorShareholderAssessment,
): DomesticListedSaleTaxTreatment = when {
    assessment.isMajorShareholder -> DomesticListedSaleTaxTreatment.TAXABLE_MAJOR_SHAREHOLDER
    isOnExchange -> DomesticListedSaleTaxTreatment.EXEMPT_SMALL_SHAREHOLDER_ON_EXCHANGE
    else -> DomesticListedSaleTaxTreatment.TAXABLE_OFF_EXCHANGE
}

data class DomesticMajorCapitalGainsRequest(
    val taxYear: Int,
    /** Tax base after annual stock loss netting and the shared KRW 2.5m deduction. */
    val taxableBaseKrw: Long,
    val isSmallOrMediumEnterprise: Boolean,
    val heldLessThanOneYear: Boolean,
    val calculatedOn: LocalDate,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxYear >= 2026) { "The frozen policy starts in 2026." }
        require(taxableBaseKrw >= 0L) { "Taxable capital gain cannot be negative." }
    }
}

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

internal fun progressiveNationalTax(
    taxableBaseKrw: Long,
    lowerBandUpperKrw: Long,
    lowerRate: TaxRate,
    upperRate: TaxRate,
    rounding: MoneyRoundingPolicy,
): Long {
    val lowerBase = minOf(taxableBaseKrw, lowerBandUpperKrw)
    val upperBase = (taxableBaseKrw - lowerBandUpperKrw).coerceAtLeast(0L)
    return lowerRate.apply(lowerBase, Currency.KRW, rounding).minorUnits +
        upperRate.apply(upperBase, Currency.KRW, rounding).minorUnits
}
