package com.amond.kmpbook.domain.model

/** 한 종목에 도착한 모든 경로를 합성한 순수 전파 결과다. */
data class CausalStockImpact(
    val stockId: String,
    val direction: ImpactDirection,
    /** 부호를 포함한 합성 민감도. 절댓값은 엔진의 상한을 넘지 않는다. */
    val signedSensitivity: Double,
    val relativeSensitivity: Double,
    val confidence: Double,
    val specificity: Int,
    val traces: List<CausalImpactTrace>,
    /** 화면용 trace 상한과 무관하게 이 종목에 실제 기여한 시작 요인 전체. */
    val contributingFactors: Set<CausalEconomicFactor>,
    /** 비가격 충격 합성에 쓰는 요인별 시장 전염 요약. 화면용 trace에서 역산하지 않는다. */
    val marketTransmissionsByFactor: Map<CausalEconomicFactor, CausalMarketTransmissionTrace> = emptyMap(),
) {
    init {
        require(stockId.isNotBlank())
        require(signedSensitivity.isFinite())
        require(relativeSensitivity.isFinite() && relativeSensitivity in 0.0..1.5)
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(specificity in 1..4)
        require(traces.isNotEmpty())
        require(contributingFactors.isNotEmpty())
        require(traces.mapNotNull { it.nodes.firstOrNull()?.factor }.all(contributingFactors::contains))
        require(marketTransmissionsByFactor.keys.all(contributingFactors::contains))
    }

    val primaryTrace: CausalImpactTrace get() = traces.first()
}
