package com.amond.kmpbook.domain.model.causal

/** 경제 요인이 종목 가치로 바뀌는 마지막 사업 메커니즘이다. 문장과 부호를 산업명 추측에서 분리한다. */
enum class CausalExposureMechanism {
    REFERENCE_PRICE_REVENUE,
    REFERENCE_PRICE_LINK,
    VARIABLE_INPUT_COST,
    DEMAND_VOLUME,
    CREDIT_INTERMEDIATION,
    CAPITAL_EXPENDITURE_DEMAND,
    RISK_ASSET_FLOW,
    SAFE_HAVEN_FLOW,
}
