package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.FixedIncomeAssetType
import com.amond.kmpbook.domain.model.fund.FixedIncomeCreditBucket
import com.amond.kmpbook.domain.model.fund.FixedIncomeReferenceProfile
import com.amond.kmpbook.domain.model.fund.FixedIncomeTenorBand
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.reference.CreditQuality
import com.amond.kmpbook.domain.model.reference.FixedIncomeInstrumentKind
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceBook
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceBookAdvance
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferencePosition
import com.amond.kmpbook.domain.model.reference.FixedIncomeReferenceState
import com.amond.kmpbook.domain.model.reference.FixedIncomeRollRecord
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import kotlin.math.max
import kotlin.time.Instant

/**
 * 고정수익 benchmark마다 만기 ladder를 만들고 금리곡선·스프레드 평가와 만기 교체를 수행한다.
 *
 * ladder의 개별 ID는 거래 종목이 아니라 동질 채권 sleeve다. 만기가 하단을 통과하면 같은
 * 통화·신용군의 신규 장기 sleeve로 교체되어 2040년까지 듀레이션이 0으로 소멸하지 않는다.
 */
class FixedIncomeReferenceBookEngine(
    private val marketModel: FixedIncomeMarketModel = FixedIncomeMarketModel(),
    private val referenceEngine: FixedIncomeReferenceEngine = FixedIncomeReferenceEngine(),
) {
    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        macro: MacroEnvironment,
        at: Instant,
    ): FixedIncomeReferenceBook {
        val byRef = validatedDefinitions(definitions)
        val currencies = byRef.values.flatMapTo(linkedSetOf()) {
            requireNotNull(it.fixedIncomeProfile).currencies
        }
        val frame = marketModel.frame(currencies, macro, at)
        val states = byRef.values.sortedBy(BenchmarkDefinition::ref).associate { definition ->
            val profile = requireNotNull(definition.fixedIncomeProfile)
            val positions = initialPositions(definition.ref, profile, frame)
            definition.ref to FixedIncomeReferenceState(
                benchmarkRef = definition.ref,
                positions = positions,
                nominalCurves = frame.nominalCurves.filterKeys(profile.currencies::contains),
                realCurves = frame.realCurves.filterKeys(profile.currencies::contains),
                creditSpreads = frame.creditSpreads.filterKeys(profile.currencies::contains),
                revision = 0L,
                asOf = at,
            )
        }
        return FixedIncomeReferenceBook(states)
    }

    fun advance(
        book: FixedIncomeReferenceBook,
        definitions: Collection<BenchmarkDefinition>,
        macro: MacroEnvironment,
        elapsedYearFractions: Map<BenchmarkRef, Double>,
        to: Instant,
    ): FixedIncomeReferenceBookAdvance {
        val byRef = validatedDefinitions(definitions)
        require(byRef.keys == book.states.keys)
        require(elapsedYearFractions.keys == book.states.keys)
        val currencies = book.states.values.flatMapTo(linkedSetOf()) { state ->
            state.positions.map(FixedIncomeReferencePosition::currency)
        }
        val frame = marketModel.frame(currencies, macro, to)
        val states = linkedMapOf<BenchmarkRef, FixedIncomeReferenceState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val incomeYields = linkedMapOf<BenchmarkRef, Double>()
        val records = mutableListOf<FixedIncomeRollRecord>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            val profile = requireNotNull(byRef.getValue(ref).fixedIncomeProfile)
            val fraction = elapsedYearFractions.getValue(ref)
            require(fraction.isFinite() && fraction in 0.0..1.0)
            if (fraction == 0.0) {
                states[ref] = previous
                returns[ref] = 0.0
                incomeYields[ref] = previous.estimatedAnnualIncomeYield
                return@forEach
            }
            val advance = referenceEngine.advance(
                FixedIncomeAdvanceInput(
                    state = previous,
                    currentNominalCurves = frame.nominalCurves.filterKeys(profile.currencies::contains),
                    currentRealCurves = frame.realCurves.filterKeys(profile.currencies::contains),
                    currentCreditSpreads = frame.creditSpreads.filterKeys(profile.currencies::contains),
                    inflationAccrualRateAnnual = macro.inflationRate,
                    elapsedYearFraction = fraction,
                    expectedCreditLossRatesAnnual = previous.positions.associate { position ->
                        position.assetId to expectedCreditLossRate(position.creditQuality, macro)
                    },
                    defaultEvents = emptyList(),
                    to = to,
                ),
            )
            val rolled = rollMaturedSleeves(advance.state, profile, to)
            states[ref] = rolled.state
            returns[ref] = advance.grossReferenceLogReturn
            incomeYields[ref] = advance.annualIncomeYield
            rolled.record?.let(records::add)
        }
        return FixedIncomeReferenceBookAdvance(
            book = FixedIncomeReferenceBook(states),
            grossReferenceLogReturns = returns,
            annualIncomeYields = incomeYields,
            rollRecords = records.sortedWith(
                compareBy<FixedIncomeRollRecord> { it.benchmarkRef }.thenBy { it.id },
            ),
        )
    }

    private fun validatedDefinitions(
        definitions: Collection<BenchmarkDefinition>,
    ): Map<BenchmarkRef, BenchmarkDefinition> {
        require(definitions.isNotEmpty())
        require(definitions.all { it.engineKind == BenchmarkEngineKind.FIXED_INCOME_CURVE })
        val byRef = definitions.associateBy(BenchmarkDefinition::ref)
        require(byRef.size == definitions.size)
        return byRef
    }

    private fun initialPositions(
        ref: BenchmarkRef,
        profile: FixedIncomeReferenceProfile,
        frame: FixedIncomeMarketFrame,
    ): List<FixedIncomeReferencePosition> {
        val rungScales = if (profile.assetType == FixedIncomeAssetType.MONEY_MARKET) {
            MONEY_MARKET_RUNG_MATURITIES
        } else {
            STANDARD_RUNG_SCALES.map { scale -> targetMaturity(profile) * scale }
        }
        val count = profile.currencies.size * rungScales.size
        val weight = 1.0 / count.toDouble()
        return profile.currencies.flatMap { currency ->
            rungScales.mapIndexed { rung, maturity ->
                newPosition(
                    ref = ref,
                    profile = profile,
                    currency = currency,
                    rung = rung,
                    generation = 0L,
                    maturity = maturity,
                    currentWeight = weight,
                    targetWeight = weight,
                    dirtyMarketValue = PAR_VALUE,
                    frame = frame,
                )
            }
        }.sortedBy(FixedIncomeReferencePosition::assetId)
    }

    private fun rollMaturedSleeves(
        state: FixedIncomeReferenceState,
        profile: FixedIncomeReferenceProfile,
        at: Instant,
    ): RollResult {
        val threshold = rollThreshold(profile)
        val expiring = state.positions.filter { it.remainingMaturityYears <= threshold }
        if (expiring.isEmpty()) return RollResult(state, null)
        val nextRevision = state.revision + 1L
        val frame = FixedIncomeMarketFrame(state.nominalCurves, state.realCurves, state.creditSpreads)
        val replacementsById = expiring.associate { old ->
            val rung = rungFromAssetId(old.assetId)
            old.assetId to newPosition(
                ref = state.benchmarkRef,
                profile = profile,
                currency = old.currency,
                rung = rung,
                generation = nextRevision,
                maturity = topRungMaturity(profile),
                currentWeight = old.currentWeight,
                targetWeight = old.targetWeight,
                dirtyMarketValue = old.dirtyMarketValue,
                frame = frame,
            )
        }
        val positions = state.positions.map { old -> replacementsById[old.assetId] ?: old }
            .sortedBy(FixedIncomeReferencePosition::assetId)
        val next = state.copy(positions = positions, revision = nextRevision)
        val removed = expiring.map(FixedIncomeReferencePosition::assetId).sorted()
        val added = replacementsById.values.map(FixedIncomeReferencePosition::assetId).sorted()
        return RollResult(
            state = next,
            record = FixedIncomeRollRecord(
                id = "fixed-income-roll:${state.benchmarkRef.benchmarkId}:v${state.benchmarkRef.version}:" +
                    "$nextRevision:${at.epochSeconds}",
                benchmarkRef = state.benchmarkRef,
                removedAssetIds = removed,
                addedAssetIds = added,
                effectiveAt = at,
                revision = nextRevision,
            ),
        )
    }

    private fun newPosition(
        ref: BenchmarkRef,
        profile: FixedIncomeReferenceProfile,
        currency: ReferenceCurrency,
        rung: Int,
        generation: Long,
        maturity: Double,
        currentWeight: Double,
        targetWeight: Double,
        dirtyMarketValue: Double,
        frame: FixedIncomeMarketFrame,
    ): FixedIncomeReferencePosition {
        val kind = instrumentKind(profile.assetType)
        val quality = creditQuality(profile.creditQuality)
        val durationScale = if (profile.assetType == FixedIncomeAssetType.MONEY_MARKET) {
            1.0
        } else {
            maturity / targetMaturity(profile)
        }
        val duration = when (kind) {
            FixedIncomeInstrumentKind.CASH_EQUIVALENT -> minOf(maturity, 0.08)
            FixedIncomeInstrumentKind.FLOATING_RATE,
            FixedIncomeInstrumentKind.CLO_TRANCHE,
            -> minOf(profile.effectiveDurationYears, 0.35)
            else -> minOf(maturity, profile.effectiveDurationYears * durationScale)
        }.coerceAtLeast(0.0)
        val nominal = frame.nominalCurves.getValue(currency)
        val spread = frame.creditSpreads.getValue(currency).annualSpreads.getValue(quality)
        val coupon = when (kind) {
            FixedIncomeInstrumentKind.CASH_EQUIVALENT,
            FixedIncomeInstrumentKind.FLOATING_RATE,
            FixedIncomeInstrumentKind.CLO_TRANCHE,
            -> 0.0
            FixedIncomeInstrumentKind.INFLATION_LINKED -> (
                frame.realCurves.getValue(currency).rateAtYears(maturity) + spread
                ).coerceIn(-0.10, 2.0)
            else -> (nominal.rateAtYears(maturity) + spread).coerceIn(-0.10, 2.0)
        }
        val floatingSpread = if (
            kind == FixedIncomeInstrumentKind.FLOATING_RATE ||
            kind == FixedIncomeInstrumentKind.CLO_TRANCHE
        ) spread else 0.0
        return FixedIncomeReferencePosition(
            assetId = "FI:${ref.benchmarkId}:v${ref.version}:${currency.name}:r$rung:g$generation",
            kind = kind,
            currency = currency,
            creditQuality = quality,
            currentWeight = currentWeight,
            targetWeight = targetWeight,
            dirtyMarketValue = dirtyMarketValue,
            remainingMaturityYears = maturity,
            modifiedDurationYears = duration,
            convexityYearsSquared = duration * duration * CONVEXITY_MULTIPLIER,
            spreadDurationYears = if (quality == CreditQuality.SOVEREIGN) 0.0 else duration,
            couponRateAnnual = coupon,
            floatingSpreadAnnual = floatingSpread,
            floatingRateFloorAnnual = 0.0,
            inflationIndexRatio = 1.0,
        )
    }

    private fun targetMaturity(profile: FixedIncomeReferenceProfile): Double = when (profile.tenorBand) {
        FixedIncomeTenorBand.OVERNIGHT -> 1.0 / 52.0
        FixedIncomeTenorBand.ULTRA_SHORT -> 0.5
        FixedIncomeTenorBand.SHORT -> max(2.0, profile.effectiveDurationYears * 1.15)
        FixedIncomeTenorBand.INTERMEDIATE -> max(7.0, profile.effectiveDurationYears * 1.15)
        FixedIncomeTenorBand.LONG -> max(20.0, profile.effectiveDurationYears * 1.15)
        FixedIncomeTenorBand.BROAD -> max(10.0, profile.effectiveDurationYears * 1.20)
        FixedIncomeTenorBand.VARIABLE -> max(3.0, profile.effectiveDurationYears * 1.15)
    }

    private fun topRungMaturity(profile: FixedIncomeReferenceProfile): Double =
        if (profile.assetType == FixedIncomeAssetType.MONEY_MARKET) {
            MONEY_MARKET_RUNG_MATURITIES.last()
        } else {
            targetMaturity(profile) * STANDARD_RUNG_SCALES.last()
        }

    private fun rollThreshold(profile: FixedIncomeReferenceProfile): Double = when (profile.tenorBand) {
        FixedIncomeTenorBand.OVERNIGHT,
        FixedIncomeTenorBand.ULTRA_SHORT,
        -> 1.0 / 52.0
        else -> 0.25
    }

    private fun rungFromAssetId(assetId: String): Int =
        assetId.substringAfterLast(":r").substringBefore(':').toInt()

    private fun instrumentKind(type: FixedIncomeAssetType): FixedIncomeInstrumentKind = when (type) {
        FixedIncomeAssetType.NOMINAL_GOVERNMENT -> FixedIncomeInstrumentKind.TREASURY
        FixedIncomeAssetType.INFLATION_LINKED -> FixedIncomeInstrumentKind.INFLATION_LINKED
        FixedIncomeAssetType.AGENCY_MBS -> FixedIncomeInstrumentKind.MORTGAGE_BACKED
        FixedIncomeAssetType.SECURITIZED_CREDIT -> FixedIncomeInstrumentKind.SECURITIZED_CREDIT
        FixedIncomeAssetType.MUNICIPAL -> FixedIncomeInstrumentKind.MUNICIPAL
        FixedIncomeAssetType.PREFERRED_HYBRID -> FixedIncomeInstrumentKind.PREFERRED
        FixedIncomeAssetType.INVESTMENT_GRADE,
        FixedIncomeAssetType.HIGH_YIELD,
        FixedIncomeAssetType.MULTI_SECTOR_CREDIT,
        -> FixedIncomeInstrumentKind.CORPORATE
        FixedIncomeAssetType.FLOATING_RATE -> FixedIncomeInstrumentKind.FLOATING_RATE
        FixedIncomeAssetType.CLO -> FixedIncomeInstrumentKind.CLO_TRANCHE
        FixedIncomeAssetType.MONEY_MARKET -> FixedIncomeInstrumentKind.CASH_EQUIVALENT
    }

    private fun creditQuality(bucket: FixedIncomeCreditBucket): CreditQuality = when (bucket) {
        FixedIncomeCreditBucket.GOVERNMENT_BACKED -> CreditQuality.SOVEREIGN
        FixedIncomeCreditBucket.AAA -> CreditQuality.AAA
        FixedIncomeCreditBucket.INVESTMENT_GRADE -> CreditQuality.BBB
        FixedIncomeCreditBucket.HIGH_YIELD -> CreditQuality.BB
        FixedIncomeCreditBucket.MIXED -> CreditQuality.BBB
        FixedIncomeCreditBucket.UNVERIFIED -> CreditQuality.BBB
    }

    private fun expectedCreditLossRate(
        quality: CreditQuality,
        macro: MacroEnvironment,
    ): Double {
        val base = BASE_EXPECTED_CREDIT_LOSS_RATES.getValue(quality)
        if (base == 0.0) return 0.0
        val stressMultiplier = (
            1.0 + macro.liquidityStress * 4.0 - macro.growthSurprise * 10.0
            ).coerceIn(0.25, 8.0)
        return (base * stressMultiplier).coerceIn(0.0, 1.0)
    }

    // RollResult는 만기 교체 알고리즘 안에서만 상태와 기록을 묶는 단명 결과다. 별도 파일로
    // 분리하면 private 구현 타입을 internal로 넓혀야 하므로 엔진 내부에 둔다.
    private data class RollResult(
        val state: FixedIncomeReferenceState,
        val record: FixedIncomeRollRecord?,
    )

    companion object {
        private const val PAR_VALUE = 100.0
        private const val CONVEXITY_MULTIPLIER = 1.10
        private val STANDARD_RUNG_SCALES = listOf(0.70, 0.90, 1.10, 1.30)
        private val MONEY_MARKET_RUNG_MATURITIES = listOf(
            1.0 / 12.0,
            2.0 / 12.0,
            3.0 / 12.0,
            6.0 / 12.0,
        )
        private val BASE_EXPECTED_CREDIT_LOSS_RATES = mapOf(
            CreditQuality.SOVEREIGN to 0.0,
            CreditQuality.AAA to 0.00002,
            CreditQuality.AA to 0.00005,
            CreditQuality.A to 0.00015,
            CreditQuality.BBB to 0.00070,
            CreditQuality.BB to 0.00800,
            CreditQuality.B to 0.02500,
            CreditQuality.CCC to 0.08000,
        )
    }
}
