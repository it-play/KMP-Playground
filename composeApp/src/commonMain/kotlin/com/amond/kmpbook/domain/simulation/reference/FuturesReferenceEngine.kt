package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.FuturesAllocationMode
import com.amond.kmpbook.domain.model.reference.FuturesAllocationRecord
import com.amond.kmpbook.domain.model.reference.FuturesContractQuote
import com.amond.kmpbook.domain.model.reference.FuturesCurveSnapshot
import com.amond.kmpbook.domain.model.reference.FuturesPriceReturnConvention
import com.amond.kmpbook.domain.model.reference.FuturesReferenceAdvance
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesRollCalendar
import com.amond.kmpbook.domain.model.reference.FuturesRollRecord
import com.amond.kmpbook.domain.model.reference.FuturesSleeveState
import com.amond.kmpbook.domain.model.reference.FuturesSleeveTerms
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Pure, fully-collateralized futures reference engine.
 *
 * Contract marks create the excess return. Collateral interest is then added once. Moving notional
 * from front to deferred contracts has no immediate P&L; contango/backwardation appears as those
 * held contracts converge through later marks. [FuturesRollRecord.normalizedCurveBasis] is therefore
 * diagnostic and is never added again as a return.
 */
class FuturesReferenceEngine {
    fun initialState(
        terms: FuturesReferenceTerms,
        curvesBySleeveId: Map<String, FuturesCurveSnapshot>,
        referenceTradingDates: Map<FuturesRollCalendar, LocalDate>,
        referenceLevel: Double,
        at: Instant,
    ): FuturesReferenceState {
        require(referenceLevel.isFinite() && referenceLevel > 0.0)
        val sleeveIds = terms.sleeves.mapTo(linkedSetOf(), FuturesSleeveTerms::sleeveId)
        require(curvesBySleeveId.keys == sleeveIds)
        val calendars = terms.sleeves.mapTo(linkedSetOf(), FuturesSleeveTerms::rollCalendar)
        require(referenceTradingDates.keys == calendars)
        require(referenceTradingDates.all { (calendar, date) -> calendar.isTradingDate(date) })
        val states = terms.sleeves.map { sleeveTerms ->
            val curve = curvesBySleeveId.getValue(sleeveTerms.sleeveId)
            validateCurve(curve, sleeveTerms, at)
            val tradingDate = referenceTradingDates.getValue(sleeveTerms.rollCalendar)
            val eligible = eligibleContracts(curve, sleeveTerms, tradingDate)
            require(eligible.size >= 2) {
                "${sleeveTerms.sleeveId} curve needs at least two eligible unexpired contracts."
            }
            val front = eligible[0]
            val next = eligible[1]
            FuturesSleeveState(
                sleeveId = sleeveTerms.sleeveId,
                curveId = sleeveTerms.curveId,
                assetClass = sleeveTerms.assetClass,
                rollCalendar = sleeveTerms.rollCalendar,
                priceReturnConvention = sleeveTerms.priceReturnConvention,
                fixedPriceReturnNotional = sleeveTerms.fixedPriceReturnNotional,
                currentWeight = sleeveTerms.targetWeight,
                targetWeight = sleeveTerms.targetWeight,
                currentSpotLevel = curve.currentSpotLevel,
                frontContractId = front.contractId,
                frontExpiryDate = front.expiryDate,
                frontPrice = front.price,
                frontContractWeight = 1.0,
                nextContractId = next.contractId,
                nextExpiryDate = next.expiryDate,
                nextPrice = next.price,
                nextContractWeight = 0.0,
                lastRollTradingDate = null,
            )
        }
        return FuturesReferenceState(
            benchmarkRef = terms.benchmarkRef,
            baseCurrency = terms.baseCurrency,
            portfolioStyle = terms.portfolioStyle,
            allocationMode = terms.allocationMode,
            currentReferenceLevel = referenceLevel,
            sleeves = states,
            revision = 0L,
            asOf = at,
        )
    }

    fun advance(input: FuturesAdvanceInput): FuturesReferenceAdvance {
        val termsById = input.terms.sleeves.associateBy(FuturesSleeveTerms::sleeveId)
        val sleeveFactors = linkedMapOf<String, Double>()
        val spotFactors = linkedMapOf<String, Double>()
        val markedSleeves = input.state.sleeves.map { previous ->
            val sleeveTerms = termsById.getValue(previous.sleeveId)
            validateStateBinding(previous, sleeveTerms)
            val curve = input.curvesBySleeveId.getValue(previous.sleeveId)
            validateCurve(curve, sleeveTerms, input.to)
            val quotes = curve.contracts.associateBy(FuturesContractQuote::contractId)
            val front = requireNotNull(quotes[previous.frontContractId]) {
                "Curve ${curve.curveId} omitted held front ${previous.frontContractId}."
            }
            val next = requireNotNull(quotes[previous.nextContractId]) {
                "Curve ${curve.curveId} omitted held deferred ${previous.nextContractId}."
            }
            require(front.expiryDate == previous.frontExpiryDate)
            require(next.expiryDate == previous.nextExpiryDate)
            validatePrice(front.price, sleeveTerms)
            validatePrice(next.price, sleeveTerms)
            val frontReturn = contractSimpleReturn(previous.frontPrice, front.price, sleeveTerms)
            val nextReturn = contractSimpleReturn(previous.nextPrice, next.price, sleeveTerms)
            val contractReturn =
                previous.frontContractWeight * frontReturn +
                    previous.nextContractWeight * nextReturn
            val sleeveFactor = 1.0 + sleeveTerms.notionalExposureRatio * contractReturn
            val spotFactor = 1.0 + sleeveTerms.notionalExposureRatio *
                (curve.currentSpotLevel / previous.currentSpotLevel - 1.0)
            require(sleeveFactor.isFinite() && sleeveFactor > MIN_GROSS_FACTOR)
            require(spotFactor.isFinite() && spotFactor > MIN_GROSS_FACTOR)
            sleeveFactors[previous.sleeveId] = sleeveFactor
            spotFactors[previous.sleeveId] = spotFactor
            previous.copy(
                currentSpotLevel = curve.currentSpotLevel,
                frontPrice = front.price,
                nextPrice = next.price,
            )
        }

        val futuresExcessFactor = input.state.sleeves.sumOf { previous ->
            previous.currentWeight * sleeveFactors.getValue(previous.sleeveId)
        }
        val spotProxyFactor = input.state.sleeves.sumOf { previous ->
            previous.currentWeight * spotFactors.getValue(previous.sleeveId)
        }
        require(futuresExcessFactor.isFinite() && futuresExcessFactor > MIN_GROSS_FACTOR)
        require(spotProxyFactor.isFinite() && spotProxyFactor > MIN_GROSS_FACTOR)
        var sleeves = markedSleeves.map { sleeve ->
            sleeve.copy(
                currentWeight =
                    sleeve.currentWeight * sleeveFactors.getValue(sleeve.sleeveId) /
                        futuresExcessFactor,
            )
        }

        var revision = input.state.revision
        val rollRecords = mutableListOf<FuturesRollRecord>()
        sleeves = sleeves.map { marked ->
            val sleeveTerms = termsById.getValue(marked.sleeveId)
            val calendar = sleeveTerms.rollCalendar
            val tradingDate = input.referenceTradingDates.getValue(calendar)
            val consumesClose =
                calendar in input.tradingClosesAtEnd && marked.lastRollTradingDate != tradingDate
            if (!consumesClose) return@map marked
            val daysUntilExpiry = calendar.tradingDaysAfterThrough(tradingDate, marked.frontExpiryDate)
            val isRollDate =
                tradingDate >= marked.frontExpiryDate ||
                    daysUntilExpiry <= sleeveTerms.rollStartTradingDaysBeforeExpiry
            if (!isRollDate) return@map marked

            val curve = input.curvesBySleeveId.getValue(marked.sleeveId)
            val quotes = curve.contracts.associateBy(FuturesContractQuote::contractId)
            val frontQuote = requireNotNull(quotes[marked.frontContractId])
            val nextQuote = requireNotNull(quotes[marked.nextContractId])
            val regularTransfer = 1.0 / sleeveTerms.rollWindowTradingDays.toDouble()
            val transfer = if (tradingDate >= marked.frontExpiryDate) {
                marked.frontContractWeight
            } else {
                minOf(marked.frontContractWeight, regularTransfer)
            }
            require(transfer > WEIGHT_EPSILON)
            val frontAfter = (marked.frontContractWeight - transfer).coerceAtLeast(0.0)
            val promoted = frontAfter <= WEIGHT_EPSILON
            val successor = if (promoted) {
                eligibleContracts(curve, sleeveTerms, tradingDate)
                    .firstOrNull { it.expiryDate > marked.nextExpiryDate }
                    ?: error("${marked.sleeveId} curve needs a successor after ${marked.nextContractId}.")
            } else {
                null
            }
            revision += 1L
            val record = FuturesRollRecord(
                id = "futures-roll:${input.state.benchmarkRef.benchmarkId}:" +
                    "v${input.state.benchmarkRef.version}:${marked.sleeveId}:$tradingDate:r$revision",
                benchmarkRef = input.state.benchmarkRef,
                sleeveId = marked.sleeveId,
                rollTradingDate = tradingDate,
                fromContractId = marked.frontContractId,
                toContractId = marked.nextContractId,
                transferredContractWeight = transfer,
                frontWeightBefore = marked.frontContractWeight,
                frontWeightAfter = if (promoted) 0.0 else frontAfter,
                normalizedCurveBasis = normalizedCurveBasis(
                    frontQuote.price,
                    nextQuote.price,
                    sleeveTerms,
                ),
                promotedDeferredToFront = promoted,
                successorContractId = successor?.contractId,
                effectiveAt = input.to,
                revision = revision,
            )
            rollRecords += record
            if (promoted) {
                requireNotNull(successor)
                validatePrice(successor.price, sleeveTerms)
                marked.copy(
                    frontContractId = nextQuote.contractId,
                    frontExpiryDate = nextQuote.expiryDate,
                    frontPrice = nextQuote.price,
                    frontContractWeight = 1.0,
                    nextContractId = successor.contractId,
                    nextExpiryDate = successor.expiryDate,
                    nextPrice = successor.price,
                    nextContractWeight = 0.0,
                    lastRollTradingDate = tradingDate,
                )
            } else {
                marked.copy(
                    frontContractWeight = frontAfter,
                    nextContractWeight = 1.0 - frontAfter,
                    lastRollTradingDate = tradingDate,
                )
            }
        }

        var allocationRecord: FuturesAllocationRecord? = null
        if (input.rebalanceAtEnd) {
            val desiredWeights = when (input.terms.allocationMode) {
                FuturesAllocationMode.STATIC_TARGETS ->
                    input.terms.sleeves.associate { it.sleeveId to it.targetWeight }
                FuturesAllocationMode.EXTERNAL_TARGETS -> requireNotNull(input.externalTargetWeights)
            }
            val weightsBefore = sleeves.associate { it.sleeveId to it.currentWeight }
            val materiallyChanged = sleeves.any { sleeve ->
                abs(sleeve.currentWeight - desiredWeights.getValue(sleeve.sleeveId)) > WEIGHT_EPSILON ||
                    abs(sleeve.targetWeight - desiredWeights.getValue(sleeve.sleeveId)) > WEIGHT_EPSILON
            }
            if (materiallyChanged) {
                revision += 1L
                sleeves = sleeves.map { sleeve ->
                    val weight = desiredWeights.getValue(sleeve.sleeveId)
                    sleeve.copy(currentWeight = weight, targetWeight = weight)
                }
                allocationRecord = FuturesAllocationRecord(
                    id = "futures-allocation:${input.state.benchmarkRef.benchmarkId}:" +
                        "v${input.state.benchmarkRef.version}:${input.to.epochSeconds}:r$revision",
                    benchmarkRef = input.state.benchmarkRef,
                    weightsBefore = weightsBefore,
                    weightsAfter = desiredWeights,
                    effectiveAt = input.to,
                    revision = revision,
                )
            }
        }

        val collateralLogReturn =
            input.cashRateAnnual * input.terms.collateralRatio *
                input.terms.collateralYieldParticipation * input.elapsedYearFraction
        val futuresExcessLogReturn = ln(futuresExcessFactor)
        val spotProxyLogReturn = ln(spotProxyFactor)
        val grossLogReturn = futuresExcessLogReturn + collateralLogReturn
        val nextReferenceLevel = input.state.currentReferenceLevel * exp(grossLogReturn)
        require(nextReferenceLevel.isFinite() && nextReferenceLevel > 0.0)
        val nextState = input.state.copy(
            currentReferenceLevel = nextReferenceLevel,
            sleeves = sleeves.sortedBy(FuturesSleeveState::sleeveId),
            revision = revision,
            asOf = input.to,
        )
        return FuturesReferenceAdvance(
            state = nextState,
            grossReferenceLogReturn = grossLogReturn,
            spotProxyLogReturn = spotProxyLogReturn,
            curveAndRollLogReturn = futuresExcessLogReturn - spotProxyLogReturn,
            collateralLogReturn = collateralLogReturn,
            rollRecords = rollRecords,
            allocationRecord = allocationRecord,
        )
    }

    private fun validateStateBinding(state: FuturesSleeveState, terms: FuturesSleeveTerms) {
        require(state.sleeveId == terms.sleeveId && state.curveId == terms.curveId)
        require(state.assetClass == terms.assetClass)
        require(state.rollCalendar == terms.rollCalendar)
        require(state.priceReturnConvention == terms.priceReturnConvention)
        require(state.fixedPriceReturnNotional == terms.fixedPriceReturnNotional)
        require(state.frontExpiryDate.month.number in terms.eligibleDeliveryMonths)
        require(state.nextExpiryDate.month.number in terms.eligibleDeliveryMonths)
    }

    private fun validateCurve(
        curve: FuturesCurveSnapshot,
        terms: FuturesSleeveTerms,
        at: Instant,
    ) {
        require(curve.sleeveId == terms.sleeveId && curve.curveId == terms.curveId)
        require(curve.asOf == at)
    }

    private fun eligibleContracts(
        curve: FuturesCurveSnapshot,
        terms: FuturesSleeveTerms,
        tradingDate: LocalDate,
    ): List<FuturesContractQuote> = curve.contracts.filter { quote ->
        quote.expiryDate > tradingDate &&
            quote.expiryDate.month.number in terms.eligibleDeliveryMonths &&
            isValidPrice(quote.price, terms)
    }

    private fun validatePrice(price: Double, terms: FuturesSleeveTerms) {
        require(isValidPrice(price, terms)) {
            "${terms.sleeveId} quote violates ${terms.priceReturnConvention}."
        }
    }

    private fun isValidPrice(price: Double, terms: FuturesSleeveTerms): Boolean =
        price.isFinite() && when (terms.priceReturnConvention) {
            FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO -> price > 0.0
            FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL -> true
        }

    private fun contractSimpleReturn(
        previousPrice: Double,
        currentPrice: Double,
        terms: FuturesSleeveTerms,
    ): Double = when (terms.priceReturnConvention) {
        FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO -> {
            require(previousPrice > 0.0 && currentPrice > 0.0)
            currentPrice / previousPrice - 1.0
        }
        FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL ->
            (currentPrice - previousPrice) / requireNotNull(terms.fixedPriceReturnNotional)
    }

    private fun normalizedCurveBasis(
        frontPrice: Double,
        nextPrice: Double,
        terms: FuturesSleeveTerms,
    ): Double = when (terms.priceReturnConvention) {
        FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO -> {
            require(frontPrice > 0.0 && nextPrice > 0.0)
            ln(nextPrice / frontPrice)
        }
        FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL ->
            (nextPrice - frontPrice) / requireNotNull(terms.fixedPriceReturnNotional)
    }

    companion object {
        private const val MIN_GROSS_FACTOR: Double = 1e-12
        private const val WEIGHT_EPSILON: Double = 1e-10
    }
}
