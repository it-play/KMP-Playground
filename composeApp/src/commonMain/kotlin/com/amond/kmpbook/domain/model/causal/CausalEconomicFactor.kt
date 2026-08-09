package com.amond.kmpbook.domain.model.causal

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
