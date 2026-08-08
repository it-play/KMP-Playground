package com.amond.kmpbook.presentation

import com.amond.kmpbook.domain.model.InstrumentTradingHalt
import com.amond.kmpbook.domain.model.InvestmentAlertDesignation
import com.amond.kmpbook.domain.model.InvestmentAlertLevel
import com.amond.kmpbook.domain.model.InvestmentAlertStatus
import com.amond.kmpbook.domain.model.KrxCircuitBreakerPhase
import com.amond.kmpbook.domain.model.KrxCircuitBreakerState
import com.amond.kmpbook.domain.model.KrxSidecarPhase
import com.amond.kmpbook.domain.model.KrxSidecarState
import com.amond.kmpbook.domain.model.KrxViKind
import com.amond.kmpbook.domain.model.KrxViPhase
import com.amond.kmpbook.domain.model.KrxViState
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingLifecycleState
import com.amond.kmpbook.domain.model.ListingLifecycleStatus
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.ProgramOrderSide
import com.amond.kmpbook.domain.model.TradingHaltReason
import com.amond.kmpbook.domain.model.TradingHaltStatus
import com.amond.kmpbook.domain.model.TradingProtectionSnapshot
import com.amond.kmpbook.domain.model.UsLuldPhase
import com.amond.kmpbook.domain.model.UsLuldState
import com.amond.kmpbook.domain.model.UsMwcbPhase
import com.amond.kmpbook.domain.model.UsMwcbState
import com.amond.kmpbook.domain.simulation.TradingProtectionEngine
import com.amond.kmpbook.domain.time.GameCalendar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * 엔진 상태를 화면 문구로 바꾼 불변 모델이다. UI가 거래소 enum을 다시 해석하지 않도록
 * 주문 영향과 재개 조건까지 한곳에서 결정한다.
 */
data class ProtectionUiStatus(
    val id: String,
    val badgeLabel: String,
    val title: String,
    val summary: String,
    val orderImpact: String,
    val resumeGuidance: String,
    val ruleExplanation: String,
    val tone: ProtectionUiTone,
    val emphasis: ProtectionBadgeEmphasis,
    val priority: Int,
    val stockId: String? = null,
    val markets: Set<Market> = emptySet(),
    val endsAt: Instant? = null,
) {
    init {
        require(id.isNotBlank())
        require(badgeLabel.isNotBlank() && title.isNotBlank() && summary.isNotBlank())
        require(orderImpact.isNotBlank() && resumeGuidance.isNotBlank() && ruleExplanation.isNotBlank())
        require(priority >= 0)
    }
}
