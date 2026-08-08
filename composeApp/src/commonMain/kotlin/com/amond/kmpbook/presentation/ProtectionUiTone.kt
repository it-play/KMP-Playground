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

/** TDS Badge의 정보 강도에 대응하는 보호장치 색상 역할. */
enum class ProtectionUiTone {
    INFO,
    CAUTION,
    CRITICAL,
}
