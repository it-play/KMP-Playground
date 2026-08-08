package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * KRX alert criteria vary by designation reason. The listing policy supplies already-calculated
 * trading-day windows, while this primitive records and enforces release/re-designation timing.
 */
data class InvestmentAlertDesignation(
    val stockId: String,
    val level: InvestmentAlertLevel,
    val reasonCodes: Set<String>,
    val summary: String,
    val designatedAt: Instant,
    val designatedOn: LocalDate,
    val releaseReviewWindow: TradingDayWindow,
    val redesignationWindow: TradingDayWindow? = null,
    val releaseRule: InvestmentAlertReleaseRule,
    /** 투자경고 재지정 판단에 쓰는 최초 투자경고 지정 전일 종가. */
    val preDesignationClose: Double? = null,
    /** 투자경고 재지정 판단에 쓰는 투자경고 해제 전일 종가. */
    val preReleaseClose: Double? = null,
    val redesignationReleaseRule: InvestmentAlertReleaseRule? = null,
    /** 상위 경보 지정예고 효력일과 그 가격 규칙. */
    val escalationNoticeOn: LocalDate? = null,
    val escalationNoticeReasons: Set<String> = emptySet(),
    /** 새 지정 효력일 전까지 화면에 유지할 직전 경보 단계. */
    val priorLevelUntilEffective: InvestmentAlertLevel? = null,
    val isRedesignation: Boolean = false,
    val status: InvestmentAlertStatus = InvestmentAlertStatus.ACTIVE,
    val releasedAt: Instant? = null,
    val releasedOn: LocalDate? = null,
    /** 해제 판단 공시의 다음 거래일부터 화면·규제가 바뀐다. */
    val releaseEffectiveOn: LocalDate? = null,
    val releaseReason: String? = null,
) {
    init {
        require(stockId.isNotBlank())
        require(reasonCodes.isNotEmpty() && reasonCodes.none { it.isBlank() })
        require(summary.isNotBlank())
        require(designatedOn <= releaseReviewWindow.endsOnInclusive)
        require(preDesignationClose == null || preDesignationClose > 0.0 && preDesignationClose.isFinite())
        require(preReleaseClose == null || preReleaseClose > 0.0 && preReleaseClose.isFinite())
        require((escalationNoticeOn == null) == escalationNoticeReasons.isEmpty())
        if (status == InvestmentAlertStatus.RELEASED) {
            require(releasedAt != null && releasedOn != null)
            require(releaseEffectiveOn == null || releaseEffectiveOn >= releasedOn)
        }
    }
}
