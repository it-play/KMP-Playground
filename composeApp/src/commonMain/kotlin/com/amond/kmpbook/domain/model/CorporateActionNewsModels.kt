package com.amond.kmpbook.domain.model

import kotlin.math.abs
import kotlin.time.Instant

/**
 * 기업행동 공시가 나타내는 업무 전이다. 뉴스의 만료 시각은 표시 수명일 뿐이며,
 * 실제 상태는 이 전이와 기업행동 원장으로 판단한다.
 */
enum class CorporateActionNewsTransition {
    ANNOUNCED,
    APPLIED,
    CANCELLED,
}

/**
 * 분할·병합 뉴스를 예정/적용 원장과 연결하는 불변 참조다.
 *
 * [occurrenceId]는 공시와 적용 기록이 공유하는 기업행동 ID다. 적용 뉴스에는 실제
 * 효력 시각과 전역 회계 순번까지 저장해, 뉴스 문구나 ID 접두사 없이 원장 전이를
 * 정확히 재검증할 수 있다.
 */
data class CorporateActionNewsReference(
    val occurrenceId: String,
    val transition: CorporateActionNewsTransition,
    val stockId: String,
    val kind: CorporateActionKind,
    val announcedAt: Instant,
    val effectiveNotBefore: Instant,
    val quantityMultiplier: Double,
    val source: CorporateActionSource,
    val rationale: String,
    val appliedAt: Instant? = null,
    val accountingSequence: Long? = null,
    val cancelledAt: Instant? = null,
    /** 취소 원인이 된 상장 생명주기 원장 이벤트를 ID와 시퀀스로 동시에 고정한다. */
    val cancellingListingEventId: String? = null,
    val cancellingListingLedgerSequence: Long? = null,
    val cancellingListingStatus: ListingLifecycleStatus? = null,
) {
    init {
        val violation = semanticInvariantViolation()
        require(violation == null) { violation.orEmpty() }
    }

    fun semanticInvariantViolation(): String? {
        val safeTransition = transition as CorporateActionNewsTransition?
            ?: return "기업행동 뉴스 전이가 필요합니다."
        val safeKind = kind as CorporateActionKind?
            ?: return "기업행동 뉴스 종류가 필요합니다."
        val safeSource = source as CorporateActionSource?
            ?: return "기업행동 뉴스 출처가 필요합니다."
        if (safeTransition !in CorporateActionNewsTransition.entries ||
            safeKind !in CorporateActionKind.entries || safeSource !in CorporateActionSource.entries
        ) {
            return "기업행동 뉴스 전이·종류·출처가 유효하지 않습니다."
        }
        return when {
            occurrenceId.isBlank() -> "기업행동 발생 ID는 비어 있을 수 없습니다."
            stockId.isBlank() -> "기업행동 대상 종목 ID는 비어 있을 수 없습니다."
            rationale.isBlank() -> "기업행동 근거는 비어 있을 수 없습니다."
            !quantityMultiplier.isFinite() || quantityMultiplier <= 0.0 ||
                abs(quantityMultiplier - 1.0) <= 1e-9 ->
                "기업행동 수량 배수가 유효하지 않습니다."
            safeKind == CorporateActionKind.FORWARD_SPLIT && quantityMultiplier <= 1.0 ||
                safeKind == CorporateActionKind.REVERSE_SPLIT && quantityMultiplier >= 1.0 ->
                "기업행동 종류와 수량 배수가 일치하지 않습니다."
            effectiveNotBefore <= announcedAt -> "기업행동 최소 효력 시각은 공시 시각보다 늦어야 합니다."
            safeTransition == CorporateActionNewsTransition.ANNOUNCED && appliedAt != null ->
                "기업행동 공시 전이에는 실제 적용 시각을 저장할 수 없습니다."
            safeTransition == CorporateActionNewsTransition.ANNOUNCED && accountingSequence != null ->
                "기업행동 공시 전이에는 회계 순번을 저장할 수 없습니다."
            safeTransition == CorporateActionNewsTransition.ANNOUNCED && hasCancellationFields() ->
                "기업행동 공시 전이에는 취소 원장 정보를 저장할 수 없습니다."
            safeTransition == CorporateActionNewsTransition.APPLIED && appliedAt == null ->
                "기업행동 적용 전이에는 실제 적용 시각이 필요합니다."
            safeTransition == CorporateActionNewsTransition.APPLIED &&
                appliedAt?.let { it < effectiveNotBefore } == true ->
                "기업행동 실제 적용은 최소 효력 시각보다 빠를 수 없습니다."
            safeTransition == CorporateActionNewsTransition.APPLIED &&
                (accountingSequence == null || accountingSequence <= 0L) ->
                "기업행동 적용 전이에는 양수인 회계 순번이 필요합니다."
            safeTransition == CorporateActionNewsTransition.APPLIED && hasCancellationFields() ->
                "기업행동 적용 전이에는 취소 원장 정보를 저장할 수 없습니다."
            safeTransition == CorporateActionNewsTransition.CANCELLED &&
                (appliedAt != null || accountingSequence != null) ->
                "취소된 기업행동에는 적용 시각이나 회계 순번을 저장할 수 없습니다."
            safeTransition == CorporateActionNewsTransition.CANCELLED && cancelledAt == null ->
                "기업행동 취소 전이에는 취소 시각이 필요합니다."
            safeTransition == CorporateActionNewsTransition.CANCELLED &&
                cancelledAt?.let { it < announcedAt } == true ->
                "기업행동 취소는 공시보다 먼저 발생할 수 없습니다."
            safeTransition == CorporateActionNewsTransition.CANCELLED &&
                cancellingListingEventId?.isBlank() != false ->
                "기업행동 취소 전이에는 상장 원장 이벤트 ID가 필요합니다."
            safeTransition == CorporateActionNewsTransition.CANCELLED &&
                (cancellingListingLedgerSequence == null || cancellingListingLedgerSequence <= 0L) ->
                "기업행동 취소 전이에는 양수인 상장 원장 시퀀스가 필요합니다."
            safeTransition == CorporateActionNewsTransition.CANCELLED &&
                cancellingListingStatus !in setOf(
                    ListingLifecycleStatus.LIQUIDATION_PENDING,
                    ListingLifecycleStatus.DELISTED,
                    ListingLifecycleStatus.TERMINATED,
                ) -> "기업행동은 청산 대기 또는 최종 상장 종료 원장으로만 취소할 수 있습니다."
            else -> null
        }
    }

    private fun hasCancellationFields(): Boolean =
        cancelledAt != null || cancellingListingEventId != null ||
            cancellingListingLedgerSequence != null || cancellingListingStatus != null

    fun pendingLineageViolation(action: PendingCorporateAction): String? = when {
        transition != CorporateActionNewsTransition.ANNOUNCED ->
            "대기 기업행동은 공시 전이로 참조해야 합니다."
        occurrenceId != action.id -> "기업행동 공시의 발생 ID가 대기 원장과 다릅니다."
        stockId != action.stockId -> "기업행동 공시의 종목이 대기 원장과 다릅니다."
        kind != action.kind -> "기업행동 공시의 종류가 대기 원장과 다릅니다."
        announcedAt != action.announcedAt -> "기업행동 공시 시각이 대기 원장과 다릅니다."
        effectiveNotBefore != action.effectiveNotBefore ->
            "기업행동 최소 효력 시각이 대기 원장과 다릅니다."
        quantityMultiplier != action.quantityMultiplier -> "기업행동 공시 배수가 대기 원장과 다릅니다."
        source != action.source -> "기업행동 공시 출처가 대기 원장과 다릅니다."
        rationale != action.rationale -> "기업행동 공시 근거가 대기 원장과 다릅니다."
        else -> semanticInvariantViolation()
    }

    fun appliedLineageViolation(record: CorporateActionRecord): String? = when {
        transition != CorporateActionNewsTransition.APPLIED ->
            "적용 기업행동은 완료 전이로 참조해야 합니다."
        occurrenceId != record.id -> "기업행동 적용 뉴스의 발생 ID가 적용 원장과 다릅니다."
        stockId != record.stockId -> "기업행동 적용 뉴스의 종목이 적용 원장과 다릅니다."
        kind != record.kind -> "기업행동 적용 뉴스의 종류가 적용 원장과 다릅니다."
        announcedAt != record.announcedAt -> "기업행동 적용 뉴스의 공시 시각이 적용 원장과 다릅니다."
        effectiveNotBefore != record.effectiveNotBefore ->
            "기업행동 적용 뉴스의 최소 효력 시각이 적용 원장과 다릅니다."
        quantityMultiplier != record.quantityMultiplier -> "기업행동 적용 배수가 적용 원장과 다릅니다."
        source != record.source -> "기업행동 적용 출처가 적용 원장과 다릅니다."
        rationale != record.rationale -> "기업행동 적용 근거가 적용 원장과 다릅니다."
        appliedAt != record.effectiveAt -> "기업행동 적용 시각이 적용 원장과 다릅니다."
        accountingSequence != record.accountingSequence ->
            "기업행동 적용 뉴스의 회계 순번이 적용 원장과 다릅니다."
        else -> semanticInvariantViolation()
    }

    fun announcementLineageViolation(record: CorporateActionRecord): String? = when {
        transition != CorporateActionNewsTransition.ANNOUNCED ->
            "적용 전 기업행동 공시는 공시 전이로 참조해야 합니다."
        occurrenceId != record.id -> "기업행동 공시의 발생 ID가 적용 원장과 다릅니다."
        stockId != record.stockId -> "기업행동 공시의 종목이 적용 원장과 다릅니다."
        kind != record.kind -> "기업행동 공시의 종류가 적용 원장과 다릅니다."
        announcedAt != record.announcedAt -> "기업행동 공시 시각이 적용 원장과 다릅니다."
        effectiveNotBefore != record.effectiveNotBefore ->
            "기업행동 공시의 최소 효력 시각이 적용 원장과 다릅니다."
        quantityMultiplier != record.quantityMultiplier -> "기업행동 공시 배수가 적용 원장과 다릅니다."
        source != record.source -> "기업행동 공시 출처가 적용 원장과 다릅니다."
        rationale != record.rationale -> "기업행동 공시 근거가 적용 원장과 다릅니다."
        else -> semanticInvariantViolation()
    }

    fun cancellationLineageViolation(
        announcement: CorporateActionNewsReference,
        listingEvent: ListingLifecycleLedgerEvent,
    ): String? = when {
        transition != CorporateActionNewsTransition.CANCELLED ->
            "취소된 기업행동은 취소 전이로 참조해야 합니다."
        announcement.transition != CorporateActionNewsTransition.ANNOUNCED ->
            "기업행동 취소에는 같은 발생 ID의 선행 공시가 필요합니다."
        occurrenceId != announcement.occurrenceId || stockId != announcement.stockId ||
            kind != announcement.kind || announcedAt != announcement.announcedAt ||
            effectiveNotBefore != announcement.effectiveNotBefore ||
            quantityMultiplier != announcement.quantityMultiplier || source != announcement.source ||
            rationale != announcement.rationale ->
            "기업행동 취소 전이와 선행 공시의 원장 계보가 다릅니다."
        cancellingListingEventId != listingEvent.id ||
            cancellingListingLedgerSequence != listingEvent.sequence ||
            stockId != listingEvent.stockId ||
            cancellingListingStatus != listingEvent.toStatus ->
            "기업행동 취소 전이가 상장 생명주기 원장과 다릅니다."
        else -> semanticInvariantViolation()
    }
}

fun PendingCorporateAction.toAnnouncementNewsReference(): CorporateActionNewsReference =
    CorporateActionNewsReference(
        occurrenceId = id,
        transition = CorporateActionNewsTransition.ANNOUNCED,
        stockId = stockId,
        kind = kind,
        announcedAt = announcedAt,
        effectiveNotBefore = effectiveNotBefore,
        quantityMultiplier = quantityMultiplier,
        source = source,
        rationale = rationale,
    )

fun CorporateActionRecord.toAppliedNewsReference(): CorporateActionNewsReference =
    CorporateActionNewsReference(
        occurrenceId = id,
        transition = CorporateActionNewsTransition.APPLIED,
        stockId = stockId,
        kind = kind,
        announcedAt = announcedAt,
        effectiveNotBefore = effectiveNotBefore,
        quantityMultiplier = quantityMultiplier,
        source = source,
        rationale = rationale,
        appliedAt = effectiveAt,
        accountingSequence = accountingSequence,
    )

fun PendingCorporateAction.toCancellationNewsReference(
    cancelledAt: Instant,
    listingEvent: ListingLifecycleLedgerEvent,
): CorporateActionNewsReference = CorporateActionNewsReference(
    occurrenceId = id,
    transition = CorporateActionNewsTransition.CANCELLED,
    stockId = stockId,
    kind = kind,
    announcedAt = announcedAt,
    effectiveNotBefore = effectiveNotBefore,
    quantityMultiplier = quantityMultiplier,
    source = source,
    rationale = rationale,
    cancelledAt = cancelledAt,
    cancellingListingEventId = listingEvent.id,
    cancellingListingLedgerSequence = listingEvent.sequence,
    cancellingListingStatus = listingEvent.toStatus,
)
