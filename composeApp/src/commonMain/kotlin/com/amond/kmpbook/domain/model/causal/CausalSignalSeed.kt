package com.amond.kmpbook.domain.model.causal

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
