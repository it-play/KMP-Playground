package com.amond.kmpbook.domain.model.instrument

import kotlin.time.Instant

const val MIN_FUND_REFERENCE_VALUE: Double = 0.000001
const val MAX_FUND_REFERENCE_VALUE: Double = 1.0e18

/**
 * 현재 런타임이 개방형 ETF에 저장하는 원시 좌수·기준가 회계 상태다.
 *
 * [navPerUnit]과 [indicativeValuePerUnit]는 시장가격이 아니라 공정가치 계층이며,
 * [lastNetFlow]는 누적값이 아닌 직전 회계 구간의 `좌수 증감 × 갱신 기준가`다. AUM,
 * 시가총액, 프리미엄·디스카운트는 저장하지 않고 호가와 함께 투영한다.
 * ETN과 폐쇄형 펀드는 각각의 별도 계약·대차대조표 상태를 사용한다.
 *
 * 기준가는 공유된 양수 범위, 좌수는 유한한 양수여야 하고 시각은 역행할 수 없다.
 * 좌수 조정은 [cumulativeUnitAdjustmentFactor]와 엄격히 증가하는
 * [lastCorporateActionAccountingSequence]로 추적한다. 아직 조정을 반영하지 않았다면
 * 순번은 `null`이고 누적 배수는 1이어야 한다.
 */
data class FundFinancialState(
    val stockId: String,
    val navPerUnit: Double,
    val indicativeValuePerUnit: Double,
    val unitsOrNotesOutstanding: Double,
    val lastNetFlow: Double,
    val cumulativeUnitAdjustmentFactor: Double = 1.0,
    val lastCorporateActionAccountingSequence: Long? = null,
    val asOf: Instant,
) {
    init {
        require(stockId.isNotBlank())
        require(navPerUnit in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE)
        require(indicativeValuePerUnit in MIN_FUND_REFERENCE_VALUE..MAX_FUND_REFERENCE_VALUE)
        require(unitsOrNotesOutstanding.isFinite() && unitsOrNotesOutstanding > 0.0)
        require(lastNetFlow.isFinite())
        require(cumulativeUnitAdjustmentFactor.isFinite() && cumulativeUnitAdjustmentFactor > 0.0)
        require(lastCorporateActionAccountingSequence == null || lastCorporateActionAccountingSequence > 0L)
        if (lastCorporateActionAccountingSequence == null) require(cumulativeUnitAdjustmentFactor == 1.0)
    }
}
