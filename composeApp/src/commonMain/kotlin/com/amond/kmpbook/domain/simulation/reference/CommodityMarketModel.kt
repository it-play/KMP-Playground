package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.reference.CommodityAssetClass
import com.amond.kmpbook.domain.model.reference.CommodityContractExpiryRule
import com.amond.kmpbook.domain.model.reference.CommodityMarketModelParameters
import com.amond.kmpbook.domain.model.reference.CommodityReferenceBook
import com.amond.kmpbook.domain.model.reference.CommoditySpotInitialization
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesAllocationMode
import com.amond.kmpbook.domain.model.reference.FuturesContractQuote
import com.amond.kmpbook.domain.model.reference.FuturesCurveSnapshot
import com.amond.kmpbook.domain.model.reference.FuturesInitialization
import com.amond.kmpbook.domain.model.reference.FuturesPriceReturnConvention
import com.amond.kmpbook.domain.model.reference.FuturesReferenceState
import com.amond.kmpbook.domain.model.reference.FuturesReferenceTerms
import com.amond.kmpbook.domain.model.reference.FuturesRollCalendar
import com.amond.kmpbook.domain.model.reference.FuturesSleeveState
import com.amond.kmpbook.domain.model.reference.FuturesSleeveTerms
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Stateless market-data adapter for shared spot and futures references.
 *
 * All random innovations are keyed by campaign seed, typed asset/curve identity and interval. The
 * adapter never consumes an RNG stream and never sees a product symbol. Asset-class calibrations
 * are explicit model inputs, so provisional game assumptions cannot be mistaken for an official
 * futures methodology or actual future contract holdings.
 */
class CommodityMarketModel private constructor(
    campaignSeed: Long,
    parameters: Collection<CommodityMarketModelParameters>,
) {
    private val seed: Long = DeterministicRandom.mixSeed(campaignSeed, STREAM_ID)
    private val parametersByAssetClass: Map<CommodityAssetClass, CommodityMarketModelParameters>

    init {
        require(parameters.isNotEmpty())
        val grouped = parameters.groupBy(CommodityMarketModelParameters::assetClass)
        grouped.forEach { (assetClass, duplicates) ->
            require(duplicates.all { it == duplicates.first() }) {
                "Conflicting commodity market calibrations were supplied for $assetClass."
            }
        }
        parametersByAssetClass = grouped.mapValues { (_, duplicates) -> duplicates.first() }
    }

    fun initialFrame(
        spotTerms: Collection<CommoditySpotReferenceTerms>,
        futuresTerms: Collection<FuturesReferenceTerms>,
        macro: MacroEnvironment,
        at: Instant,
    ): CommodityMarketInitializationFrame {
        val spotsByRef = uniqueSpotTerms(spotTerms)
        val futuresByRef = uniqueFuturesTerms(futuresTerms)
        require(spotsByRef.isNotEmpty() || futuresByRef.isNotEmpty())
        require(spotsByRef.keys.intersect(futuresByRef.keys).isEmpty())
        validateCalibrations(spotsByRef.values, futuresByRef.values)
        val cashRate = supportedCashRate(macro.policyRate)
        val spotInitializations = spotsByRef.toSortedMap().values.map { terms ->
            CommoditySpotInitialization(
                terms = terms,
                spotLevel = parameters(terms.assetClass).initialSpotLevel,
                referenceLevel = INITIAL_REFERENCE_LEVEL,
                cashRateAnnual = cashRate,
            )
        }
        val futuresInitializations = futuresByRef.toSortedMap().values.map { terms ->
            val calendars = terms.sleeves.mapTo(linkedSetOf(), FuturesSleeveTerms::rollCalendar)
            val referenceDates = calendars.associateWith { calendar ->
                lastTradingDateOnOrBefore(calendar, localDate(calendar, at))
            }
            val curves = terms.sleeves.associate { sleeve ->
                val tradingDate = referenceDates.getValue(sleeve.rollCalendar)
                val calibration = parameters(sleeve.assetClass)
                sleeve.sleeveId to curveSnapshot(
                    sleeve = sleeve,
                    previous = null,
                    spotLevel = calibration.initialSpotLevel,
                    macro = macro,
                    tradingDate = tradingDate,
                    at = at,
                )
            }
            FuturesInitialization(
                terms = terms,
                curvesBySleeveId = curves,
                referenceTradingDates = referenceDates,
                referenceLevel = INITIAL_REFERENCE_LEVEL,
            )
        }
        return CommodityMarketInitializationFrame(
            spotInitializations = spotInitializations,
            futuresInitializations = futuresInitializations,
            asOf = at,
        )
    }

    fun advanceFrame(
        book: CommodityReferenceBook,
        spotTerms: Collection<CommoditySpotReferenceTerms>,
        futuresTerms: Collection<FuturesReferenceTerms>,
        macro: MacroEnvironment,
        from: Instant,
        to: Instant,
    ): CommodityMarketAdvanceFrame {
        require(from == book.asOf)
        val elapsed = to - from
        require(elapsed.isPositive() && elapsed <= MAX_ADVANCE_DURATION)
        val elapsedYearFraction = elapsed.inWholeMilliseconds.toDouble() / MILLISECONDS_PER_YEAR
        require(elapsedYearFraction > 0.0)
        val spotsByRef = uniqueSpotTerms(spotTerms)
        val futuresByRef = uniqueFuturesTerms(futuresTerms)
        require(spotsByRef.keys == book.spotStates.keys)
        require(futuresByRef.keys == book.futuresStates.keys)
        validateCalibrations(spotsByRef.values, futuresByRef.values)
        val cashRate = supportedCashRate(macro.policyRate)

        val spotInputs = book.spotStates.toSortedMap().map { (ref, state) ->
            val terms = spotsByRef.getValue(ref)
            val logReturn = spotLogReturn(
                calibration = parameters(terms.assetClass),
                identity = "spot:${terms.assetClass.name}",
                currentSpotLevel = state.currentSpotLevel,
                macro = macro,
                elapsedYearFraction = elapsedYearFraction,
                from = from,
                to = to,
            )
            CommoditySpotAdvanceInput(
                state = state,
                terms = terms,
                currentSpotLevel = boundedLevel(state.currentSpotLevel * exp(logReturn)),
                cashRateAnnual = cashRate,
                elapsedYearFraction = elapsedYearFraction,
                to = to,
            )
        }

        val futuresInputs = book.futuresStates.toSortedMap().map { (ref, state) ->
            futuresInput(
                state = state,
                terms = futuresByRef.getValue(ref),
                macro = macro,
                cashRate = cashRate,
                elapsedYearFraction = elapsedYearFraction,
                from = from,
                to = to,
            )
        }
        return CommodityMarketAdvanceFrame(spotInputs, futuresInputs, from, to)
    }

    private fun futuresInput(
        state: FuturesReferenceState,
        terms: FuturesReferenceTerms,
        macro: MacroEnvironment,
        cashRate: Double,
        elapsedYearFraction: Double,
        from: Instant,
        to: Instant,
    ): FuturesAdvanceInput {
        val termsById = terms.sleeves.associateBy(FuturesSleeveTerms::sleeveId)
        val calendars = terms.sleeves.mapTo(linkedSetOf(), FuturesSleeveTerms::rollCalendar)
        val closeDates = calendars.mapNotNull { calendar ->
            crossedTradingClose(calendar, from, to)?.let { date -> calendar to date }
        }.toMap()
        val referenceDates = calendars.associateWith { calendar ->
            closeDates[calendar] ?: localDate(calendar, to)
        }
        val curves = state.sleeves.associate { previous ->
            val sleeve = termsById.getValue(previous.sleeveId)
            val logReturn = spotLogReturn(
                calibration = parameters(sleeve.assetClass),
                identity = "curve:${sleeve.curveId}",
                currentSpotLevel = previous.currentSpotLevel,
                macro = macro,
                elapsedYearFraction = elapsedYearFraction,
                from = from,
                to = to,
            )
            val spotLevel = boundedLevel(previous.currentSpotLevel * exp(logReturn))
            previous.sleeveId to curveSnapshot(
                sleeve = sleeve,
                previous = previous,
                spotLevel = spotLevel,
                macro = macro,
                tradingDate = referenceDates.getValue(sleeve.rollCalendar),
                at = to,
            )
        }
        val allocationCalendar = allocationCalendar(terms)
        val allocationDate = closeDates[allocationCalendar]
        val rebalanceAtEnd = allocationDate != null &&
            isFirstTradingDateOfMonth(allocationCalendar, allocationDate)
        val externalTargets = if (
            rebalanceAtEnd && terms.allocationMode == FuturesAllocationMode.EXTERNAL_TARGETS
        ) {
            externalAllocationTargets(terms, curves, macro, requireNotNull(allocationDate))
        } else {
            null
        }
        return FuturesAdvanceInput(
            state = state,
            terms = terms,
            curvesBySleeveId = curves,
            cashRateAnnual = cashRate,
            elapsedYearFraction = elapsedYearFraction,
            referenceTradingDates = referenceDates,
            tradingClosesAtEnd = closeDates.keys,
            rebalanceAtEnd = rebalanceAtEnd,
            externalTargetWeights = externalTargets,
            to = to,
        )
    }

    private fun curveSnapshot(
        sleeve: FuturesSleeveTerms,
        previous: FuturesSleeveState?,
        spotLevel: Double,
        macro: MacroEnvironment,
        tradingDate: LocalDate,
        at: Instant,
    ): FuturesCurveSnapshot {
        val expiriesById = linkedMapOf<String, LocalDate>()
        previous?.let { state ->
            expiriesById[state.frontContractId] = state.frontExpiryDate
            expiriesById[state.nextContractId] = state.nextExpiryDate
        }
        var deliveryYear = tradingDate.year
        var deliveryMonth = tradingDate.month.number
        var scannedMonths = 0
        while (
            expiriesById.values.count { it > tradingDate } < TARGET_FUTURE_CONTRACTS &&
            scannedMonths < MAX_MONTH_SCAN
        ) {
            if (deliveryMonth in sleeve.eligibleDeliveryMonths) {
                val expiry = contractExpiry(
                    year = deliveryYear,
                    month = deliveryMonth,
                    calendar = sleeve.rollCalendar,
                    rule = parameters(sleeve.assetClass).expiryRule,
                )
                if (expiry > tradingDate) {
                    expiriesById.putIfAbsent(
                        contractId(sleeve.curveId, deliveryYear, deliveryMonth),
                        expiry,
                    )
                }
            }
            deliveryMonth += 1
            if (deliveryMonth > 12) {
                deliveryMonth = 1
                deliveryYear += 1
            }
            scannedMonths += 1
        }
        require(expiriesById.size >= 2) { "${sleeve.sleeveId} could not build a usable curve." }
        require(expiriesById.size <= MAX_CURVE_CONTRACTS)
        val contracts = expiriesById.map { (contractId, expiryDate) ->
            FuturesContractQuote(
                contractId = contractId,
                expiryDate = expiryDate,
                price = contractPrice(
                    sleeve = sleeve,
                    contractId = contractId,
                    expiryDate = expiryDate,
                    spotLevel = spotLevel,
                    macro = macro,
                    tradingDate = tradingDate,
                ),
            )
        }.sortedWith(compareBy<FuturesContractQuote> { it.expiryDate }.thenBy { it.contractId })
        return FuturesCurveSnapshot(
            sleeveId = sleeve.sleeveId,
            curveId = sleeve.curveId,
            currentSpotLevel = spotLevel,
            contracts = contracts,
            sourceId = MARKET_MODEL_SOURCE_ID,
            asOf = at,
        )
    }

    private fun contractPrice(
        sleeve: FuturesSleeveTerms,
        contractId: String,
        expiryDate: LocalDate,
        spotLevel: Double,
        macro: MacroEnvironment,
        tradingDate: LocalDate,
    ): Double {
        val calibration = parameters(sleeve.assetClass)
        val timeToExpiry = (
            expiryDate.toEpochDays() - tradingDate.toEpochDays()
            ).coerceAtLeast(0).toDouble() / DAYS_PER_YEAR
        val basis = annualizedCurveBasis(sleeve, macro, tradingDate)
        return when (sleeve.priceReturnConvention) {
            FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO ->
                (spotLevel * exp(basis * timeToExpiry)).coerceIn(MIN_POSITIVE_LEVEL, MAX_LEVEL)
            FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL -> {
                val daysUntilExpiry = sleeve.rollCalendar.tradingDaysAfterThrough(
                    tradingDate,
                    expiryDate,
                )
                val proximity = (
                    (NEGATIVE_TAIL_WINDOW_TRADING_DAYS - daysUntilExpiry).toDouble() /
                        NEGATIVE_TAIL_WINDOW_TRADING_DAYS.toDouble()
                    ).coerceIn(0.0, 1.0)
                val normalizedStress = if (
                    macro.liquidityStress > calibration.negativePriceStressThreshold &&
                    calibration.negativePriceStressScale > 0.0
                ) {
                    (macro.liquidityStress - calibration.negativePriceStressThreshold) /
                        (1.0 - calibration.negativePriceStressThreshold)
                } else {
                    0.0
                }
                val trigger = if (normalizedStress > 0.0) {
                    DeterministicRandom.keyed(
                        seed,
                        "commodity-negative-tail:${sleeve.curveId}:$contractId:$tradingDate",
                    ).nextDouble() < normalizedStress
                } else {
                    false
                }
                val dislocation = if (trigger) {
                    spotLevel * calibration.negativePriceStressScale * normalizedStress * proximity
                } else {
                    0.0
                }
                (spotLevel * (1.0 + basis * timeToExpiry) - dislocation)
                    .coerceIn(-MAX_SIGNED_PRICE, MAX_SIGNED_PRICE)
            }
        }
    }

    private fun annualizedCurveBasis(
        sleeve: FuturesSleeveTerms,
        macro: MacroEnvironment,
        tradingDate: LocalDate,
    ): Double {
        val calibration = parameters(sleeve.assetClass)
        val monthlyShock = DeterministicRandom.keyed(
            seed,
            "commodity-curve-basis:${sleeve.curveId}:${tradingDate.year}:" +
                tradingDate.month.number,
        ).nextGaussian()
        return (
            calibration.baseAnnualizedCurveBasis +
                calibration.curveBasisPolicyRateLoading * macro.policyRate +
                calibration.curveBasisInflationLoading * macro.inflationSurprise +
                calibration.curveBasisVolatility * monthlyShock
            ).coerceIn(MIN_ANNUALIZED_BASIS, MAX_ANNUALIZED_BASIS)
    }

    private fun externalAllocationTargets(
        terms: FuturesReferenceTerms,
        curves: Map<String, FuturesCurveSnapshot>,
        macro: MacroEnvironment,
        allocationDate: LocalDate,
    ): Map<String, Double> {
        val raw = terms.sleeves.associate { sleeve ->
            val calibration = parameters(sleeve.assetClass)
            val curve = curves.getValue(sleeve.sleeveId)
            val eligible = curve.contracts.filter { it.expiryDate > allocationDate }
            require(eligible.size >= 2)
            val front = eligible[0]
            val next = eligible[1]
            val normalizedCarry = when (sleeve.priceReturnConvention) {
                FuturesPriceReturnConvention.POSITIVE_PRICE_RATIO -> -ln(next.price / front.price)
                FuturesPriceReturnConvention.SIGNED_CHANGE_OVER_FIXED_NOTIONAL ->
                    -(next.price - front.price) / requireNotNull(sleeve.fixedPriceReturnNotional)
            }.coerceIn(-1.0, 1.0)
            val macroTilt = (
                calibration.inflationSurpriseLoading * macro.inflationSurprise +
                    calibration.growthSurpriseLoading * macro.growthSurprise +
                    calibration.riskSentimentLoading * macro.riskSentiment * .10 +
                    calibration.liquidityStressLoading * macro.liquidityStress * .10
                ).coerceIn(-1.5, 1.5)
            val keyedTilt = DeterministicRandom.keyed(
                seed,
                "commodity-allocation:${terms.benchmarkRef}:" +
                    "${sleeve.sleeveId}:${allocationDate.year}:${allocationDate.month.number}",
            ).nextGaussian() * ALLOCATION_SCORE_NOISE
            val score = (macroTilt * ALLOCATION_MACRO_SCALE +
                normalizedCarry * ALLOCATION_CARRY_SCALE + keyedTilt).coerceIn(-3.0, 3.0)
            sleeve.sleeveId to sleeve.targetWeight * exp(score)
        }
        val total = raw.values.sum()
        require(total.isFinite() && total > 0.0)
        return raw.mapValues { (_, value) -> value / total }
    }

    private fun spotLogReturn(
        calibration: CommodityMarketModelParameters,
        identity: String,
        currentSpotLevel: Double,
        macro: MacroEnvironment,
        elapsedYearFraction: Double,
        from: Instant,
        to: Instant,
    ): Double {
        val commonRandom = DeterministicRandom.keyed(
            seed,
            "commodity-common:${calibration.assetClass}:" +
                "${from.epochSeconds}:${to.epochSeconds}",
        ).nextGaussian()
        val specificRandom = DeterministicRandom.keyed(
            seed,
            "commodity-specific:$identity:${from.epochSeconds}:${to.epochSeconds}",
        ).nextGaussian()
        val macroImpulse = (
            calibration.inflationSurpriseLoading * macro.inflationSurprise +
                calibration.growthSurpriseLoading * macro.growthSurprise +
                calibration.riskSentimentLoading * macro.riskSentiment * .10 +
                calibration.liquidityStressLoading * macro.liquidityStress * .10
            ).coerceIn(-MAX_MACRO_IMPULSE, MAX_MACRO_IMPULSE)
        val diffusion = calibration.annualSpotVolatility * sqrt(elapsedYearFraction) *
            (COMMON_SHOCK_WEIGHT * commonRandom + SPECIFIC_SHOCK_WEIGHT * specificRandom)
        val meanReversion = calibration.annualSpotMeanReversionRate *
            ln(calibration.initialSpotLevel / currentSpotLevel) * elapsedYearFraction
        return (
            calibration.annualSpotDrift * elapsedYearFraction + meanReversion +
                macroImpulse * sqrt(elapsedYearFraction) + diffusion
            ).coerceIn(-MAX_INTERVAL_LOG_MOVE, MAX_INTERVAL_LOG_MOVE)
    }

    private fun crossedTradingClose(
        calendar: FuturesRollCalendar,
        from: Instant,
        to: Instant,
    ): LocalDate? {
        var date = localDate(calendar, from)
        val through = localDate(calendar, to)
        var result: LocalDate? = null
        while (date <= through) {
            if (calendar.isTradingDate(date)) {
                val close = LocalDateTime(date, closeTime(calendar)).toInstant(timeZone(calendar))
                if (from < close && to >= close) {
                    require(result == null) { "One market-model interval crossed multiple closes." }
                    result = date
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result
    }

    private fun allocationCalendar(terms: FuturesReferenceTerms): FuturesRollCalendar {
        val calendars = terms.sleeves.map(FuturesSleeveTerms::rollCalendar).distinct()
        require(calendars.size == 1) {
            "A multi-calendar basket needs an explicit allocation-calendar policy."
        }
        return calendars.single()
    }

    private fun isFirstTradingDateOfMonth(
        calendar: FuturesRollCalendar,
        date: LocalDate,
    ): Boolean {
        require(calendar.isTradingDate(date))
        val previous = previousTradingDate(calendar, date.minus(1, DateTimeUnit.DAY))
        return previous.month != date.month
    }

    private fun contractExpiry(
        year: Int,
        month: Int,
        calendar: FuturesRollCalendar,
        rule: CommodityContractExpiryRule,
    ): LocalDate {
        val firstOfNext = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        val monthEnd = firstOfNext.minus(1, DateTimeUnit.DAY)
        return when (rule) {
            CommodityContractExpiryRule.MONTH_END_TRADING_DAY ->
                previousTradingDate(calendar, monthEnd)
            CommodityContractExpiryRule.THIRD_LAST_TRADING_DAY -> {
                var date = previousTradingDate(calendar, monthEnd)
                repeat(2) { date = previousTradingDate(calendar, date.minus(1, DateTimeUnit.DAY)) }
                date
            }
            CommodityContractExpiryRule.MID_MONTH_PRECEDING_TRADING_DAY ->
                previousTradingDate(calendar, LocalDate(year, month, 15))
            CommodityContractExpiryRule.LAST_FRIDAY_PRECEDING_TRADING_DAY -> {
                var friday = monthEnd
                while (friday.dayOfWeek != DayOfWeek.FRIDAY) {
                    friday = friday.minus(1, DateTimeUnit.DAY)
                }
                previousTradingDate(calendar, friday)
            }
        }
    }

    private fun previousTradingDate(
        calendar: FuturesRollCalendar,
        onOrBefore: LocalDate,
    ): LocalDate {
        var date = onOrBefore
        repeat(MAX_CALENDAR_SEARCH_DAYS) {
            if (isModeledTradingDate(calendar, date)) return date
            date = date.minus(1, DateTimeUnit.DAY)
        }
        error("Could not resolve a modeled trading date before $onOrBefore.")
    }

    private fun lastTradingDateOnOrBefore(
        calendar: FuturesRollCalendar,
        date: LocalDate,
    ): LocalDate = previousTradingDate(calendar, date)

    private fun isModeledTradingDate(
        calendar: FuturesRollCalendar,
        date: LocalDate,
    ): Boolean = if (
        date.year in GameCalendar.START_LOCAL_DATE_TIME.year..GameCalendar.CAMPAIGN_END_DATE.year
    ) {
        calendar.isTradingDate(date)
    } else {
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }

    private fun localDate(calendar: FuturesRollCalendar, at: Instant): LocalDate =
        at.toLocalDateTime(timeZone(calendar)).date

    private fun timeZone(calendar: FuturesRollCalendar): TimeZone = when (calendar) {
        FuturesRollCalendar.US_FUTURES_FULL_DAY_APPROXIMATION -> GameCalendar.NEW_YORK_TIME_ZONE
        FuturesRollCalendar.KRX_DERIVATIVES_FULL_DAY_APPROXIMATION -> GameCalendar.KOREA_TIME_ZONE
    }

    private fun closeTime(calendar: FuturesRollCalendar): LocalTime = when (calendar) {
        FuturesRollCalendar.US_FUTURES_FULL_DAY_APPROXIMATION -> LocalTime(16, 0)
        FuturesRollCalendar.KRX_DERIVATIVES_FULL_DAY_APPROXIMATION -> LocalTime(15, 30)
    }

    private fun contractId(curveId: String, year: Int, month: Int): String =
        "cm:${DeterministicRandom.stableHash64(curveId).toULong()}:$year" +
            month.toString().padStart(2, '0')

    private fun validateCalibrations(
        spotTerms: Collection<CommoditySpotReferenceTerms>,
        futuresTerms: Collection<FuturesReferenceTerms>,
    ) {
        val used = buildSet {
            spotTerms.mapTo(this, CommoditySpotReferenceTerms::assetClass)
            futuresTerms.flatMapTo(this) { it.sleeves.map(FuturesSleeveTerms::assetClass) }
        }
        require(used.all(parametersByAssetClass::containsKey)) {
            "Every commodity asset class needs explicit market-model parameters."
        }
    }

    private fun uniqueSpotTerms(
        values: Collection<CommoditySpotReferenceTerms>,
    ): Map<BenchmarkRef, CommoditySpotReferenceTerms> = uniqueIdenticalByRef(
        values = values,
        ref = CommoditySpotReferenceTerms::benchmarkRef,
    )

    private fun uniqueFuturesTerms(
        values: Collection<FuturesReferenceTerms>,
    ): Map<BenchmarkRef, FuturesReferenceTerms> = uniqueIdenticalByRef(
        values = values,
        ref = FuturesReferenceTerms::benchmarkRef,
    )

    private fun <T> uniqueIdenticalByRef(
        values: Collection<T>,
        ref: (T) -> BenchmarkRef,
    ): Map<BenchmarkRef, T> {
        val grouped = values.groupBy(ref)
        grouped.forEach { (benchmarkRef, duplicates) ->
            require(duplicates.all { it == duplicates.first() }) {
                "Conflicting commodity market terms were supplied for $benchmarkRef."
            }
        }
        return grouped.mapValues { (_, duplicates) -> duplicates.first() }
    }

    private fun parameters(assetClass: CommodityAssetClass): CommodityMarketModelParameters =
        requireNotNull(parametersByAssetClass[assetClass]) {
            "No commodity market calibration was supplied for $assetClass."
        }

    private fun supportedCashRate(value: Double): Double {
        require(value.isFinite())
        return value.coerceIn(MIN_CASH_RATE, MAX_CASH_RATE)
    }

    private fun boundedLevel(value: Double): Double {
        require(value.isFinite() && value > 0.0)
        return value.coerceIn(MIN_POSITIVE_LEVEL, MAX_LEVEL)
    }

    companion object {
        fun forCampaignSeed(
            campaignSeed: Long,
            parameters: Collection<CommodityMarketModelParameters> =
                CommodityMarketModelParameterCatalog.calibratedModelAssumptions(),
        ): CommodityMarketModel = CommodityMarketModel(campaignSeed, parameters)

        private const val STREAM_ID: Long = 0x434F4D4D4F444954L
        private const val MARKET_MODEL_SOURCE_ID: String = "commodity-market-model.v1"
        private const val INITIAL_REFERENCE_LEVEL: Double = 100.0
        private const val MILLISECONDS_PER_YEAR: Double = 31_557_600_000.0
        private const val DAYS_PER_YEAR: Double = 365.25
        private const val MIN_CASH_RATE: Double = -0.10
        private const val MAX_CASH_RATE: Double = 1.0
        private const val MIN_POSITIVE_LEVEL: Double = 1e-12
        private const val MAX_LEVEL: Double = 1e24
        private const val MAX_SIGNED_PRICE: Double = 1e12
        private const val MIN_ANNUALIZED_BASIS: Double = -3.0
        private const val MAX_ANNUALIZED_BASIS: Double = 3.0
        private const val MAX_MACRO_IMPULSE: Double = 3.0
        private const val MAX_INTERVAL_LOG_MOVE: Double = .75
        private const val COMMON_SHOCK_WEIGHT: Double = .90
        private const val SPECIFIC_SHOCK_WEIGHT: Double = .4358898943540673
        private const val TARGET_FUTURE_CONTRACTS: Int = 3
        private const val MAX_CURVE_CONTRACTS: Int = 36
        private const val MAX_MONTH_SCAN: Int = 1_200
        private const val MAX_CALENDAR_SEARCH_DAYS: Int = 31
        private const val NEGATIVE_TAIL_WINDOW_TRADING_DAYS: Int = 5
        private const val ALLOCATION_MACRO_SCALE: Double = .35
        private const val ALLOCATION_CARRY_SCALE: Double = .50
        private const val ALLOCATION_SCORE_NOISE: Double = .08
        private val MAX_ADVANCE_DURATION = 48.hours
    }
}
