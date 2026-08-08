package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate

/** 직렬화 가능한 상장 생명주기 스냅샷. */
data class ListingLifecycleState(
    val stockId: String,
    val market: Market,
    val instrumentType: InstrumentType,
    val profileId: ListingLifecycleProfileId,
    val status: ListingLifecycleStatus = ListingLifecycleStatus.LISTED,
    val activeReason: ListingLifecycleReason? = null,
    val designatedOn: LocalDate? = null,
    val cureDeadline: LocalDate? = null,
    val reviewDeadline: LocalDate? = null,
    val scheduledDelistingOn: LocalDate? = null,
    val settlementDueOn: LocalDate? = null,
    /** 현재 ETF/ETN orderly termination 절차를 지배하는 exact GameEvent.id. */
    val controllingTerminationOccurrenceId: String? = null,
    val controllingTerminationNoticePriority: Int? = null,
    val controllingTerminationRawEffectiveOn: LocalDate? = null,
    /** 공시 후 매매 가능한 정리 기간인지, 기존 정지가 계속되는지를 구분한다. */
    val tradingAllowedUntilDelisting: Boolean = true,
    val consecutiveLowBidTradingDays: Int = 0,
    val consecutiveLowMarketCapTradingDays: Int = 0,
    val consecutiveLowLiquidityTradingDays: Int = 0,
    val consecutiveCureTradingDays: Int = 0,
    /** 해제 뒤 같은 사유가 다시 발생해도 이전 이력을 잃지 않는다. */
    val designationCount: Int = 0,
    val finalDisposition: ListingFinalDisposition? = null,
    val lastEvaluatedTradingDate: LocalDate? = null,
    val ledgerSequence: Long = 0L,
) {
    init {
        require(stockId.isNotBlank())
        require(
            consecutiveLowBidTradingDays >= 0 &&
                consecutiveLowMarketCapTradingDays >= 0 &&
                consecutiveLowLiquidityTradingDays >= 0 &&
                consecutiveCureTradingDays >= 0 &&
                designationCount >= 0 &&
                ledgerSequence >= 0L,
        )
        require(
            status in setOf(ListingLifecycleStatus.LISTED, ListingLifecycleStatus.DELISTED, ListingLifecycleStatus.TERMINATED) ||
                activeReason != null,
        ) { "진행 중인 상장 조치에는 사유가 필요합니다." }
        require(status != ListingLifecycleStatus.DELISTING_SCHEDULED || scheduledDelistingOn != null)
        require(status != ListingLifecycleStatus.LIQUIDATION_PENDING || settlementDueOn != null)
        require(controllingTerminationOccurrenceId?.isNotBlank() != false)
        require(controllingTerminationNoticePriority == null || controllingTerminationNoticePriority >= 0)
        val orderlyTerminationStage = isOrderlyProductTerminationStage(activeReason, status)
        require((controllingTerminationOccurrenceId != null) == orderlyTerminationStage) {
            "상품 종료 상태에는 이를 지배하는 정확한 종료 공시 ID가 필요합니다."
        }
        require((controllingTerminationNoticePriority != null) == orderlyTerminationStage)
        require((controllingTerminationRawEffectiveOn != null) == orderlyTerminationStage)
        require(
            status !in setOf(ListingLifecycleStatus.DELISTED, ListingLifecycleStatus.TERMINATED) ||
                finalDisposition != null,
        ) { "최종 상장 상태에는 잔고 처분 방식이 필요합니다." }
    }

    /** 상장 상태만 본 체결 가능성. 장 운영시간·VI·서킷브레이커는 별도로 AND 결합한다. */
    val isTradable: Boolean
        get() = when (status) {
            ListingLifecycleStatus.LISTED,
            ListingLifecycleStatus.DEFICIENCY_NOTICE,
            -> true

            ListingLifecycleStatus.DELISTING_SCHEDULED -> tradingAllowedUntilDelisting

            /** 미국 개선·심사 기간은 거래를 유지할 수 있지만 KRX 적격성 심사는 정지로 모델링한다. */
            ListingLifecycleStatus.UNDER_REVIEW -> market.isUnitedStates
            ListingLifecycleStatus.TRADING_SUSPENDED,
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
            -> false
        }

    val isOrderAllowed: Boolean get() = isTradable

    /**
     * 거래정지와 지수 편출은 별도 조치다. 별도 지수 리밸런싱 일자가 없는 게임 데이터에서는
     * 상장 종료 효력일까지 마지막 체결가로 구성종목에 남기고, 청산·최종 종료 시점에만 제외한다.
     */
    val isIndexEligible: Boolean
        get() = status !in setOf(
            ListingLifecycleStatus.LIQUIDATION_PENDING,
            ListingLifecycleStatus.DELISTED,
            ListingLifecycleStatus.TERMINATED,
        )

    val isSettlementPending: Boolean get() = status == ListingLifecycleStatus.LIQUIDATION_PENDING
    val isTerminal: Boolean
        get() = status == ListingLifecycleStatus.DELISTED || status == ListingLifecycleStatus.TERMINATED
}
