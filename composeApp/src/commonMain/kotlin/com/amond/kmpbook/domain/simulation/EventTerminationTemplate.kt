package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.EventScope
import com.amond.kmpbook.domain.model.EventSeverity
import com.amond.kmpbook.domain.model.EventType
import com.amond.kmpbook.domain.model.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.EventImpactInsight
import com.amond.kmpbook.domain.model.EventImpactCoveragePolicy
import com.amond.kmpbook.domain.model.EventRecordKind
import com.amond.kmpbook.domain.model.EventTradingHaltDirective
import com.amond.kmpbook.domain.model.GameEvent
import com.amond.kmpbook.domain.model.GameEventImpact
import com.amond.kmpbook.domain.model.ImpactDirection
import com.amond.kmpbook.domain.model.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.InstrumentTerminationValuationMethod
import com.amond.kmpbook.domain.model.InstrumentStrategy
import com.amond.kmpbook.domain.model.InstrumentType
import com.amond.kmpbook.domain.model.ListingFinalDispositionType
import com.amond.kmpbook.domain.model.ListingRecoveryCondition
import com.amond.kmpbook.domain.model.ListingRiskTag
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.Sector
import com.amond.kmpbook.domain.model.StockDefinition
import com.amond.kmpbook.domain.model.isDirectProductImpactFor
import com.amond.kmpbook.domain.model.resolvedImpactFor
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 확률 이벤트가 발생한 순간 실제 [InstrumentTerminationTerms]를 만드는 선언형 규칙이다.
 * 최소 효력 시각과 가속상환 회수율은 생성된 [GameEvent]에 복사되어 이후 다시 추첨하지 않는다.
 */
data class EventTerminationTemplate(
    val kind: InstrumentTerminationKind,
    val valuationMethod: InstrumentTerminationValuationMethod,
    val accelerationRecoveryRate: ClosedFloatingPointRange<Double>? = null,
) {
    init {
        require(kind != InstrumentTerminationKind.CONTRACTUAL_MATURITY) {
            "계약상 만기일은 상품 조건에서 정확한 날짜를 읽어 런타임 공시로 생성해야 합니다."
        }
        require(
            (kind == InstrumentTerminationKind.ISSUER_ACCELERATION) ==
                (accelerationRecoveryRate != null),
        ) { "발행사 가속상환 템플릿에만 회수율 범위가 필요합니다." }
        accelerationRecoveryRate?.let { range ->
            require(
                range.start.isFinite() && range.endInclusive.isFinite() &&
                    range.start in 0.40..0.80 && range.endInclusive in 0.40..0.80 &&
                    range.start <= range.endInclusive,
            ) { "가속상환 회수율 범위는 40% 이상 80% 이하여야 합니다." }
        }
        val representativeTerms = InstrumentTerminationTerms(
            kind = kind,
            effectiveNotBefore = Instant.fromEpochSeconds(0),
            valuationMethod = valuationMethod,
            accelerationRecoveryRate = accelerationRecoveryRate?.start,
        )
        require(representativeTerms.semanticInvariantViolation() == null)
    }

    /** ID가 아니라 종료 사유와 상품 계약 속성으로 확률 이벤트 후보를 제한한다. */
    fun isEligibleFor(stock: StockDefinition): Boolean = when (kind) {
        InstrumentTerminationKind.CONTRACTUAL_MATURITY -> false
        InstrumentTerminationKind.ISSUER_ACCELERATION -> stock.instrumentType == InstrumentType.ETN
        InstrumentTerminationKind.OPTIONAL_CALL ->
            stock.instrumentType == InstrumentType.ETN && stock.identityProfile?.callable == true
        InstrumentTerminationKind.FUND_LIQUIDATION ->
            stock.instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
    }
}
