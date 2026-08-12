package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyAdvance
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyKind
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyLifecycle
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyState
import com.amond.kmpbook.domain.model.fundproduct.OptionStrategyTerms
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Stateless option-overlay accounting engine.
 *
 * It marks the reference holding, collateral and each option leg exactly once. Rolls first convert
 * the old option mark into cash and then open the newly specified package. No symbol-specific
 * contract value, random state or hidden premium yield exists in this engine.
 */
class OptionStrategyEngine {
    fun initialState(
        terms: OptionStrategyTerms,
        referenceLevel: Double,
        nav: Double,
        cashRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        tradingDate: LocalDate,
        at: Instant,
    ): OptionStrategyState {
        require(referenceLevel.isFinite() && referenceLevel > 0.0)
        require(nav.isFinite() && nav in OptionStrategyState.MIN_NAV..OptionStrategyState.MAX_NAV)
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
        require(annualizedImpliedVolatility.isFinite() && annualizedImpliedVolatility in 0.0..5.0)

        val uninvested = OptionStrategyState(
            productId = terms.productId,
            strategyKind = terms.kind,
            rollCalendar = terms.rollCalendar,
            currentReferenceLevel = referenceLevel,
            currentNav = nav,
            underlyingUnits = 0.0,
            cashBalance = nav,
            cycleReferenceLevel = referenceLevel,
            optionNotionalAtRoll = 0.0,
            cycleStartedOn = tradingDate,
            remainingTradingDays = 0,
            remainingTimeYears = 0.0,
            lastProcessedTradingDate = null,
            longCallUnits = 0.0,
            longCallStrike = null,
            shortCallUnits = 0.0,
            shortCallStrike = null,
            longPutUnits = 0.0,
            longPutStrike = null,
            shortPutUnits = 0.0,
            shortPutStrike = null,
            netOptionMark = 0.0,
            cycleGrossPremiumReceived = 0.0,
            cycleGrossPremiumPaid = 0.0,
            cycleImplementationCost = 0.0,
            cumulativePremiumReceived = 0.0,
            cumulativePremiumPaid = 0.0,
            cumulativeSettlementCashFlow = 0.0,
            cumulativeImplementationCost = 0.0,
            lifecycle = OptionStrategyLifecycle.ACTIVE,
            asOf = at,
            revision = 0L,
        )
        val opened = openCycle(
            state = uninvested,
            terms = terms,
            cashRateAnnual = cashRateAnnual,
            annualizedImpliedVolatility = annualizedImpliedVolatility,
            tradingDate = tradingDate,
            incrementRevision = false,
            bootstrapSnapshot = true,
        )
        require(opened.lifecycle == OptionStrategyLifecycle.ACTIVE) {
            "초기 옵션 패키지의 비용이 상품 NAV를 소진했습니다. 계약·평가 가정을 확인하세요."
        }
        return opened
    }

    /**
     * Re-denominates every per-product-unit balance while leaving the option-underlying price
     * domain (reference levels and strikes) unchanged. Roll revision and dates are unaffected.
     */
    fun applyProductUnitAdjustment(
        state: OptionStrategyState,
        quantityMultiplier: Double,
        corporateActionAccountingSequence: Long,
        at: Instant,
    ): OptionStrategyState {
        require(quantityMultiplier.isFinite() && quantityMultiplier > 0.0)
        require(corporateActionAccountingSequence > 0L)
        require(
            state.lastCorporateActionAccountingSequence == null ||
                corporateActionAccountingSequence > state.lastCorporateActionAccountingSequence,
        )
        require(at >= state.asOf)
        require(state.lifecycle == OptionStrategyLifecycle.ACTIVE) {
            "Only an active option package can be re-denominated."
        }
        fun perUnit(value: Double): Double = value / quantityMultiplier
        return state.copy(
            currentNav = perUnit(state.currentNav),
            underlyingUnits = perUnit(state.underlyingUnits),
            cashBalance = perUnit(state.cashBalance),
            optionNotionalAtRoll = perUnit(state.optionNotionalAtRoll),
            longCallUnits = perUnit(state.longCallUnits),
            shortCallUnits = perUnit(state.shortCallUnits),
            longPutUnits = perUnit(state.longPutUnits),
            shortPutUnits = perUnit(state.shortPutUnits),
            netOptionMark = perUnit(state.netOptionMark),
            cycleGrossPremiumReceived = perUnit(state.cycleGrossPremiumReceived),
            cycleGrossPremiumPaid = perUnit(state.cycleGrossPremiumPaid),
            cycleImplementationCost = perUnit(state.cycleImplementationCost),
            cumulativePremiumReceived = perUnit(state.cumulativePremiumReceived),
            cumulativePremiumPaid = perUnit(state.cumulativePremiumPaid),
            cumulativeSettlementCashFlow = perUnit(state.cumulativeSettlementCashFlow),
            cumulativeImplementationCost = perUnit(state.cumulativeImplementationCost),
            cumulativeUnitAdjustmentFactor =
                state.cumulativeUnitAdjustmentFactor * quantityMultiplier,
            lastCorporateActionAccountingSequence = corporateActionAccountingSequence,
            asOf = at,
        )
    }

    fun advance(input: OptionStrategyAdvanceInput): OptionStrategyAdvance {
        val previous = input.state
        if (previous.lifecycle == OptionStrategyLifecycle.VALUE_EXHAUSTED) {
            return OptionStrategyAdvance(
                state = previous.copy(asOf = input.to),
                productLogReturn = 0.0,
                rolled = false,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                settlementCashFlow = 0.0,
                implementationCost = 0.0,
            )
        }
        if (previous.lifecycle == OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION) {
            val nextReferenceLevel = previous.currentReferenceLevel * exp(input.underlyingTotalLogReturn)
            require(nextReferenceLevel.isFinite() && nextReferenceLevel > 0.0)
            val nextCashBalance = previous.cashBalance * exp(
                input.cashRateAnnual * input.elapsedYearFraction,
            )
            require(nextCashBalance.isFinite())
            val nextNav = previous.underlyingUnits * nextReferenceLevel + nextCashBalance
            require(nextNav.isFinite() && nextNav in OptionStrategyState.MIN_NAV..OptionStrategyState.MAX_NAV)
            val next = previous.copy(
                currentReferenceLevel = nextReferenceLevel,
                currentNav = nextNav,
                cashBalance = nextCashBalance,
                asOf = input.to,
            )
            return OptionStrategyAdvance(
                state = next,
                productLogReturn = ln(next.currentNav / previous.currentNav),
                rolled = false,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                settlementCashFlow = 0.0,
                implementationCost = 0.0,
            )
        }

        val nextReferenceLevel = previous.currentReferenceLevel * exp(input.underlyingTotalLogReturn)
        require(nextReferenceLevel.isFinite() && nextReferenceLevel > 0.0)
        val nextCashBalance = previous.cashBalance * exp(input.cashRateAnnual * input.elapsedYearFraction)
        require(nextCashBalance.isFinite())
        val elapsedTimeYears =
            (previous.remainingTimeYears - input.elapsedYearFraction).coerceAtLeast(0.0)
        val consumesTradingClose =
            input.tradingCloseAtEnd && previous.lastProcessedTradingDate != input.referenceTradingDate
        val nextRemainingTradingDays = if (consumesTradingClose) {
            (previous.remainingTradingDays - 1).coerceAtLeast(0)
        } else {
            previous.remainingTradingDays
        }
        // The close that consumes the final tenor day is the contractual expiry boundary.
        val nextTimeYears = if (consumesTradingClose && nextRemainingTradingDays == 0) {
            0.0
        } else {
            elapsedTimeYears
        }
        val nextOptionMark = optionMark(
            state = previous,
            referenceLevel = nextReferenceLevel,
            cashRateAnnual = input.cashRateAnnual,
            annualizedImpliedVolatility = effectiveVolatility(input),
            timeYears = nextTimeYears,
        )
        val nextNav = previous.underlyingUnits * nextReferenceLevel + nextCashBalance + nextOptionMark
        val nextLastProcessedDate = if (consumesTradingClose) {
            input.referenceTradingDate
        } else {
            previous.lastProcessedTradingDate
        }

        if (!nextNav.isFinite() || nextNav <= OptionStrategyState.MIN_NAV) {
            val exhausted = exhaustedState(
                state = previous,
                referenceLevel = nextReferenceLevel,
                lastProcessedTradingDate = nextLastProcessedDate,
                at = input.to,
                incrementRevision = true,
            )
            return OptionStrategyAdvance(
                state = exhausted,
                productLogReturn = ln(OptionStrategyState.MIN_NAV / previous.currentNav),
                rolled = false,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                settlementCashFlow = 0.0,
                implementationCost = 0.0,
            )
        }
        require(nextNav <= OptionStrategyState.MAX_NAV)

        val marked = previous.copy(
            currentReferenceLevel = nextReferenceLevel,
            currentNav = nextNav,
            cashBalance = nextCashBalance,
            remainingTradingDays = nextRemainingTradingDays,
            remainingTimeYears = nextTimeYears,
            lastProcessedTradingDate = nextLastProcessedDate,
            netOptionMark = nextOptionMark,
            asOf = input.to,
        )
        val shouldRoll =
            previous.lifecycle == OptionStrategyLifecycle.ACTIVE && consumesTradingClose &&
                (input.forceRollAtEnd || nextRemainingTradingDays <= input.terms.rollLeadTradingDays)
        if (!shouldRoll) {
            return OptionStrategyAdvance(
                state = marked,
                productLogReturn = ln(marked.currentNav / previous.currentNav),
                rolled = false,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                settlementCashFlow = 0.0,
                implementationCost = 0.0,
            )
        }

        val settlementCashFlow = marked.netOptionMark
        val settled = marked.copy(
            cashBalance = marked.cashBalance + settlementCashFlow,
            longCallUnits = 0.0,
            longCallStrike = null,
            shortCallUnits = 0.0,
            shortCallStrike = null,
            longPutUnits = 0.0,
            longPutStrike = null,
            shortPutUnits = 0.0,
            shortPutStrike = null,
            netOptionMark = 0.0,
            cumulativeSettlementCashFlow =
                marked.cumulativeSettlementCashFlow + settlementCashFlow,
        )
        if (!input.allowOpeningNewCycle) {
            val awaitingLiquidation = settled.copy(
                currentNav = marked.currentNav,
                cycleReferenceLevel = nextReferenceLevel,
                optionNotionalAtRoll = 0.0,
                cycleStartedOn = input.referenceTradingDate,
                remainingTradingDays = 0,
                remainingTimeYears = 0.0,
                cycleGrossPremiumReceived = 0.0,
                cycleGrossPremiumPaid = 0.0,
                cycleImplementationCost = 0.0,
                lifecycle = OptionStrategyLifecycle.AWAITING_PRODUCT_LIQUIDATION,
                revision = previous.revision + 1L,
            )
            return OptionStrategyAdvance(
                state = awaitingLiquidation,
                productLogReturn = ln(awaitingLiquidation.currentNav / previous.currentNav),
                rolled = false,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                settlementCashFlow = settlementCashFlow,
                implementationCost = 0.0,
            )
        }
        val opened = openCycle(
            state = settled,
            terms = input.terms,
            cashRateAnnual = input.cashRateAnnual,
            annualizedImpliedVolatility = input.annualizedImpliedVolatility,
            tradingDate = input.referenceTradingDate,
            incrementRevision = true,
            bootstrapSnapshot = false,
        )
        val finalState = if (opened.currentNav <= OptionStrategyState.MIN_NAV) {
            exhaustedState(
                state = opened,
                referenceLevel = nextReferenceLevel,
                lastProcessedTradingDate = input.referenceTradingDate,
                at = input.to,
                incrementRevision = false,
            )
        } else {
            opened
        }
        return OptionStrategyAdvance(
            state = finalState,
            productLogReturn = ln(finalState.currentNav / previous.currentNav),
            rolled = true,
            grossPremiumReceived =
                finalState.cumulativePremiumReceived - previous.cumulativePremiumReceived,
            grossPremiumPaid = finalState.cumulativePremiumPaid - previous.cumulativePremiumPaid,
            settlementCashFlow = settlementCashFlow,
            implementationCost =
                finalState.cumulativeImplementationCost - previous.cumulativeImplementationCost,
        )
    }

    private fun openCycle(
        state: OptionStrategyState,
        terms: OptionStrategyTerms,
        cashRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        tradingDate: LocalDate,
        incrementRevision: Boolean,
        bootstrapSnapshot: Boolean,
    ): OptionStrategyState {
        require(state.netOptionMark == 0.0)
        require(
            state.longCallUnits == 0.0 && state.shortCallUnits == 0.0 &&
                state.longPutUnits == 0.0 && state.shortPutUnits == 0.0,
        )
        val spot = state.currentReferenceLevel
        val preTradeNav = state.currentNav
        val targetUnderlyingValue = when (terms.kind) {
            OptionStrategyKind.COVERED_CALL,
            OptionStrategyKind.BUFFERED_PUT_SPREAD,
            -> preTradeNav
            OptionStrategyKind.OPTION_INCOME ->
                preTradeNav * requireNotNull(terms.optionIncome).coreEquityAllocation
        }
        val targetUnderlyingUnits = targetUnderlyingValue / spot
        val cashAfterUnderlyingTrade =
            state.cashBalance + state.underlyingUnits * spot - targetUnderlyingValue
        val tenorYears = terms.tenorTradingDays / TRADING_DAYS_PER_YEAR

        var longCallUnits = 0.0
        var longCallStrike: Double? = null
        var shortCallUnits = 0.0
        var shortCallStrike: Double? = null
        var longPutUnits = 0.0
        var longPutStrike: Double? = null
        var shortPutUnits = 0.0
        var shortPutStrike: Double? = null
        val optionNotional: Double
        when (terms.kind) {
            OptionStrategyKind.COVERED_CALL -> {
                val detail = requireNotNull(terms.coveredCall)
                shortCallUnits = targetUnderlyingUnits * detail.overwriteRatio
                shortCallStrike = spot * detail.callStrikeMoneyness
                optionNotional = shortCallUnits * spot
            }
            OptionStrategyKind.OPTION_INCOME -> {
                val detail = requireNotNull(terms.optionIncome)
                optionNotional = preTradeNav * detail.optionIncomeAllocation
                val baseUnits = optionNotional / spot
                if (detail.upsideParticipation > 0.0) {
                    longCallUnits = baseUnits * detail.upsideParticipation
                    longCallStrike = spot
                    shortCallUnits = longCallUnits
                    shortCallStrike = spot * detail.callStrikeMoneyness
                }
                if (detail.downsideParticipation > 0.0) {
                    shortPutUnits = baseUnits * detail.downsideParticipation
                    shortPutStrike = spot
                }
            }
            OptionStrategyKind.BUFFERED_PUT_SPREAD -> {
                val detail = requireNotNull(terms.bufferedPutSpread)
                optionNotional = preTradeNav * detail.outcomeNotionalRatio
                val baseUnits = optionNotional / spot
                longPutUnits = baseUnits
                longPutStrike = spot * detail.longPutStrikeMoneyness
                if (detail.downsideParticipationBeyondBuffer > 0.0) {
                    shortPutUnits = baseUnits * detail.downsideParticipationBeyondBuffer
                    shortPutStrike =
                        spot * (detail.longPutStrikeMoneyness - detail.downsideBufferFraction)
                }
                shortCallUnits = baseUnits
                shortCallStrike = spot * (1.0 + detail.upsideCapFraction)
            }
        }

        val effectiveVolatility = effectiveVolatility(terms, annualizedImpliedVolatility)
        val fairLongPremium =
            optionValue(longCallUnits, longCallStrike, true, spot, cashRateAnnual, effectiveVolatility, tenorYears) +
                optionValue(longPutUnits, longPutStrike, false, spot, cashRateAnnual, effectiveVolatility, tenorYears)
        val fairShortPremium =
            optionValue(shortCallUnits, shortCallStrike, true, spot, cashRateAnnual, effectiveVolatility, tenorYears) +
                optionValue(shortPutUnits, shortPutStrike, false, spot, cashRateAnnual, effectiveVolatility, tenorYears)
        val netOptionMark = fairLongPremium - fairShortPremium
        // A new campaign observes an already operating package. Its historical premium execution
        // and roll cost did not occur inside the simulation, so bootstrap stores a fair-value
        // residual cash balance and begins all flow counters at zero. Subsequent rolls apply the
        // disclosed capture/cost assumptions normally.
        val grossPremiumReceived = if (bootstrapSnapshot) {
            0.0
        } else {
            fairShortPremium * terms.premiumModel.soldPremiumCaptureRatio
        }
        val grossPremiumPaid = if (bootstrapSnapshot) {
            0.0
        } else {
            fairLongPremium * terms.premiumModel.purchasedPremiumCostRatio
        }
        val implementationCost = if (bootstrapSnapshot) {
            0.0
        } else {
            preTradeNav * terms.premiumModel.implementationCostRatePerRoll
        }
        val cashBalance = if (bootstrapSnapshot) {
            preTradeNav - targetUnderlyingValue - netOptionMark
        } else {
            cashAfterUnderlyingTrade + grossPremiumReceived - grossPremiumPaid - implementationCost
        }
        val resultingNav = if (bootstrapSnapshot) {
            preTradeNav
        } else {
            targetUnderlyingValue + cashBalance + netOptionMark
        }
        val nextRevision = state.revision + if (incrementRevision) 1L else 0L
        val cumulativeReceived = state.cumulativePremiumReceived + grossPremiumReceived
        val cumulativePaid = state.cumulativePremiumPaid + grossPremiumPaid
        val cumulativeCost = state.cumulativeImplementationCost + implementationCost

        if (!resultingNav.isFinite() || resultingNav <= OptionStrategyState.MIN_NAV) {
            return state.copy(
                currentNav = OptionStrategyState.MIN_NAV,
                underlyingUnits = 0.0,
                cashBalance = OptionStrategyState.MIN_NAV,
                cycleReferenceLevel = spot,
                optionNotionalAtRoll = 0.0,
                cycleStartedOn = tradingDate,
                remainingTradingDays = 0,
                remainingTimeYears = 0.0,
                longCallUnits = 0.0,
                longCallStrike = null,
                shortCallUnits = 0.0,
                shortCallStrike = null,
                longPutUnits = 0.0,
                longPutStrike = null,
                shortPutUnits = 0.0,
                shortPutStrike = null,
                netOptionMark = 0.0,
                cycleGrossPremiumReceived = grossPremiumReceived,
                cycleGrossPremiumPaid = grossPremiumPaid,
                cycleImplementationCost = implementationCost,
                cumulativePremiumReceived = cumulativeReceived,
                cumulativePremiumPaid = cumulativePaid,
                cumulativeImplementationCost = cumulativeCost,
                lifecycle = OptionStrategyLifecycle.VALUE_EXHAUSTED,
                revision = nextRevision,
            )
        }
        require(resultingNav <= OptionStrategyState.MAX_NAV)
        return state.copy(
            currentNav = resultingNav,
            underlyingUnits = targetUnderlyingUnits,
            cashBalance = cashBalance,
            cycleReferenceLevel = spot,
            optionNotionalAtRoll = optionNotional,
            cycleStartedOn = tradingDate,
            remainingTradingDays = terms.tenorTradingDays,
            remainingTimeYears = tenorYears,
            longCallUnits = longCallUnits,
            longCallStrike = longCallStrike,
            shortCallUnits = shortCallUnits,
            shortCallStrike = shortCallStrike,
            longPutUnits = longPutUnits,
            longPutStrike = longPutStrike,
            shortPutUnits = shortPutUnits,
            shortPutStrike = shortPutStrike,
            netOptionMark = netOptionMark,
            cycleGrossPremiumReceived = grossPremiumReceived,
            cycleGrossPremiumPaid = grossPremiumPaid,
            cycleImplementationCost = implementationCost,
            cumulativePremiumReceived = cumulativeReceived,
            cumulativePremiumPaid = cumulativePaid,
            cumulativeImplementationCost = cumulativeCost,
            lifecycle = OptionStrategyLifecycle.ACTIVE,
            revision = nextRevision,
        )
    }

    private fun optionMark(
        state: OptionStrategyState,
        referenceLevel: Double,
        cashRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        timeYears: Double,
    ): Double =
        optionValue(
            state.longCallUnits,
            state.longCallStrike,
            true,
            referenceLevel,
            cashRateAnnual,
            annualizedImpliedVolatility,
            timeYears,
        ) +
            optionValue(
                state.longPutUnits,
                state.longPutStrike,
                false,
                referenceLevel,
                cashRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            ) -
            optionValue(
                state.shortCallUnits,
                state.shortCallStrike,
                true,
                referenceLevel,
                cashRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            ) -
            optionValue(
                state.shortPutUnits,
                state.shortPutStrike,
                false,
                referenceLevel,
                cashRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            )

    private fun optionValue(
        units: Double,
        strike: Double?,
        isCall: Boolean,
        referenceLevel: Double,
        cashRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        timeYears: Double,
    ): Double {
        if (units == 0.0) return 0.0
        val requiredStrike = requireNotNull(strike)
        val unitValue = if (isCall) {
            OptionValuation.call(
                referenceLevel,
                requiredStrike,
                cashRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            )
        } else {
            OptionValuation.put(
                referenceLevel,
                requiredStrike,
                cashRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            )
        }
        return units * unitValue
    }

    private fun effectiveVolatility(input: OptionStrategyAdvanceInput): Double =
        effectiveVolatility(input.terms, input.annualizedImpliedVolatility)

    private fun effectiveVolatility(
        terms: OptionStrategyTerms,
        annualizedImpliedVolatility: Double,
    ): Double =
        (annualizedImpliedVolatility * terms.premiumModel.impliedVolatilityMultiplier)
            .coerceIn(0.0, MAX_EFFECTIVE_VOLATILITY)

    private fun exhaustedState(
        state: OptionStrategyState,
        referenceLevel: Double,
        lastProcessedTradingDate: LocalDate?,
        at: Instant,
        incrementRevision: Boolean,
    ): OptionStrategyState = state.copy(
        currentReferenceLevel = referenceLevel,
        currentNav = OptionStrategyState.MIN_NAV,
        underlyingUnits = 0.0,
        cashBalance = OptionStrategyState.MIN_NAV,
        optionNotionalAtRoll = 0.0,
        remainingTradingDays = 0,
        remainingTimeYears = 0.0,
        lastProcessedTradingDate = lastProcessedTradingDate,
        longCallUnits = 0.0,
        longCallStrike = null,
        shortCallUnits = 0.0,
        shortCallStrike = null,
        longPutUnits = 0.0,
        longPutStrike = null,
        shortPutUnits = 0.0,
        shortPutStrike = null,
        netOptionMark = 0.0,
        lifecycle = OptionStrategyLifecycle.VALUE_EXHAUSTED,
        asOf = at,
        revision = state.revision + if (incrementRevision) 1L else 0L,
    )

    companion object {
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val MAX_EFFECTIVE_VOLATILITY: Double = 5.0
    }
}
