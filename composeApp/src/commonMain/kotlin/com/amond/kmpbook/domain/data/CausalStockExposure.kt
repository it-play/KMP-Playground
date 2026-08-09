package com.amond.kmpbook.domain.data

import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import com.amond.kmpbook.domain.model.causal.CausalExposureMechanism
import com.amond.kmpbook.domain.model.causal.CausalTraceNodeKind
import com.amond.kmpbook.domain.model.market.IndustrySegment
import com.amond.kmpbook.domain.model.market.Sector

/**
 * 경제 요인에서 산업·종목으로 이어지는 마지막 노출 간선이다.
 *
 * StockDefinition에 저장 필드를 추가하지 않아 저장 payload를 키우지 않는다. 명시적 회사
 * override가 같은 요인에 있으면 일반 산업 규칙을 대체하며 ETF 배율은 절대 곱하지 않는다.
 */
data class CausalStockExposure(
    val factor: CausalEconomicFactor,
    val mechanism: CausalExposureMechanism,
    val weight: Double,
    val targetKind: CausalTraceNodeKind,
    val targetLabel: String,
    val rationale: String,
    val sector: Sector? = null,
    val industrySegment: IndustrySegment? = null,
    val explicitCompanyOverride: Boolean = false,
) {
    init {
        require(weight.isFinite() && weight != 0.0 && kotlin.math.abs(weight) <= 1.0) {
            "인과 종목 노출 가중치의 절댓값은 1 이하여야 합니다."
        }
        require(targetLabel.isNotBlank() && rationale.isNotBlank())
        require(targetKind in setOf(
            CausalTraceNodeKind.INDUSTRY,
            CausalTraceNodeKind.INDUSTRY_SEGMENT,
            CausalTraceNodeKind.STOCK,
        ))
        when (mechanism) {
            CausalExposureMechanism.VARIABLE_INPUT_COST,
            CausalExposureMechanism.SAFE_HAVEN_FLOW,
            -> require(weight < 0.0) { "$mechanism 노출은 음의 가중치여야 합니다." }
            CausalExposureMechanism.REFERENCE_PRICE_REVENUE,
            CausalExposureMechanism.DEMAND_VOLUME,
            CausalExposureMechanism.CREDIT_INTERMEDIATION,
            CausalExposureMechanism.CAPITAL_EXPENDITURE_DEMAND,
            CausalExposureMechanism.RISK_ASSET_FLOW,
            -> require(weight > 0.0) { "$mechanism 노출은 양의 가중치여야 합니다." }
            CausalExposureMechanism.REFERENCE_PRICE_LINK -> Unit
        }
        require(mechanism.accepts(factor)) {
            "$mechanism 메커니즘은 ${factor.name} 경제 요인과 함께 사용할 수 없습니다."
    }
}

private fun CausalExposureMechanism.accepts(factor: CausalEconomicFactor): Boolean = when (this) {
    CausalExposureMechanism.REFERENCE_PRICE_REVENUE ->
        factor in setOf(CausalEconomicFactor.CRUDE_OIL_PRICE, CausalEconomicFactor.FREIGHT_RATE)
    CausalExposureMechanism.REFERENCE_PRICE_LINK -> factor == CausalEconomicFactor.CRUDE_OIL_PRICE
    CausalExposureMechanism.VARIABLE_INPUT_COST -> factor in setOf(
        CausalEconomicFactor.TRANSPORT_FUEL_COST,
        CausalEconomicFactor.PETROCHEMICAL_INPUT_COST,
        CausalEconomicFactor.PLASTIC_PACKAGING_COST,
        CausalEconomicFactor.LOGISTICS_INPUT_COST,
    )
    CausalExposureMechanism.DEMAND_VOLUME -> factor in setOf(
        CausalEconomicFactor.CONSUMER_DEMAND,
        CausalEconomicFactor.GAME_SOFTWARE_DEMAND,
        CausalEconomicFactor.HIGH_END_PC_DEMAND,
        CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
        CausalEconomicFactor.SEMICONDUCTOR_DEMAND,
    )
    CausalExposureMechanism.CREDIT_INTERMEDIATION -> factor == CausalEconomicFactor.CREDIT_AVAILABILITY
    CausalExposureMechanism.CAPITAL_EXPENDITURE_DEMAND -> factor == CausalEconomicFactor.BUSINESS_INVESTMENT
    CausalExposureMechanism.RISK_ASSET_FLOW,
    CausalExposureMechanism.SAFE_HAVEN_FLOW,
    -> factor == CausalEconomicFactor.RISK_APPETITE
}

    val specificity: Int
        get() = when (targetKind) {
            CausalTraceNodeKind.INDUSTRY -> 2
            CausalTraceNodeKind.INDUSTRY_SEGMENT -> 3
            CausalTraceNodeKind.STOCK -> 4
            CausalTraceNodeKind.ECONOMIC_FACTOR -> 1
        }
}
