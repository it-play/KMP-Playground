package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 실제 지수와 시뮬레이션 산식을 분리해 표시하는 메타데이터다.
 *
 * 상용 지수의 전체 구성종목·유동주식수·옵션 호가가 없으므로 [isSimulationProxy]는 항상
 * true다. [officialMethodologySummary]는 비교 기준이고 [simulationFormula]만 게임 엔진에서 실행된다.
 */
data class MarketIndexFormulaMetadata(
    val id: MarketIndexId,
    val unit: MarketIndexUnit,
    val formulaKind: MarketIndexFormulaKind,
    val initialValue: Double,
    val officialMethodologySummary: String,
    val officialMethodologyUrl: String,
    val simulationFormula: String,
    val constituentRule: String,
    val isSimulationProxy: Boolean = true,
    val constituentSnapshotDate: String? = null,
) {
    init {
        require(initialValue > 0.0 && initialValue.isFinite()) { "지수 시작값은 양수여야 합니다." }
        require(officialMethodologySummary.isNotBlank()) { "공식 산식 설명은 비어 있을 수 없습니다." }
        require(officialMethodologyUrl.startsWith("https://")) { "공식 방법론 URL은 HTTPS여야 합니다." }
        require(simulationFormula.isNotBlank()) { "시뮬레이션 산식은 비어 있을 수 없습니다." }
        require(constituentRule.isNotBlank()) { "지수 편입 규칙은 비어 있을 수 없습니다." }
        require(isSimulationProxy) { "현재 데이터로는 공식 상용 지수를 생성할 수 없습니다." }
    }
}
