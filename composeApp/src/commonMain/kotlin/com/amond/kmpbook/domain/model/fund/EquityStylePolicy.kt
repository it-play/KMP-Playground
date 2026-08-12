package com.amond.kmpbook.domain.model.fund

/** Composable equity selection tilts; product overlays such as options do not belong here. */
enum class EquityStylePolicy {
    CORE,
    GROWTH,
    VALUE,
    DIVIDEND,
    QUALITY,
    MOMENTUM,
    LOW_VOLATILITY,
    HIGH_BETA,
    ESG,
    THEMATIC,
    ACTIVE,
    SINGLE_SECURITY,
    MULTI_FACTOR,
    SIZE_TILT,
}
