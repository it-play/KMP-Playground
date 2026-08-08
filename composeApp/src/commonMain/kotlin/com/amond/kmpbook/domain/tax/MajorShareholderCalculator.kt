package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import kotlinx.datetime.LocalDate

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
