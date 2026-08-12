package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.CreditQuality
import com.amond.kmpbook.domain.model.reference.FixedIncomeInstrumentKind
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceAdvance
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferencePosition
import com.amond.kmpbook.domain.model.reference.YieldCurveSnapshot
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p

/**
 * 명목·실질곡선, 롤다운, 신용스프레드, 이자, 분산 기대손실과 확정 부도손실을 한 번씩만 합성한다.
 *
 * 부도 발생 확률과 구성 교체는 방법론 계층의 책임이다. 이 엔진은 전달된 시장 프레임과
 * 확정 사건만 소비하므로 반복 순서나 난수 호출에 의존하지 않는다.
 */
class FixedIncomeReferenceEngine {
    fun advance(input: FixedIncomeAdvanceInput): FixedIncomeReferenceAdvance {
        val defaultLossById = input.defaultEvents.associate { it.assetId to it.lossFraction }
        val assetReturns = input.state.positions.associate { position ->
            val expectedLoss = input.expectedCreditLossRatesAnnual.getValue(position.assetId) *
                input.elapsedYearFraction
            val realizedLoss = defaultLossById[position.assetId] ?: 0.0
            position.assetId to positionLogReturn(
                position,
                input,
                (expectedLoss + realizedLoss).coerceIn(0.0, 1.0),
            )
        }
        val grossFactor = input.state.positions.sumOf { position ->
            position.currentWeight * exp(assetReturns.getValue(position.assetId))
        }.coerceAtLeast(MIN_GROSS_FACTOR)
        val grossLogReturn = ln(grossFactor).coerceIn(-MAX_LOG_RETURN, MAX_LOG_RETURN)

        val nextWeightNumerators = input.state.positions.associate { position ->
            position.assetId to position.currentWeight * exp(assetReturns.getValue(position.assetId))
        }
        val nextMarketValues = input.state.positions.associate { position ->
            position.assetId to (
                position.dirtyMarketValue * exp(assetReturns.getValue(position.assetId))
                ).coerceIn(MIN_MARKET_VALUE, MAX_MARKET_VALUE)
        }
        val totalNextWeight = nextWeightNumerators.values.sum().coerceAtLeast(MIN_GROSS_FACTOR)
        val nextPositions = input.state.positions.map { position ->
            position.copy(
                currentWeight = nextWeightNumerators.getValue(position.assetId) / totalNextWeight,
                dirtyMarketValue = nextMarketValues.getValue(position.assetId),
                remainingMaturityYears = (
                    position.remainingMaturityYears - input.elapsedYearFraction
                    ).coerceAtLeast(0.0),
                inflationIndexRatio = if (position.kind == FixedIncomeInstrumentKind.INFLATION_LINKED) {
                    position.inflationIndexRatio *
                        exp(input.inflationAccrualRateAnnual * input.elapsedYearFraction)
                } else {
                    position.inflationIndexRatio
                },
            )
        }
        val nextState = input.state.copy(
                positions = nextPositions,
                nominalCurves = input.currentNominalCurves,
                realCurves = input.currentRealCurves,
                creditSpreads = input.currentCreditSpreads,
                asOf = input.to,
            )
        return FixedIncomeReferenceAdvance(
            state = nextState,
            grossReferenceLogReturn = grossLogReturn,
            annualIncomeYield = nextState.estimatedAnnualIncomeYield,
            assetLogReturns = assetReturns,
        )
    }

    private fun positionLogReturn(
        position: FixedIncomeReferencePosition,
        input: FixedIncomeAdvanceInput,
        defaultLossFraction: Double,
    ): Double {
        val oldNominalCurve = input.state.nominalCurves.getValue(position.currency)
        val newNominalCurve = input.currentNominalCurves.getValue(position.currency)
        val oldCurve = curveFor(position, oldNominalCurve, input.state.realCurves[position.currency])
        val newCurve = curveFor(position, newNominalCurve, input.currentRealCurves[position.currency])
        val oldRiskFreeYield = oldCurve.rateAtYears(position.remainingMaturityYears)
        val nextMaturity = (position.remainingMaturityYears - input.elapsedYearFraction).coerceAtLeast(0.0)
        val newRiskFreeYield = newCurve.rateAtYears(nextMaturity)
        val oldSpread = input.state.creditSpreads.getValue(position.currency)
            .annualSpreads.getValue(position.creditQuality)
        val newSpread = input.currentCreditSpreads.getValue(position.currency)
            .annualSpreads.getValue(position.creditQuality)
        val rateDelta = newRiskFreeYield - oldRiskFreeYield
        val spreadDelta = newSpread - oldSpread
        // 상품 가격에는 이자·쿠폰을 별도 분배 재원으로 적립한다. 여기서는 총수익률에서
        // 현금이자를 뺀 pull-to-par/roll-down만 반영해 같은 이자를 두 번 더하지 않는다.
        val carry = (
            totalReturnCarry(position, oldRiskFreeYield, oldSpread, oldNominalCurve) -
                annualCashIncome(position, oldNominalCurve)
            ) * input.elapsedYearFraction
        val inflation = if (position.kind == FixedIncomeInstrumentKind.INFLATION_LINKED) {
            input.inflationAccrualRateAnnual * input.elapsedYearFraction
        } else {
            0.0
        }
        val ratePrice = -position.modifiedDurationYears * rateDelta +
            0.5 * position.convexityYearsSquared * rateDelta * rateDelta
        val creditPrice = -position.spreadDurationYears * spreadDelta
        val simpleReturn = carry + inflation + ratePrice + creditPrice - defaultLossFraction
        return ln1p(simpleReturn.coerceIn(MIN_SIMPLE_RETURN, MAX_SIMPLE_RETURN))
    }

    private fun curveFor(
        position: FixedIncomeReferencePosition,
        nominal: YieldCurveSnapshot,
        real: YieldCurveSnapshot?,
    ): YieldCurveSnapshot = if (position.kind == FixedIncomeInstrumentKind.INFLATION_LINKED) {
        requireNotNull(real) { "물가연동채 수익률 계산에는 실질 금리곡선이 필요합니다." }
    } else {
        nominal
    }

    private fun totalReturnCarry(
        position: FixedIncomeReferencePosition,
        riskFreeYield: Double,
        creditSpread: Double,
        nominalCurve: YieldCurveSnapshot,
    ): Double = when (position.kind) {
        FixedIncomeInstrumentKind.CASH_EQUIVALENT -> nominalCurve.rateAtYears(0.25)
        FixedIncomeInstrumentKind.FLOATING_RATE,
        FixedIncomeInstrumentKind.CLO_TRANCHE,
        -> maxOf(
            nominalCurve.rateAtYears(0.25) + position.floatingSpreadAnnual,
            position.floatingRateFloorAnnual,
        )
        else -> riskFreeYield + creditSpread
    }

    private fun annualCashIncome(
        position: FixedIncomeReferencePosition,
        nominalCurve: YieldCurveSnapshot,
    ): Double = when (position.kind) {
        FixedIncomeInstrumentKind.CASH_EQUIVALENT -> nominalCurve.rateAtYears(0.25)
        FixedIncomeInstrumentKind.FLOATING_RATE,
        FixedIncomeInstrumentKind.CLO_TRANCHE,
        -> maxOf(
            nominalCurve.rateAtYears(0.25) + position.floatingSpreadAnnual,
            position.floatingRateFloorAnnual,
        )
        else -> position.couponRateAnnual
    }

    companion object {
        private const val MIN_GROSS_FACTOR: Double = 1e-12
        private const val MIN_MARKET_VALUE: Double = 1e-9
        private const val MAX_MARKET_VALUE: Double = 1e24
        private const val MIN_SIMPLE_RETURN: Double = -0.999999999
        private const val MAX_SIMPLE_RETURN: Double = 10.0
        private const val MAX_LOG_RETURN: Double = 2.5
    }
}
