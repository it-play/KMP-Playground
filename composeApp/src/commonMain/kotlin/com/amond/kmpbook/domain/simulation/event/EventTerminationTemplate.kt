package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.instrument.InstrumentType
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationKind
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationTerms
import com.amond.kmpbook.domain.model.listing.termination.InstrumentTerminationValuationMethod
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
            (kind == InstrumentTerminationKind.CREDIT_DEFAULT) ==
                (accelerationRecoveryRate != null),
        ) { "ETN 신용사건 템플릿에만 회수율 범위가 필요합니다." }
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
        InstrumentTerminationKind.CREDIT_DEFAULT ->
            stock.instrumentType == InstrumentType.ETN &&
                stock.fundProductProfile?.etnProductTerms
                    ?.accelerationTerms?.creditDefaultCausesAcceleration == true
        InstrumentTerminationKind.ISSUER_ACCELERATION ->
            stock.instrumentType == InstrumentType.ETN &&
                stock.fundProductProfile?.etnProductTerms
                    ?.accelerationTerms?.issuerMayAccelerate == true
        InstrumentTerminationKind.OPTIONAL_CALL ->
            stock.instrumentType == InstrumentType.ETN &&
                stock.fundProductProfile?.etnProductTerms?.callTerms?.issuerCallable == true
        InstrumentTerminationKind.FUND_LIQUIDATION ->
            stock.instrumentType in setOf(InstrumentType.ETF, InstrumentType.CLOSED_END_FUND)
    }
}
