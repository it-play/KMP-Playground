package com.amond.kmpbook.domain.simulation.fundproduct

import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadAdvance
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadLifecycle
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadState
import com.amond.kmpbook.domain.model.fundproduct.CashCollateralizedPutSpreadTerms
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Instant

/**
 * Stateless engine for a cash benchmark plus a fully collateralized vertical put spread.
 *
 * Cash return and option-underlying return are independent deterministic inputs. At a roll, the
 * old package is closed at its current fair value, then the new short high-strike put and long
 * lower-strike put are opened. Premium execution affects cash; fair value affects the option mark.
 * The engine never adds a synthetic premium yield on top of those two entries.
 */
class CashCollateralizedPutSpreadEngine {
    fun initialState(
        terms: CashCollateralizedPutSpreadTerms,
        cashReferenceLevel: Double,
        optionReferenceLevel: Double,
        nav: Double,
        optionDiscountRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        tradingDate: LocalDate,
        at: Instant,
    ): CashCollateralizedPutSpreadState {
        require(
            cashReferenceLevel.isFinite() &&
                cashReferenceLevel in
                CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL,
        )
        require(
            optionReferenceLevel.isFinite() &&
                optionReferenceLevel in
                CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL,
        )
        require(
            nav.isFinite() &&
                nav > CashCollateralizedPutSpreadState.MIN_NAV &&
                nav <= CashCollateralizedPutSpreadState.MAX_NAV,
        )
        require(optionDiscountRateAnnual.isFinite() && optionDiscountRateAnnual in -0.10..1.0)
        require(annualizedImpliedVolatility.isFinite() && annualizedImpliedVolatility in 0.0..5.0)
        require(terms.rollCalendar.isTradingDate(tradingDate))

        val opening =
            calculateCycleOpening(
                preTradeNav = nav,
                optionReferenceLevel = optionReferenceLevel,
                terms = terms,
                optionDiscountRateAnnual = optionDiscountRateAnnual,
                annualizedImpliedVolatility = annualizedImpliedVolatility,
                bootstrapSnapshot = true,
            )
        return CashCollateralizedPutSpreadState(
            productId = terms.productId,
            cashBenchmarkRef = terms.cashBenchmarkRef,
            optionReference = terms.optionReference,
            rollCalendar = terms.rollCalendar,
            currentCashReferenceLevel = cashReferenceLevel,
            currentOptionReferenceLevel = optionReferenceLevel,
            currentNav = opening.resultingNav,
            cashBalance = opening.cashBalance,
            cycleOptionReferenceLevel = optionReferenceLevel,
            navAtRoll = opening.navAtRoll,
            optionNotionalAtRoll = opening.optionNotionalAtRoll,
            maximumSettlementLossAtRoll = opening.maximumSettlementLossAtRoll,
            cycleStartedOn = tradingDate,
            remainingTradingDays = terms.tenorTradingDays,
            remainingTimeYears = terms.tenorTradingDays / TRADING_DAYS_PER_YEAR,
            lastProcessedTradingDate = null,
            longPutUnits = opening.units,
            longPutStrike = opening.longPutStrike,
            shortPutUnits = opening.units,
            shortPutStrike = opening.shortPutStrike,
            netOptionMark = opening.netOptionMark,
            cycleGrossPremiumReceived = 0.0,
            cycleGrossPremiumPaid = 0.0,
            cycleImplementationCost = 0.0,
            cumulativePremiumReceived = 0.0,
            cumulativePremiumPaid = 0.0,
            cumulativeSettlementCashFlow = 0.0,
            cumulativeImplementationCost = 0.0,
            lifecycle = CashCollateralizedPutSpreadLifecycle.ACTIVE,
            asOf = at,
            revision = 0L,
        )
    }

    /**
     * Re-denominates per-product-unit cash and option balances. Cash/option reference levels and
     * option strikes remain in their independent underlying price domains.
     */
    fun applyProductUnitAdjustment(
        state: CashCollateralizedPutSpreadState,
        quantityMultiplier: Double,
        corporateActionAccountingSequence: Long,
        at: Instant,
    ): CashCollateralizedPutSpreadState {
        require(quantityMultiplier.isFinite() && quantityMultiplier > 0.0)
        require(corporateActionAccountingSequence > 0L)
        require(
            state.lastCorporateActionAccountingSequence == null ||
                corporateActionAccountingSequence > state.lastCorporateActionAccountingSequence,
        )
        require(at >= state.asOf)
        require(state.lifecycle == CashCollateralizedPutSpreadLifecycle.ACTIVE) {
            "Only an active cash-collateralized option package can be re-denominated."
        }
        fun perUnit(value: Double): Double = value / quantityMultiplier
        return state.copy(
            currentNav = perUnit(state.currentNav),
            cashBalance = perUnit(state.cashBalance),
            navAtRoll = perUnit(state.navAtRoll),
            optionNotionalAtRoll = perUnit(state.optionNotionalAtRoll),
            maximumSettlementLossAtRoll = perUnit(state.maximumSettlementLossAtRoll),
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

    fun advance(input: CashCollateralizedPutSpreadAdvanceInput): CashCollateralizedPutSpreadAdvance {
        val previous = input.state
        if (previous.lifecycle == CashCollateralizedPutSpreadLifecycle.VALUE_EXHAUSTED) {
            val frozen = previous.copy(asOf = input.to)
            return CashCollateralizedPutSpreadAdvance(
                state = frozen,
                previousNav = previous.currentNav,
                productLogReturn = 0.0,
                rolled = false,
                cashReturnContribution = 0.0,
                optionMarkChangeBeforeSettlement = 0.0,
                settlementCashFlow = 0.0,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                openingOptionMark = 0.0,
                implementationCost = 0.0,
                terminalLiquidationAdjustment = 0.0,
            )
        }
        if (previous.lifecycle == CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION) {
            val nextCashReferenceLevel =
                previous.currentCashReferenceLevel * exp(input.cashBenchmarkTotalLogReturn)
            val nextOptionReferenceLevel =
                previous.currentOptionReferenceLevel * exp(input.optionUnderlyingTotalLogReturn)
            require(
                nextCashReferenceLevel.isFinite() && nextCashReferenceLevel in
                    CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..
                    CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL,
            )
            require(
                nextOptionReferenceLevel.isFinite() && nextOptionReferenceLevel in
                    CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..
                    CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL,
            )
            val nextCashBalance = previous.cashBalance * exp(input.cashBenchmarkTotalLogReturn)
            require(nextCashBalance.isFinite() && nextCashBalance >= 0.0)
            val next = previous.copy(
                currentCashReferenceLevel = nextCashReferenceLevel,
                currentOptionReferenceLevel = nextOptionReferenceLevel,
                currentNav = nextCashBalance,
                cashBalance = nextCashBalance,
                asOf = input.to,
            )
            return CashCollateralizedPutSpreadAdvance(
                state = next,
                previousNav = previous.currentNav,
                productLogReturn = ln(next.currentNav / previous.currentNav),
                rolled = false,
                cashReturnContribution = nextCashBalance - previous.cashBalance,
                optionMarkChangeBeforeSettlement = 0.0,
                settlementCashFlow = 0.0,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                openingOptionMark = 0.0,
                implementationCost = 0.0,
                terminalLiquidationAdjustment = 0.0,
            )
        }
        if (previous.lifecycle == CashCollateralizedPutSpreadLifecycle.ACTIVE) {
            validateCycleAgainstTerms(previous, input.terms)
        }

        val nextCashReferenceLevel =
            previous.currentCashReferenceLevel * exp(input.cashBenchmarkTotalLogReturn)
        val nextOptionReferenceLevel =
            previous.currentOptionReferenceLevel * exp(input.optionUnderlyingTotalLogReturn)
        require(
            nextCashReferenceLevel.isFinite() &&
                nextCashReferenceLevel in
                CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL,
        )
        require(
            nextOptionReferenceLevel.isFinite() &&
                nextOptionReferenceLevel in
                CashCollateralizedPutSpreadState.MIN_REFERENCE_LEVEL..CashCollateralizedPutSpreadState.MAX_REFERENCE_LEVEL,
        )

        val nextCashBalance = previous.cashBalance * exp(input.cashBenchmarkTotalLogReturn)
        require(nextCashBalance.isFinite() && nextCashBalance >= 0.0)
        val cashReturnContribution = nextCashBalance - previous.cashBalance
        val elapsedTimeYears =
            (previous.remainingTimeYears - input.elapsedYearFraction).coerceAtLeast(0.0)
        val consumesTradingClose =
            input.tradingCloseAtEnd &&
                previous.lastProcessedTradingDate != input.referenceTradingDate
        val nextRemainingTradingDays =
            if (consumesTradingClose) {
                (previous.remainingTradingDays - 1).coerceAtLeast(0)
            } else {
                previous.remainingTradingDays
            }
        val nextTimeYears =
            if (consumesTradingClose && nextRemainingTradingDays == 0) {
                0.0
            } else {
                elapsedTimeYears
            }
        val nextOptionMark =
            optionMark(
                state = previous,
                optionReferenceLevel = nextOptionReferenceLevel,
                optionDiscountRateAnnual = input.optionDiscountRateAnnual,
                annualizedImpliedVolatility =
                    effectiveVolatility(
                        input.terms,
                        input.annualizedImpliedVolatility,
                    ),
                timeYears = nextTimeYears,
            )
        val optionMarkChange = nextOptionMark - previous.netOptionMark
        val rawMarkedNav = nextCashBalance + nextOptionMark
        val nextLastProcessedDate =
            if (consumesTradingClose) {
                input.referenceTradingDate
            } else {
                previous.lastProcessedTradingDate
            }

        if (!rawMarkedNav.isFinite() || rawMarkedNav <= CashCollateralizedPutSpreadState.MIN_NAV) {
            val exhausted =
                exhaustedState(
                    state = previous,
                    cashReferenceLevel = nextCashReferenceLevel,
                    optionReferenceLevel = nextOptionReferenceLevel,
                    lastProcessedTradingDate = nextLastProcessedDate,
                    at = input.to,
                )
            return CashCollateralizedPutSpreadAdvance(
                state = exhausted,
                previousNav = previous.currentNav,
                productLogReturn = ln(exhausted.currentNav / previous.currentNav),
                rolled = false,
                cashReturnContribution = cashReturnContribution,
                optionMarkChangeBeforeSettlement = optionMarkChange,
                settlementCashFlow = 0.0,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                openingOptionMark = 0.0,
                implementationCost = 0.0,
                terminalLiquidationAdjustment = exhausted.currentNav - rawMarkedNav,
            )
        }
        require(rawMarkedNav <= CashCollateralizedPutSpreadState.MAX_NAV)

        val shouldRoll =
            previous.lifecycle == CashCollateralizedPutSpreadLifecycle.ACTIVE && consumesTradingClose &&
                (
                    input.forceRollAtEnd ||
                        nextRemainingTradingDays <= input.terms.rollLeadTradingDays
                )
        if (!shouldRoll) {
            val marked =
                previous.copy(
                    currentCashReferenceLevel = nextCashReferenceLevel,
                    currentOptionReferenceLevel = nextOptionReferenceLevel,
                    currentNav = rawMarkedNav,
                    cashBalance = nextCashBalance,
                    remainingTradingDays = nextRemainingTradingDays,
                    remainingTimeYears = nextTimeYears,
                    lastProcessedTradingDate = nextLastProcessedDate,
                    netOptionMark = nextOptionMark,
                    asOf = input.to,
                )
            return CashCollateralizedPutSpreadAdvance(
                state = marked,
                previousNav = previous.currentNav,
                productLogReturn = ln(marked.currentNav / previous.currentNav),
                rolled = false,
                cashReturnContribution = cashReturnContribution,
                optionMarkChangeBeforeSettlement = optionMarkChange,
                settlementCashFlow = 0.0,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                openingOptionMark = 0.0,
                implementationCost = 0.0,
                terminalLiquidationAdjustment = 0.0,
            )
        }

        if (!input.allowOpeningNewCycle) {
            val settlementCashFlow = nextOptionMark
            val awaitingLiquidation = previous.copy(
                currentCashReferenceLevel = nextCashReferenceLevel,
                currentOptionReferenceLevel = nextOptionReferenceLevel,
                currentNav = rawMarkedNav,
                cashBalance = nextCashBalance + settlementCashFlow,
                cycleOptionReferenceLevel = nextOptionReferenceLevel,
                navAtRoll = 0.0,
                optionNotionalAtRoll = 0.0,
                maximumSettlementLossAtRoll = 0.0,
                cycleStartedOn = input.referenceTradingDate,
                remainingTradingDays = 0,
                remainingTimeYears = 0.0,
                lastProcessedTradingDate = nextLastProcessedDate,
                longPutUnits = 0.0,
                longPutStrike = null,
                shortPutUnits = 0.0,
                shortPutStrike = null,
                netOptionMark = 0.0,
                cycleGrossPremiumReceived = 0.0,
                cycleGrossPremiumPaid = 0.0,
                cycleImplementationCost = 0.0,
                cumulativeSettlementCashFlow =
                    previous.cumulativeSettlementCashFlow + settlementCashFlow,
                lifecycle = CashCollateralizedPutSpreadLifecycle.AWAITING_PRODUCT_LIQUIDATION,
                asOf = input.to,
                revision = previous.revision + 1L,
            )
            return CashCollateralizedPutSpreadAdvance(
                state = awaitingLiquidation,
                previousNav = previous.currentNav,
                productLogReturn = ln(awaitingLiquidation.currentNav / previous.currentNav),
                rolled = false,
                cashReturnContribution = cashReturnContribution,
                optionMarkChangeBeforeSettlement = optionMarkChange,
                settlementCashFlow = settlementCashFlow,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                openingOptionMark = 0.0,
                implementationCost = 0.0,
                terminalLiquidationAdjustment = 0.0,
            )
        }

        val prospectiveImplementationCost =
            rawMarkedNav * input.terms.premiumModel.implementationCostRatePerRoll
        if (rawMarkedNav - prospectiveImplementationCost <= CashCollateralizedPutSpreadState.MIN_NAV) {
            val exhausted =
                exhaustedState(
                    state = previous,
                    cashReferenceLevel = nextCashReferenceLevel,
                    optionReferenceLevel = nextOptionReferenceLevel,
                    lastProcessedTradingDate = nextLastProcessedDate,
                    at = input.to,
                )
            return CashCollateralizedPutSpreadAdvance(
                state = exhausted,
                previousNav = previous.currentNav,
                productLogReturn = ln(exhausted.currentNav / previous.currentNav),
                rolled = false,
                cashReturnContribution = cashReturnContribution,
                optionMarkChangeBeforeSettlement = optionMarkChange,
                settlementCashFlow = 0.0,
                grossPremiumReceived = 0.0,
                grossPremiumPaid = 0.0,
                openingOptionMark = 0.0,
                implementationCost = 0.0,
                terminalLiquidationAdjustment = exhausted.currentNav - rawMarkedNav,
            )
        }

        val settlementCashFlow = nextOptionMark
        val opened =
            openCycle(
                state = previous,
                preTradeNav = rawMarkedNav,
                currentCashReferenceLevel = nextCashReferenceLevel,
                currentOptionReferenceLevel = nextOptionReferenceLevel,
                lastProcessedTradingDate = nextLastProcessedDate,
                settlementCashFlow = settlementCashFlow,
                at = input.to,
                terms = input.terms,
                optionDiscountRateAnnual = input.optionDiscountRateAnnual,
                annualizedImpliedVolatility = input.annualizedImpliedVolatility,
                tradingDate = input.referenceTradingDate,
                incrementRevision = true,
                bootstrapSnapshot = false,
            )
        val received = opened.cumulativePremiumReceived - previous.cumulativePremiumReceived
        val paid = opened.cumulativePremiumPaid - previous.cumulativePremiumPaid
        val implementationCost =
            opened.cumulativeImplementationCost - previous.cumulativeImplementationCost
        return CashCollateralizedPutSpreadAdvance(
            state = opened,
            previousNav = previous.currentNav,
            productLogReturn = ln(opened.currentNav / previous.currentNav),
            rolled = true,
            cashReturnContribution = cashReturnContribution,
            optionMarkChangeBeforeSettlement = optionMarkChange,
            settlementCashFlow = settlementCashFlow,
            grossPremiumReceived = received,
            grossPremiumPaid = paid,
            openingOptionMark = opened.netOptionMark,
            implementationCost = implementationCost,
            terminalLiquidationAdjustment = 0.0,
        )
    }

    private fun openCycle(
        state: CashCollateralizedPutSpreadState,
        preTradeNav: Double,
        currentCashReferenceLevel: Double,
        currentOptionReferenceLevel: Double,
        lastProcessedTradingDate: LocalDate?,
        settlementCashFlow: Double,
        at: Instant,
        terms: CashCollateralizedPutSpreadTerms,
        optionDiscountRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        tradingDate: LocalDate,
        incrementRevision: Boolean,
        bootstrapSnapshot: Boolean,
    ): CashCollateralizedPutSpreadState {
        val opening =
            calculateCycleOpening(
                preTradeNav = preTradeNav,
                optionReferenceLevel = currentOptionReferenceLevel,
                terms = terms,
                optionDiscountRateAnnual = optionDiscountRateAnnual,
                annualizedImpliedVolatility = annualizedImpliedVolatility,
                bootstrapSnapshot = bootstrapSnapshot,
            )

        return state.copy(
            currentCashReferenceLevel = currentCashReferenceLevel,
            currentOptionReferenceLevel = currentOptionReferenceLevel,
            currentNav = opening.resultingNav,
            cashBalance = opening.cashBalance,
            cycleOptionReferenceLevel = currentOptionReferenceLevel,
            navAtRoll = opening.navAtRoll,
            optionNotionalAtRoll = opening.optionNotionalAtRoll,
            maximumSettlementLossAtRoll = opening.maximumSettlementLossAtRoll,
            cycleStartedOn = tradingDate,
            remainingTradingDays = terms.tenorTradingDays,
            remainingTimeYears = terms.tenorTradingDays / TRADING_DAYS_PER_YEAR,
            lastProcessedTradingDate = lastProcessedTradingDate,
            longPutUnits = opening.units,
            longPutStrike = opening.longPutStrike,
            shortPutUnits = opening.units,
            shortPutStrike = opening.shortPutStrike,
            netOptionMark = opening.netOptionMark,
            cycleGrossPremiumReceived = opening.grossPremiumReceived,
            cycleGrossPremiumPaid = opening.grossPremiumPaid,
            cycleImplementationCost = opening.implementationCost,
            cumulativePremiumReceived =
                state.cumulativePremiumReceived + opening.grossPremiumReceived,
            cumulativePremiumPaid = state.cumulativePremiumPaid + opening.grossPremiumPaid,
            cumulativeSettlementCashFlow =
                state.cumulativeSettlementCashFlow + settlementCashFlow,
            cumulativeImplementationCost =
                state.cumulativeImplementationCost + opening.implementationCost,
            lifecycle = CashCollateralizedPutSpreadLifecycle.ACTIVE,
            asOf = at,
            revision = state.revision + if (incrementRevision) 1L else 0L,
        )
    }

    private fun calculateCycleOpening(
        preTradeNav: Double,
        optionReferenceLevel: Double,
        terms: CashCollateralizedPutSpreadTerms,
        optionDiscountRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        bootstrapSnapshot: Boolean,
    ): CashCollateralizedPutSpreadCycleOpening {
        val tenorYears = terms.tenorTradingDays / TRADING_DAYS_PER_YEAR
        val shortStrike = optionReferenceLevel * terms.shortPutStrikeMoneyness
        val longStrike = optionReferenceLevel * terms.longPutStrikeMoneyness
        val spreadWidth = shortStrike - longStrike
        val effectiveVolatility = effectiveVolatility(terms, annualizedImpliedVolatility)
        val fairLongPerUnit =
            OptionValuation.put(
                optionReferenceLevel,
                longStrike,
                optionDiscountRateAnnual,
                effectiveVolatility,
                tenorYears,
            )
        val fairShortPerUnit =
            OptionValuation.put(
                optionReferenceLevel,
                shortStrike,
                optionDiscountRateAnnual,
                effectiveVolatility,
                tenorYears,
            )
        require(fairShortPerUnit + amountTolerance(fairShortPerUnit) >= fairLongPerUnit)

        val targetMaximumLoss = preTradeNav * terms.maximumSettlementLossRatio
        val implementationCost =
            if (bootstrapSnapshot) {
                0.0
            } else {
                preTradeNav * terms.premiumModel.implementationCostRatePerRoll
            }
        val availableAfterFixedCost = preTradeNav - implementationCost
        require(availableAfterFixedCost > CashCollateralizedPutSpreadState.MIN_NAV)
        val premiumCashPerMaximumLoss =
            if (bootstrapSnapshot) {
                0.0
            } else {
                (
                    fairShortPerUnit * terms.premiumModel.soldPremiumCaptureRatio -
                        fairLongPerUnit * terms.premiumModel.purchasedPremiumCostRatio
                ) / spreadWidth
            }
        val markPerMaximumLoss = (fairLongPerUnit - fairShortPerUnit) / spreadWidth
        var maximumLoss = targetMaximumLoss
        val collateralDenominator = 1.0 - premiumCashPerMaximumLoss
        if (!bootstrapSnapshot && collateralDenominator > 0.0) {
            maximumLoss =
                minOf(
                    maximumLoss,
                    availableAfterFixedCost / collateralDenominator * LIMIT_SAFETY_FACTOR,
                )
        }
        val navChangePerMaximumLoss = premiumCashPerMaximumLoss + markPerMaximumLoss
        if (!bootstrapSnapshot && navChangePerMaximumLoss < 0.0) {
            maximumLoss =
                minOf(
                    maximumLoss,
                    (availableAfterFixedCost - CashCollateralizedPutSpreadState.MIN_NAV) /
                        -navChangePerMaximumLoss * LIMIT_SAFETY_FACTOR,
                )
        }
        require(maximumLoss.isFinite() && maximumLoss > 0.0)

        val units = maximumLoss / spreadWidth
        val fairLongPremium = units * fairLongPerUnit
        val fairShortPremium = units * fairShortPerUnit
        val netOptionMark = fairLongPremium - fairShortPremium
        val grossPremiumReceived =
            if (bootstrapSnapshot) {
                0.0
            } else {
                fairShortPremium * terms.premiumModel.soldPremiumCaptureRatio
            }
        val grossPremiumPaid =
            if (bootstrapSnapshot) {
                0.0
            } else {
                fairLongPremium * terms.premiumModel.purchasedPremiumCostRatio
            }
        val cashBalance =
            if (bootstrapSnapshot) {
                preTradeNav - netOptionMark
            } else {
                preTradeNav + grossPremiumReceived - grossPremiumPaid - implementationCost
            }
        val resultingNav = cashBalance + netOptionMark
        require(resultingNav.isFinite() && resultingNav > CashCollateralizedPutSpreadState.MIN_NAV)
        require(resultingNav <= CashCollateralizedPutSpreadState.MAX_NAV)
        require(cashBalance + amountTolerance(cashBalance) >= maximumLoss) {
            "새 풋스프레드의 최대 정산손실은 개시 시점 현금담보를 넘을 수 없습니다."
        }
        return CashCollateralizedPutSpreadCycleOpening(
            navAtRoll = preTradeNav,
            units = units,
            longPutStrike = longStrike,
            shortPutStrike = shortStrike,
            optionNotionalAtRoll = units * optionReferenceLevel,
            maximumSettlementLossAtRoll = maximumLoss,
            netOptionMark = netOptionMark,
            grossPremiumReceived = grossPremiumReceived,
            grossPremiumPaid = grossPremiumPaid,
            implementationCost = implementationCost,
            cashBalance = cashBalance,
            resultingNav = resultingNav,
        )
    }

    private fun optionMark(
        state: CashCollateralizedPutSpreadState,
        optionReferenceLevel: Double,
        optionDiscountRateAnnual: Double,
        annualizedImpliedVolatility: Double,
        timeYears: Double,
    ): Double =
        state.longPutUnits *
            OptionValuation.put(
                optionReferenceLevel,
                requireNotNull(state.longPutStrike),
                optionDiscountRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            ) -
            state.shortPutUnits *
            OptionValuation.put(
                optionReferenceLevel,
                requireNotNull(state.shortPutStrike),
                optionDiscountRateAnnual,
                annualizedImpliedVolatility,
                timeYears,
            )

    private fun validateCycleAgainstTerms(
        state: CashCollateralizedPutSpreadState,
        terms: CashCollateralizedPutSpreadTerms,
    ) {
        val expectedLongStrike =
            state.cycleOptionReferenceLevel * terms.longPutStrikeMoneyness
        val expectedShortStrike =
            state.cycleOptionReferenceLevel * terms.shortPutStrikeMoneyness
        require(approximatelyEqual(requireNotNull(state.longPutStrike), expectedLongStrike))
        require(approximatelyEqual(requireNotNull(state.shortPutStrike), expectedShortStrike))
        require(
            state.maximumSettlementLossAtRoll <=
                state.navAtRoll * terms.maximumSettlementLossRatio +
                amountTolerance(state.navAtRoll),
        )
    }

    private fun exhaustedState(
        state: CashCollateralizedPutSpreadState,
        cashReferenceLevel: Double,
        optionReferenceLevel: Double,
        lastProcessedTradingDate: LocalDate?,
        at: Instant,
    ): CashCollateralizedPutSpreadState =
        state.copy(
            currentCashReferenceLevel = cashReferenceLevel,
            currentOptionReferenceLevel = optionReferenceLevel,
            currentNav = CashCollateralizedPutSpreadState.MIN_NAV,
            cashBalance = CashCollateralizedPutSpreadState.MIN_NAV,
            navAtRoll = 0.0,
            optionNotionalAtRoll = 0.0,
            maximumSettlementLossAtRoll = 0.0,
            remainingTradingDays = 0,
            remainingTimeYears = 0.0,
            lastProcessedTradingDate = lastProcessedTradingDate,
            longPutUnits = 0.0,
            longPutStrike = null,
            shortPutUnits = 0.0,
            shortPutStrike = null,
            netOptionMark = 0.0,
            lifecycle = CashCollateralizedPutSpreadLifecycle.VALUE_EXHAUSTED,
            asOf = at,
        )

    private fun effectiveVolatility(
        terms: CashCollateralizedPutSpreadTerms,
        annualizedImpliedVolatility: Double,
    ): Double =
        (annualizedImpliedVolatility * terms.premiumModel.impliedVolatilityMultiplier)
            .coerceIn(0.0, MAX_EFFECTIVE_VOLATILITY)

    private fun approximatelyEqual(
        left: Double,
        right: Double,
    ): Boolean = abs(left - right) <= ACCOUNTING_EPSILON * maxOf(1.0, abs(left), abs(right))

    private fun amountTolerance(value: Double): Double = ACCOUNTING_EPSILON * maxOf(1.0, abs(value))

    companion object {
        private const val TRADING_DAYS_PER_YEAR: Double = 252.0
        private const val MAX_EFFECTIVE_VOLATILITY: Double = 5.0
        private const val LIMIT_SAFETY_FACTOR: Double = 1.0 - 1e-12
        private const val ACCOUNTING_EPSILON: Double = 1e-9
    }
}
