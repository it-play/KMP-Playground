package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/**
 * 하루 한 번 공급하는 감시 스냅샷. 정량 자료가 없는 사유는 [riskTags]와
 * [recoveryConditions]로 명시해, 숨은 난수나 시스템 시계 없이 재생할 수 있게 한다.
 */
data class DailyListingSurveillanceInput(
    val stockId: String,
    val tradingDate: LocalDate,
    val close: Double? = null,
    val marketCapitalization: Double? = null,
    val tradedVolume: Long? = null,
    /** 0.01은 하루 유통주식의 1%가 거래됐다는 뜻이다. */
    val turnoverRate: Double? = null,
    val riskTags: Set<ListingRiskTag> = emptySet(),
    /** 서로 다른 공시가 겹칠 때 한 사건의 중요도가 다른 사유를 잘못 승격하지 않게 태그별로 보존한다. */
    val riskSeverityByTag: Map<ListingRiskTag, ListingRiskSeverity> = emptyMap(),
    val recoveryConditions: Set<ListingRecoveryCondition> = emptySet(),
    /** 실제 공시 일정이 있는 캠페인 이벤트가 정책 기본 일정을 덮어쓸 때 사용한다. */
    val scheduledDelistingOn: LocalDate? = null,
    val scheduledSettlementOn: LocalDate? = null,
    val finalDispositionHint: ListingFinalDispositionType? = null,
    val otcTransferAvailable: Boolean = false,
    val liquidationCashPerUnit: Double? = null,
    /** 이 입력의 종료일·평가 조건을 공급한 exact GameEvent.id. */
    val controllingTerminationOccurrenceId: String? = null,
    val controllingTerminationNoticePriority: Int? = null,
    val controllingTerminationRawEffectiveOn: LocalDate? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(close == null || close >= 0.0 && close.isFinite())
        require(marketCapitalization == null || marketCapitalization >= 0.0 && marketCapitalization.isFinite())
        require(tradedVolume == null || tradedVolume >= 0L)
        require(turnoverRate == null || turnoverRate >= 0.0 && turnoverRate.isFinite())
        require(scheduledDelistingOn == null || scheduledDelistingOn >= tradingDate)
        require(scheduledSettlementOn == null || scheduledSettlementOn >= tradingDate)
        require(liquidationCashPerUnit == null || liquidationCashPerUnit >= 0.0 && liquidationCashPerUnit.isFinite())
        require(riskSeverityByTag.keys.all(riskTags::contains)) {
            "위험 중요도는 현재 입력에 포함된 위험 태그에만 지정할 수 있습니다."
        }
        require(controllingTerminationOccurrenceId?.isNotBlank() != false)
        require(controllingTerminationNoticePriority == null || controllingTerminationNoticePriority >= 0)
        val hasOrderlyTerminationSignal = riskTags.hasOrderlyProductTerminationSignal()
        require((controllingTerminationOccurrenceId != null) == hasOrderlyTerminationSignal) {
            "상품 종료 감시 입력에는 종료 조건을 공급한 정확한 공시 ID가 필요합니다."
        }
        require((controllingTerminationNoticePriority != null) == hasOrderlyTerminationSignal)
        require((controllingTerminationRawEffectiveOn != null) == hasOrderlyTerminationSignal)
        require(controllingTerminationOccurrenceId == null || scheduledDelistingOn != null)
    }
}
