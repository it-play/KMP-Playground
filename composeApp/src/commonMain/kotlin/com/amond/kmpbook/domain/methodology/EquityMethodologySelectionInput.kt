package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.EquityMethodologyProfile
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioActionKind
import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits

/** Bounded input for a methodology's constituent selection phase. */
class EquityMethodologySelectionInput(
    val profile: EquityMethodologyProfile,
    val scheduledAction: EquityMethodologyScheduledAction,
    candidates: List<EquityMethodologyCandidate>,
    incumbentAssetIds: Set<String>,
) {
    val candidates: List<EquityMethodologyCandidate> = buildList { addAll(candidates) }
    val incumbentAssetIds: Set<String> = buildSet { addAll(incumbentAssetIds.sorted()) }

    init {
        require(scheduledAction.kind == ReferencePortfolioActionKind.SCHEDULED_RECONSTITUTION) {
            "종목 선정은 예정된 정기 구성 변경 액션에서만 수행할 수 있습니다."
        }
        require(scheduledAction.effectiveDate >= profile.effectiveFrom) {
            "종목 선정 액션은 방법론 효력 시작일보다 빠를 수 없습니다."
        }
        require(candidates.isNotEmpty())
        require(candidates.size <= MAX_CANDIDATES)
        require(candidates.map(EquityMethodologyCandidate::assetId).distinct().size == candidates.size)
        require(incumbentAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(incumbentAssetIds.all { incumbent -> candidates.any { it.assetId == incumbent } })
    }

    companion object {
        const val MAX_CANDIDATES: Int = 2_600
    }
}
