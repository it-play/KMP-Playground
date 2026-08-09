package com.amond.kmpbook.domain.model.causal

enum class CausalSignalDirection(
    val sign: Double,
    val displayName: String,
) {
    INCREASE(1.0, "상승"),
    DECREASE(-1.0, "하락"),
}
