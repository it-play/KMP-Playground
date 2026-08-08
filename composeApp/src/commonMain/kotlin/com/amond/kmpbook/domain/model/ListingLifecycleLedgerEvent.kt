package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

data class ListingLifecycleLedgerEvent(
    val id: String,
    val sequence: Long,
    val stockId: String,
    val tradingDate: LocalDate,
    val kind: ListingLifecycleEventKind,
    val fromStatus: ListingLifecycleStatus,
    val toStatus: ListingLifecycleStatus,
    val reason: ListingLifecycleReason?,
    val level: ListingNoticeLevel,
    val title: String,
    val summary: String,
    val deadline: LocalDate? = null,
    val disposition: ListingFinalDisposition? = null,
    /** ETF/ETN orderly termination state를 만든 exact GameEvent.id. */
    val controllingTerminationOccurrenceId: String? = null,
    /** 동일 효력일 공시의 계약 우선순위. 낮을수록 우선한다. */
    val controllingTerminationNoticePriority: Int? = null,
    /** 거래일/현재일 clamp 전 계약상 또는 공시상 원래 효력 거래일. */
    val controllingTerminationRawEffectiveOn: LocalDate? = null,
    val sourceUrls: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank() && stockId.isNotBlank())
        require(sequence > 0L)
        require(title.isNotBlank() && summary.isNotBlank())
        require(sourceUrls.distinct().size == sourceUrls.size)
        require(sourceUrls.all { it.startsWith("https://") })
        require(controllingTerminationOccurrenceId?.isNotBlank() != false)
        require(controllingTerminationNoticePriority == null || controllingTerminationNoticePriority >= 0)
        val orderlyTerminationStage = isOrderlyProductTerminationStage(reason, toStatus)
        require((controllingTerminationOccurrenceId != null) == orderlyTerminationStage) {
            "상품 종료 원장 단계에는 이를 지배하는 정확한 종료 공시 ID가 필요합니다."
        }
        require((controllingTerminationNoticePriority != null) == orderlyTerminationStage)
        require((controllingTerminationRawEffectiveOn != null) == orderlyTerminationStage)
    }
}
