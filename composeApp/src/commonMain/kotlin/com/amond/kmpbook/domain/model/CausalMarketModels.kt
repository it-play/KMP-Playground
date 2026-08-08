package com.amond.kmpbook.domain.model

/**
 * 사건이 처음 바꾸는 경제 변수와 인과 그래프의 중간 변수다.
 *
 * 이름은 가격 방향이 아니라 변수 자체를 뜻한다. 예를 들어 [CRUDE_OIL_PRICE]의
 * [CausalSignalDirection.INCREASE]는 유가 상승이고, 연결 간선의 부호가 비용·수요로
 * 전달되는 방향을 결정한다.
 */
enum class CausalEconomicFactor(val displayName: String) {
    CRUDE_OIL_PRICE("원유 가격"),
    TRANSPORT_FUEL_COST("운송 연료비"),
    PETROCHEMICAL_INPUT_COST("석유화학 투입 원가"),
    PLASTIC_PACKAGING_COST("플라스틱·포장재 원가"),
    HOUSEHOLD_ENERGY_BURDEN("가계 에너지 부담"),
    CONSUMER_DEMAND("소비 수요"),
    FREIGHT_RATE("운임"),
    LOGISTICS_INPUT_COST("물류 투입 원가"),
    GAME_SOFTWARE_DEMAND("게임 소프트웨어 수요"),
    HIGH_END_PC_DEMAND("고사양 PC 수요"),
    COMPUTING_HARDWARE_DEMAND("컴퓨팅 하드웨어 수요"),
    SEMICONDUCTOR_DEMAND("반도체 수요"),
    CREDIT_AVAILABILITY("신용 공급"),
    BUSINESS_INVESTMENT("기업 투자"),
    RISK_APPETITE("위험자산 선호"),
}

enum class CausalSignalDirection(
    val sign: Double,
    val displayName: String,
) {
    INCREASE(1.0, "상승"),
    DECREASE(-1.0, "하락"),
}

/** 같은 경제 요인이라도 사건의 성격에 따라 공간적으로 이동하는 속도를 구분한다. */
enum class CausalTransmissionProfile(val displayName: String) {
    /** 거래소 호가 공백·기술적 주문 불균형. 같은 국가의 인접 시장에만 매우 약하게 번진다. */
    LOCAL_MICROSTRUCTURE("현지 미시구조"),
    /** 마진콜·위험 축소·글로벌 포트폴리오 재조정. */
    PORTFOLIO_DELEVERAGING("포트폴리오 디레버리징"),
    /** 달러·회사채·은행 조달 여건처럼 국가 간 금융 연결을 타는 신호. */
    FUNDING_STRESS("자금조달 경색"),
    /** 소비·투자·산업 수요처럼 무역과 실적을 통해 느리게 이동하는 신호. */
    GLOBAL_REAL_ECONOMY("글로벌 실물경제"),
    /** 원유·운임처럼 여러 시장이 같은 기준가격을 참조하는 신호. */
    GLOBAL_REFERENCE_PRICE("글로벌 기준가격"),
}

/** 저장되는 사건 payload의 구조화된 인과 시작점이다. */
data class CausalSignalSeed(
    val factor: CausalEconomicFactor,
    val direction: CausalSignalDirection,
    /** 같은 사건 안에서 시작 신호의 상대 크기. 가격 등락률이 아니다. */
    val strength: Double,
    /** 출발 신호 자체에 대한 신뢰도. 경로가 길어질수록 엔진에서 추가 감쇠한다. */
    val confidence: Double = 1.0,
    /** 경제 요인과 별개로 사건이 시장 경계를 넘는 방식. */
    val transmissionProfile: CausalTransmissionProfile = CausalTransmissionProfile.GLOBAL_REAL_ECONOMY,
) {
    init {
        require(strength.isFinite() && strength > 0.0 && strength <= 1.0) {
            "인과 신호 강도는 0보다 크고 1 이하여야 합니다."
        }
        require(confidence.isFinite() && confidence > 0.0 && confidence <= 1.0) {
            "인과 신호 신뢰도는 0보다 크고 1 이하여야 합니다."
        }
        require(transmissionProfile in CausalTransmissionProfile.entries)
    }

    val signedStrength: Double get() = direction.sign * strength
}

/** 전파 흔적에서 노드가 맡는 역할이다. */
enum class CausalTraceNodeKind {
    ECONOMIC_FACTOR,
    INDUSTRY,
    INDUSTRY_SEGMENT,
    STOCK,
}

data class CausalTraceNode(
    val kind: CausalTraceNodeKind,
    val label: String,
    val factor: CausalEconomicFactor? = null,
    val sector: Sector? = null,
    val industrySegment: IndustrySegment? = null,
    val stockId: String? = null,
) {
    init {
        require(label.isNotBlank()) { "인과 경로 노드 이름은 비어 있을 수 없습니다." }
        when (kind) {
            CausalTraceNodeKind.ECONOMIC_FACTOR -> require(
                factor != null && sector == null && industrySegment == null && stockId == null,
            ) { "경제 요인 경로 노드에는 경제 요인만 필요합니다." }
            CausalTraceNodeKind.INDUSTRY -> require(
                factor == null && sector != null && industrySegment == null && stockId == null,
            ) { "산업 경로 노드에는 산업만 필요합니다." }
            CausalTraceNodeKind.INDUSTRY_SEGMENT -> require(
                factor == null && sector != null && industrySegment?.parentSector == sector && stockId == null,
            ) { "세부 산업 경로 노드에는 일치하는 상위 산업이 필요합니다." }
            CausalTraceNodeKind.STOCK -> require(
                factor == null && sector == null && industrySegment == null && !stockId.isNullOrBlank(),
            ) { "종목 경로 노드에는 종목 ID가 필요합니다." }
        }
    }
}

/**
 * 경제 요인 그래프에 들어오기 전에 신호가 거래소 사이를 이동한 대표 경로다.
 *
 * [reach]는 가능한 시장 경로들을 noisy-OR로 합성한 전염도이고,
 * [dominantPathContribution]은 화면에 보존한 대표 경로 하나의 기여도다.
 */
data class CausalMarketTransmissionTrace(
    val markets: List<Market>,
    val reach: Double,
    val dominantPathContribution: Double,
) {
    init {
        require(markets.isNotEmpty()) { "시장 전염 경로에는 시장이 하나 이상 필요합니다." }
        require(markets.zipWithNext().none { (left, right) -> left == right }) {
            "시장 전염 경로는 같은 시장을 연속해서 방문할 수 없습니다."
        }
        require(reach.isFinite() && reach > 0.0 && reach <= 1.0) {
            "시장 전염도는 0보다 크고 1 이하여야 합니다."
        }
        require(
            dominantPathContribution.isFinite() &&
                dominantPathContribution > 0.0 &&
                dominantPathContribution <= reach,
        ) { "대표 시장 경로 기여도는 0보다 크고 전체 전염도 이하여야 합니다." }
    }

    val isCrossMarket: Boolean get() = markets.size > 1
    val labels: List<String> get() = markets.map(Market::displayName)
}

/** 하나의 단순 경로가 특정 종목에 기여한 결과다. */
data class CausalImpactTrace(
    val contribution: Double,
    val confidence: Double,
    val nodes: List<CausalTraceNode>,
    val rationale: String,
    val marketTransmission: CausalMarketTransmissionTrace? = null,
) {
    init {
        require(contribution.isFinite() && contribution != 0.0)
        require(confidence.isFinite() && confidence in 0.0..1.0)
        require(nodes.size >= 2) { "인과 흔적에는 시작 요인과 최종 대상이 필요합니다." }
        require(rationale.isNotBlank()) { "인과 흔적 근거는 비어 있을 수 없습니다." }
    }

    val labels: List<String>
        get() = marketTransmission?.labels.orEmpty() + nodes.map(CausalTraceNode::label)
}

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

/** 어떤 우선순위 단계에서 종목 영향을 확정했는지 표시한다. */
enum class EventImpactResolutionSource {
    EXPLICIT_PATH,
    CAUSAL_GRAPH,
    SCOPE_FALLBACK,
    NONE,
}
