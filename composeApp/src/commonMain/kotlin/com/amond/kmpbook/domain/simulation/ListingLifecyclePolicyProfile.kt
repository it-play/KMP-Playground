package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.DailyListingSurveillanceInput
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDisposition
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleEvaluation
import com.amond.kmpbook.domain.model.ListingLifecycleEventKind
import com.amond.kmpbook.domain.model.ListingLifecycleLedgerEvent
import com.amond.kmpbook.domain.model.ListingLifecycleProfileId
import com.amond.kmpbook.domain.model.ListingLifecycleReason
import com.amond.kmpbook.domain.model.ListingLifecycleReplayResult
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.ListingNoticeLevel
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskSeverity
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.ListingRuleBasis
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.blocksOrderlyProductTermination
import com.amond.kmpbook.domain.model.preemptsOrderlyProductTermination
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 종목팩에서 상장 유지 프로필을 선택하는 데 쓰는 불변 정책값.
 * 금액 단위는 종목 상장통화이며 비율은 0.01 = 1% 형식이다.
 */
data class ListingLifecyclePolicyProfile(
    val id: ListingLifecycleProfileId,
    val ruleBasis: ListingRuleBasis,
    val applicableMarkets: Set<Market>,
    val applicableInstrumentTypes: Set<InstrumentType>,
    val minimumBidPrice: Double? = null,
    val bidDeficiencyTradingDays: Int = 0,
    val bidCureTradingDays: Int = 10,
    val minimumMarketCapitalization: Double? = null,
    val marketCapDeficiencyTradingDays: Int = 0,
    val minimumTurnoverRate: Double? = null,
    val liquidityDeficiencyTradingDays: Int = 0,
    val curePeriodCalendarDays: Int,
    val reviewPeriodCalendarDays: Int,
    val delistingNoticeCalendarDays: Int,
    val liquidationSettlementCalendarDays: Int,
    val officialSourceUrls: List<String>,
    val gameApproximationExplanation: String? = null,
) {
    init {
        require(applicableMarkets.isNotEmpty() && applicableInstrumentTypes.isNotEmpty())
        require(minimumBidPrice == null || minimumBidPrice > 0.0 && minimumBidPrice.isFinite())
        require(minimumBidPrice == null || bidDeficiencyTradingDays > 0)
        require(bidCureTradingDays > 0)
        require(
            minimumMarketCapitalization == null ||
                minimumMarketCapitalization > 0.0 && minimumMarketCapitalization.isFinite(),
        )
        require(minimumMarketCapitalization == null || marketCapDeficiencyTradingDays > 0)
        require(minimumTurnoverRate == null || minimumTurnoverRate >= 0.0 && minimumTurnoverRate.isFinite())
        require(minimumTurnoverRate == null || liquidityDeficiencyTradingDays > 0)
        require(curePeriodCalendarDays > 0)
        require(reviewPeriodCalendarDays > 0)
        require(delistingNoticeCalendarDays > 0)
        require(liquidationSettlementCalendarDays > 0)
        require(officialSourceUrls.isNotEmpty())
        require(officialSourceUrls.distinct().size == officialSourceUrls.size)
        require(officialSourceUrls.all { it.startsWith("https://") })
        require(
            ruleBasis == ListingRuleBasis.OFFICIAL_PUBLIC_RULE_SUMMARY ||
                !gameApproximationExplanation.isNullOrBlank(),
        ) { "게임 근사가 포함된 프로필은 근사 범위를 설명해야 합니다." }
    }

    fun supports(state: ListingLifecycleState): Boolean =
        state.market in applicableMarkets && state.instrumentType in applicableInstrumentTypes
}
