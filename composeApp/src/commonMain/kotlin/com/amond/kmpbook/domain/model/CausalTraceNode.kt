package com.amond.kmpbook.domain.model

data class CausalTraceNode(
    val kind: CausalTraceNodeKind,
    val label: String,
    val factor: CausalEconomicFactor? = null,
    /** 경제 요인이 이 경로에서 실제로 움직인 방향. 전파 엔진이 signed edge를 반영해 고정한다. */
    val factorDirection: CausalSignalDirection? = null,
    val sector: Sector? = null,
    val industrySegment: IndustrySegment? = null,
    val stockId: String? = null,
    /** 일반 시장 노출이 아니라 회사별 사업구성 override에서 만들어진 종착점인지 표시한다. */
    val companySpecificExposure: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "인과 경로 노드 이름은 비어 있을 수 없습니다." }
        when (kind) {
            CausalTraceNodeKind.ECONOMIC_FACTOR -> require(
                factor != null && factorDirection != null &&
                    sector == null && industrySegment == null && stockId == null && !companySpecificExposure,
            ) { "경제 요인 경로 노드에는 경제 요인과 실제 방향만 필요합니다." }
            CausalTraceNodeKind.INDUSTRY -> require(
                factor == null && factorDirection == null &&
                    sector != null && industrySegment == null && stockId == null && !companySpecificExposure,
            ) { "산업 경로 노드에는 산업만 필요합니다." }
            CausalTraceNodeKind.INDUSTRY_SEGMENT -> require(
                factor == null && factorDirection == null &&
                    sector != null && industrySegment?.parentSector == sector && stockId == null &&
                    !companySpecificExposure,
            ) { "세부 산업 경로 노드에는 일치하는 상위 산업이 필요합니다." }
            CausalTraceNodeKind.STOCK -> require(
                factor == null && factorDirection == null &&
                    sector == null && industrySegment == null && !stockId.isNullOrBlank(),
            ) { "종목 경로 노드에는 종목 ID가 필요합니다." }
    }
}

private fun CausalEconomicFactor.movementLabel(direction: CausalSignalDirection): String = when (this) {
    CausalEconomicFactor.HOUSEHOLD_ENERGY_BURDEN -> direction.choose("증가", "감소")
    CausalEconomicFactor.CONSUMER_DEMAND,
    CausalEconomicFactor.GAME_SOFTWARE_DEMAND,
    CausalEconomicFactor.HIGH_END_PC_DEMAND,
    CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
    CausalEconomicFactor.SEMICONDUCTOR_DEMAND,
    -> direction.choose("증가", "감소")
    CausalEconomicFactor.CREDIT_AVAILABILITY -> direction.choose("확대", "축소")
    CausalEconomicFactor.BUSINESS_INVESTMENT -> direction.choose("확대", "축소")
    CausalEconomicFactor.RISK_APPETITE -> direction.choose("강화", "약화")
    else -> direction.choose("상승", "하락")
}

private fun CausalSignalDirection.choose(increase: String, decrease: String): String =
    if (this == CausalSignalDirection.INCREASE) increase else decrease

    val displayLabel: String
        get() = if (kind == CausalTraceNodeKind.ECONOMIC_FACTOR) {
            "$label ${requireNotNull(factor).movementLabel(requireNotNull(factorDirection))}"
        } else {
            label
        }
}
