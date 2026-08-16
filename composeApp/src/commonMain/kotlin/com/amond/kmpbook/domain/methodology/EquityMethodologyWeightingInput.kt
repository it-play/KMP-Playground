package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import kotlinx.datetime.LocalDate

/** Bounded input for target-weight construction after selection or a permanent replacement. */
class EquityMethodologyWeightingInput(
    val profile: EquityMethodologyProfile,
    val actionKind: ReferencePortfolioActionKind,
    val observationDate: LocalDate,
    val effectiveDate: LocalDate,
    selectedCandidates: List<EquityMethodologyCandidate>,
    referenceMarketValues: Map<String, Double>,
) {
    val selectedCandidates: List<EquityMethodologyCandidate> = buildList {
        addAll(selectedCandidates)
    }
    val referenceMarketValues: Map<String, Double> = buildMap {
        putAll(referenceMarketValues.toSortedMap())
    }

    init {
        require(
            actionKind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION ||
                actionKind == ReferencePortfolioActionKind.SCHEDULED_REWEIGHT ||
                actionKind == ReferencePortfolioActionKind.CONSTRAINT_REWEIGHT ||
                actionKind == ReferencePortfolioActionKind.CONSTITUENT_MERGER ||
                actionKind == ReferencePortfolioActionKind.TERMINAL_REMOVAL,
        ) { "대상 비중을 생성할 수 없는 참조 포트폴리오 액션입니다." }
        require(observationDate < effectiveDate) {
            "비중 관측일은 액션 효력일보다 빨라야 합니다."
        }
        require(effectiveDate >= profile.effectiveFrom) {
            "비중 액션은 방법론 효력 시작일보다 빠를 수 없습니다."
        }
        require(selectedCandidates.isNotEmpty())
        require(selectedCandidates.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        val ids = selectedCandidates.map(EquityMethodologyCandidate::assetId)
        require(ids.distinct().size == ids.size)
        require(referenceMarketValues.keys == ids.toSet())
        require(referenceMarketValues.values.all { it.isFinite() && it > 0.0 })
    }
}
