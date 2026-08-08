package com.amond.kmpbook.domain.model

/** 하나의 단순 경로가 특정 종목에 기여한 결과다. */
data class CausalImpactTrace(
    val contribution: Double,
    val confidence: Double,
    val nodes: List<CausalTraceNode>,
    val rationale: String,
    val exposureMechanism: CausalExposureMechanism,
    val marketTransmission: CausalMarketTransmissionTrace? = null,
) {
    init {
        require(contribution.isFinite() && contribution != 0.0)
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(nodes.size >= 2) { "인과 흔적에는 시작 요인과 최종 대상이 필요합니다." }
        require(rationale.isNotBlank()) { "인과 흔적 근거는 비어 있을 수 없습니다." }
    }

    val labels: List<String>
        get() = marketTransmission?.labels.orEmpty() + nodes.map(CausalTraceNode::displayLabel)
}
