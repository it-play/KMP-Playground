package com.amond.kmpbook.domain.simulation.market

import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.GameEvent
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import com.amond.kmpbook.domain.simulation.price.DeterministicRandom
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * 외부 목표값을 시장의 잠재상태로 바꾸는 시간당 동역학 엔진이다.
 *
 * - GJR-GARCH형 조건부 분산으로 변동성 군집과 하방 비대칭을 보존한다.
 * - 완전 이산 전환 대신 Markov prior와 softmax posterior를 혼합한다.
 * - 뉴스 강도는 감쇠하는 Hawkes형 excitation을 사용하되 유한 상한을 둔다.
 * - 개인·기관 수급은 서로 다른 반응함수를 가진 bounded AR(1) 과정이다.
 */
class MarketDynamicsEngine(
    seed: Long,
    initialForces: ExternalMarketForces,
) {
    private val random = DeterministicRandom(seed)
    private var state = initialSnapshot(initialForces)

    fun advance(target: ExternalMarketForces): MarketDynamicsFrame {
        val previous = state
        val effective = smoothForces(previous.effectiveForces, target)
        val observedReturn = previous.previousObservedReturn
        val normalizedReturn = observedReturn
            ?.let { raw ->
                MAX_STANDARDIZED_RETURN * tanh(
                    (raw / BASE_HOURLY_VOLATILITY) / MAX_STANDARDIZED_RETURN,
                )
            }
            ?: 0.0
        val negativeInnovation = max(-normalizedReturn, 0.0)
        val downsideMemory = (
            DOWNSIDE_MEMORY_PERSISTENCE * previous.downsideMemory +
                (1.0 - DOWNSIDE_MEMORY_PERSISTENCE) * negativeInnovation
            ).coerceIn(0.0, MarketDynamicsSnapshot.MAX_DOWNSIDE_MEMORY)

        val regime = evolveRegime(
            previous = previous.regimeProbabilities,
            forces = effective,
            liquidityStress = previous.liquidityStress,
            normalizedReturn = normalizedReturn,
            downsideMemory = downsideMemory,
            hasReturnObservation = observedReturn != null,
        )
        val regimeStress = regime.stressIndex / 3.0
        val targetVariance = longRunVariance(effective, previous.liquidityStress)
        // With no tradable market, forecast E[epsilon^2] instead of inserting a fake zero
        // observation. This preserves the GJR-GARCH fixed point through nights and weekends.
        val innovationSquared = observedReturn?.let { normalizedReturn * normalizedReturn }
            ?: previous.conditionalVariance
        val negativeInnovationSquared = observedReturn?.let { negativeInnovation * negativeInnovation }
            ?: previous.conditionalVariance * 0.5
        val conditionalVariance = (
            GARCH_TARGET_WEIGHT * targetVariance +
                GARCH_ALPHA * innovationSquared +
                GARCH_BETA * previous.conditionalVariance +
                GARCH_LEVERAGE * negativeInnovationSquared +
                NEWS_VARIANCE_LOADING * previous.newsExcitation
            ).coerceIn(MarketDynamicsSnapshot.MIN_VARIANCE, MarketDynamicsSnapshot.MAX_VARIANCE)

        val decayedExcitation = previous.newsExcitation * exp(-LN_2 / NEWS_EXCITATION_HALF_LIFE_HOURS)
        val newsBase = baselineNewsIntensity(effective, regimeStress)
        val newsIntensity = (newsBase + decayedExcitation * NEWS_EXCITATION_LOADING)
            .coerceIn(
                MarketDynamicsSnapshot.MIN_NEWS_INTENSITY,
                MarketDynamicsSnapshot.MAX_NEWS_INTENSITY,
            )
        val sentimentMemory = previous.eventSentimentMemory *
            exp(-LN_2 / EVENT_SENTIMENT_HALF_LIFE_HOURS)

        val liquidityTarget = logistic(
            -1.35 +
                1.25 * centered(effective.chaos) +
                0.90 * centered(effective.worldTension) -
                1.55 * centered(effective.marketLiquidity) +
                1.10 * regimeStress +
                0.18 * downsideMemory +
                0.22 * decayedExcitation,
        )
        val liquidityStress = boundedArStep(
            previous = previous.liquidityStress,
            target = liquidityTarget,
            persistence = LIQUIDITY_STRESS_PERSISTENCE,
            innovationScale = 0.012 * (0.65 + effective.chaos),
            minimum = 0.0,
            maximum = 1.0,
        )

        val momentumSignal = (normalizedReturn / 3.0).coerceIn(-1.0, 1.0)
        val retailTarget = retailFlowTarget(
            forces = effective,
            liquidityStress = liquidityStress,
            momentumSignal = momentumSignal,
            sentimentMemory = sentimentMemory,
        )
        val institutionalTarget = institutionalFlowTarget(
            forces = effective,
            liquidityStress = liquidityStress,
            momentumSignal = momentumSignal,
            sentimentMemory = sentimentMemory,
        )
        val retailFlow = boundedArStep(
            previous = previous.retailFlow,
            target = retailTarget,
            persistence = RETAIL_FLOW_PERSISTENCE,
            innovationScale = 0.018 + effective.chaos * 0.018,
            minimum = -1.0,
            maximum = 1.0,
        )
        val institutionalFlow = boundedArStep(
            previous = previous.institutionalFlow,
            target = institutionalTarget,
            persistence = INSTITUTIONAL_FLOW_PERSISTENCE,
            innovationScale = 0.010 + effective.chaos * 0.010,
            minimum = -1.0,
            maximum = 1.0,
        )

        val volatilityRegime = MarketDynamicsSnapshot(
            effectiveForces = effective,
            regimeProbabilities = regime,
            conditionalVariance = conditionalVariance,
            newsExcitation = decayedExcitation,
            newsIntensity = newsIntensity,
            eventSentimentMemory = sentimentMemory,
            liquidityStress = liquidityStress,
            retailFlow = retailFlow,
            institutionalFlow = institutionalFlow,
            downsideMemory = downsideMemory,
            previousObservedReturn = null,
            randomState = random.snapshot(),
        ).resolvedVolatilityRegime
        val correlation = logistic(
            -1.15 + 1.05 * effective.chaos + 0.72 * effective.worldTension +
                1.10 * regimeStress + 0.55 * liquidityStress,
        ).coerceIn(0.12, 0.88)
        val marketReturns = generateMarketReturns(
            forces = effective,
            volatilityRegime = volatilityRegime,
            correlation = correlation,
            regimeStress = regimeStress,
            retailFlow = retailFlow,
            institutionalFlow = institutionalFlow,
            liquidityStress = liquidityStress,
        )
        val sectorReturns = generateSectorReturns(
            forces = effective,
            volatilityRegime = volatilityRegime,
            regimeStress = regimeStress,
        )

        state = MarketDynamicsSnapshot(
            effectiveForces = effective,
            regimeProbabilities = regime,
            conditionalVariance = conditionalVariance,
            newsExcitation = decayedExcitation,
            newsIntensity = newsIntensity,
            eventSentimentMemory = sentimentMemory,
            liquidityStress = liquidityStress,
            retailFlow = retailFlow,
            institutionalFlow = institutionalFlow,
            downsideMemory = downsideMemory,
            previousObservedReturn = null,
            randomState = random.snapshot(),
        )
        return MarketDynamicsFrame(
            effectiveForces = effective,
            regimeProbabilities = regime,
            volatilityRegime = volatilityRegime,
            newsHazardMultiplier = newsIntensity,
            liquidityStress = liquidityStress,
            retailFlow = retailFlow,
            institutionalFlow = institutionalFlow,
            crossMarketCorrelation = correlation,
            marketReturns = marketReturns,
            sectorReturns = sectorReturns,
        )
    }

    /** 이번 시간의 뉴스는 직접 가격 충격과 별개로 다음 시간의 군집 강도와 심리에만 남긴다. */
    fun recordEvents(events: List<GameEvent>) {
        if (events.isEmpty()) return
        val excitation = events.sumOf { event ->
            val severityWeight = when (event.severity) {
                EventSeverity.MINOR -> 0.08
                EventSeverity.MODERATE -> 0.16
                EventSeverity.MAJOR -> 0.30
                EventSeverity.CRITICAL -> 0.48
            }
            severityWeight * systemicScopeWeight(event)
        }
        val weightedSentiment = events.sumOf { event ->
            val severityWeight = when (event.severity) {
                EventSeverity.MINOR -> 0.12
                EventSeverity.MODERATE -> 0.20
                EventSeverity.MAJOR -> 0.32
                EventSeverity.CRITICAL -> 0.45
            }
            event.impact.sentiment * severityWeight * systemicScopeWeight(event)
        }
        state = state.copy(
            newsExcitation = (state.newsExcitation + excitation)
                .coerceAtMost(MarketDynamicsSnapshot.MAX_NEWS_EXCITATION),
            eventSentimentMemory = (state.eventSentimentMemory + weightedSentiment).coerceIn(-1.0, 1.0),
            randomState = random.snapshot(),
        )
    }

    /** 확정 가격봉에서 관측한 시장 수익률을 다음 시간의 분산·국면 필터 입력으로 고정한다. */
    fun observeMarketReturn(returnRate: Double?) {
        require(returnRate == null || returnRate.isFinite())
        state = state.copy(previousObservedReturn = returnRate?.coerceIn(-1.0, 1.0))
    }

    fun snapshot(): MarketDynamicsSnapshot = state.copy(randomState = random.snapshot())

    fun restore(snapshot: MarketDynamicsSnapshot) {
        val validated = snapshot.validatedCopy()
        random.restore(validated.randomState)
        state = validated
    }

    private fun smoothForces(
        current: ExternalMarketForces,
        target: ExternalMarketForces,
    ): ExternalMarketForces {
        val fastRate = 1.0 - exp(-LN_2 / FAST_FORCE_HALF_LIFE_HOURS)
        val slowRate = 1.0 - exp(-LN_2 / SLOW_FORCE_HALF_LIFE_HOURS)
        val slow = current.interpolate(target, slowRate)
        val fast = current.interpolate(target, fastRate)
        return ExternalMarketForces(
            chaos = slow.chaos,
            worldTension = slow.worldTension,
            retailBuyingPower = fast.retailBuyingPower,
            institutionalBuyingPower = fast.institutionalBuyingPower,
            marketLiquidity = fast.marketLiquidity,
            economicMomentum = slow.economicMomentum,
        )
    }

    private fun evolveRegime(
        previous: MarketRegimeProbabilities,
        forces: ExternalMarketForces,
        liquidityStress: Double,
        normalizedReturn: Double,
        downsideMemory: Double,
        hasReturnObservation: Boolean,
    ): MarketRegimeProbabilities {
        val prior = listOf(
            previous.calm * 0.965 + previous.balanced * 0.035,
            previous.calm * 0.030 + previous.balanced * 0.925 + previous.stress * 0.055,
            previous.balanced * 0.038 + previous.stress * 0.900 + previous.crisis * 0.062,
            previous.stress * 0.045 + previous.crisis * 0.938,
        )
        val absoluteMove = kotlin.math.abs(normalizedReturn).coerceAtMost(8.0)
        val scores = listOf(
            1.20 - 1.45 * forces.chaos - 0.75 * forces.worldTension - 0.22 * absoluteMove,
            1.05 - 0.28 * absoluteMove + 0.18 * forces.marketLiquidity,
            -0.70 + 1.20 * forces.chaos + 0.80 * forces.worldTension +
                0.52 * liquidityStress + 0.25 * absoluteMove + 0.12 * downsideMemory,
            -2.85 + 1.55 * forces.chaos + 1.25 * forces.worldTension +
                0.95 * liquidityStress + 0.38 * max(-normalizedReturn, 0.0) +
                0.20 * downsideMemory,
        )
        val likelihood = softmax(scores)
        val observationRate = if (hasReturnObservation) {
            REGIME_OBSERVATION_RATE
        } else {
            REGIME_MISSING_OBSERVATION_RATE
        }
        return MarketRegimeProbabilities.normalized(
            prior.zip(likelihood) { priorValue, likelihoodValue ->
                priorValue * (1.0 - observationRate) + likelihoodValue * observationRate
            },
        )
    }

    private fun generateMarketReturns(
        forces: ExternalMarketForces,
        volatilityRegime: Double,
        correlation: Double,
        regimeStress: Double,
        retailFlow: Double,
        institutionalFlow: Double,
        liquidityStress: Double,
    ): Map<Market, Double> {
        val globalZ = fatTailedInnovation(forces.chaos, regimeStress)
        val commonLoading = sqrt(correlation)
        val localLoading = sqrt(1.0 - correlation)
        val krZ = commonLoading * globalZ + localLoading * fatTailedInnovation(forces.chaos, regimeStress)
        val usZ = commonLoading * globalZ + localLoading * fatTailedInnovation(forces.chaos, regimeStress)
        val sigma = BASE_HOURLY_VOLATILITY * volatilityRegime
        val economicDrift = centered(forces.economicMomentum) * 0.00010
        val tensionPremium = max(centered(forces.worldTension), 0.0) * 0.00006
        val liquidityPremium = liquidityStress * 0.000035
        val krFlow = retailFlow * 0.000085 + institutionalFlow * 0.000125
        val usFlow = retailFlow * 0.000065 + institutionalFlow * 0.000145
        val krCommon = economicDrift + krFlow - tensionPremium - liquidityPremium + sigma * krZ
        val usCommon = economicDrift + usFlow - tensionPremium * 0.72 - liquidityPremium + sigma * usZ
        return Market.entries.associateWith { market ->
            val regional = if (market.isKorean) krCommon else usCommon
            val venueResidual = random.nextGaussian() * VENUE_RESIDUAL_VOLATILITY * volatilityRegime
            (regional + venueResidual).coerceIn(-MAX_FACTOR_RETURN, MAX_FACTOR_RETURN)
        }
    }

    private fun generateSectorReturns(
        forces: ExternalMarketForces,
        volatilityRegime: Double,
        regimeStress: Double,
    ): Map<Sector, Double> = Sector.entries.associateWith { sector ->
        val tensionDrift = when (sector) {
            Sector.AEROSPACE_DEFENSE -> centered(forces.worldTension) * 0.00012
            Sector.ENERGY -> centered(forces.worldTension) * 0.000065
            Sector.TRANSPORTATION_LOGISTICS -> -centered(forces.worldTension) * 0.000080
            Sector.CONSUMER_DISCRETIONARY -> -centered(forces.worldTension) * 0.000045
            else -> 0.0
        }
        val economicDrift = when (sector) {
            Sector.CONSUMER_DISCRETIONARY,
            Sector.AUTOMOTIVE,
            Sector.INDUSTRIALS,
            Sector.SEMICONDUCTOR,
            -> centered(forces.economicMomentum) * 0.000055
            Sector.CONSUMER_STAPLES,
            Sector.UTILITIES,
            -> -centered(forces.economicMomentum) * 0.000020
            else -> 0.0
        }
        val innovation = fatTailedInnovation(forces.chaos, regimeStress) *
            SECTOR_HOURLY_VOLATILITY * volatilityRegime
        (tensionDrift + economicDrift + innovation).coerceIn(-MAX_FACTOR_RETURN, MAX_FACTOR_RETURN)
    }

    private fun fatTailedInnovation(chaos: Double, regimeStress: Double): Double {
        val tailProbability = (0.012 + 0.040 * chaos + 0.045 * regimeStress).coerceIn(0.01, 0.12)
        val tailMinimum = 1.75
        val tailMaximum = tailMinimum + 0.85 + chaos * 0.55
        val scale = if (random.nextBoolean(tailProbability)) {
            tailMinimum + random.nextDouble() * (tailMaximum - tailMinimum)
        } else {
            CORE_INNOVATION_SCALE
        }
        // A Gaussian scale mixture keeps excess kurtosis, but its unconditional variance must
        // remain one because the GJR recursion already owns conditional variance h_t.
        val expectedTailScaleSquared = (
            tailMinimum * tailMinimum + tailMinimum * tailMaximum + tailMaximum * tailMaximum
            ) / 3.0
        val mixtureVariance =
            (1.0 - tailProbability) * CORE_INNOVATION_SCALE * CORE_INNOVATION_SCALE +
                tailProbability * expectedTailScaleSquared
        return random.nextGaussian() * scale / sqrt(mixtureVariance)
    }

    private fun boundedArStep(
        previous: Double,
        target: Double,
        persistence: Double,
        innovationScale: Double,
        minimum: Double,
        maximum: Double,
    ): Double = (
        persistence * previous + (1.0 - persistence) * target + random.nextGaussian() * innovationScale
        ).coerceIn(minimum, maximum)

    private fun softmax(scores: List<Double>): List<Double> {
        val maximum = scores.maxOrNull() ?: 0.0
        val exponentials = scores.map { exp(it - maximum) }
        val denominator = exponentials.sum().coerceAtLeast(1e-12)
        return exponentials.map { it / denominator }
    }

    private fun logistic(value: Double): Double = 1.0 / (1.0 + exp(-value))

    private fun centered(value: Double): Double = value - 0.5

    private fun systemicScopeWeight(event: GameEvent): Double = when (event.scope) {
        EventScope.GLOBAL -> 1.0
        EventScope.COUNTRY -> 0.72
        EventScope.MARKET -> 0.52
        EventScope.SECTOR -> 0.30
        EventScope.STOCK -> 0.10
    }

    private fun initialSnapshot(forces: ExternalMarketForces): MarketDynamicsSnapshot {
        val regime = initialRegimeProbabilities(forces)
        val liquidityStress = initialLiquidityStress(forces)
        return MarketDynamicsSnapshot(
            effectiveForces = forces,
            regimeProbabilities = regime,
            conditionalVariance = longRunVariance(forces, liquidityStress),
            newsExcitation = 0.0,
            newsIntensity = baselineNewsIntensity(forces, regime.stressIndex / 3.0),
            eventSentimentMemory = 0.0,
            liquidityStress = liquidityStress,
            retailFlow = retailFlowTarget(
                forces = forces,
                liquidityStress = liquidityStress,
                momentumSignal = 0.0,
                sentimentMemory = 0.0,
            ),
            institutionalFlow = institutionalFlowTarget(
                forces = forces,
                liquidityStress = liquidityStress,
                momentumSignal = 0.0,
                sentimentMemory = 0.0,
            ),
            downsideMemory = 0.0,
            previousObservedReturn = null,
            randomState = random.snapshot(),
        )
    }

    /** 기본 프리셋은 보정값을 정확히 재현하고, 커스텀 시작값은 첫 턴 전부터 국면에 반영한다. */
    private fun initialRegimeProbabilities(forces: ExternalMarketForces): MarketRegimeProbabilities {
        val riskTilt =
            1.55 * (forces.chaos - ExternalMarketForces.AUGUST_2026_BASELINE_CHAOS) +
                1.25 * (forces.worldTension - ExternalMarketForces.AUGUST_2026_BASELINE_WORLD_TENSION) -
                1.10 * (forces.marketLiquidity - ExternalMarketForces.AUGUST_2026_BASELINE_MARKET_LIQUIDITY) -
                0.45 * (forces.economicMomentum - ExternalMarketForces.AUGUST_2026_BASELINE_ECONOMIC_MOMENTUM)
        val baseline = MarketRegimeProbabilities.AUGUST_2026_BASELINE
        return MarketRegimeProbabilities.normalized(
            listOf(
                baseline.calm * exp(-1.45 * riskTilt),
                baseline.balanced * exp(-0.35 * riskTilt),
                baseline.stress * exp(0.82 * riskTilt),
                baseline.crisis * exp(1.65 * riskTilt),
            ),
        )
    }

    private fun initialLiquidityStress(forces: ExternalMarketForces): Double {
        val baselineLogOdds = ln(AUGUST_2026_BASELINE_LIQUIDITY_STRESS /
            (1.0 - AUGUST_2026_BASELINE_LIQUIDITY_STRESS))
        return logistic(
            baselineLogOdds +
                1.25 * (forces.chaos - ExternalMarketForces.AUGUST_2026_BASELINE_CHAOS) +
                0.90 * (forces.worldTension - ExternalMarketForces.AUGUST_2026_BASELINE_WORLD_TENSION) -
                1.55 * (forces.marketLiquidity - ExternalMarketForces.AUGUST_2026_BASELINE_MARKET_LIQUIDITY),
        )
    }

    private fun retailFlowTarget(
        forces: ExternalMarketForces,
        liquidityStress: Double,
        momentumSignal: Double,
        sentimentMemory: Double,
    ): Double = tanh(
        0.06 +
            2.55 * centered(forces.retailBuyingPower) +
            0.62 * momentumSignal +
            0.55 * sentimentMemory +
            0.28 * centered(forces.economicMomentum) -
            0.50 * liquidityStress,
    )

    private fun institutionalFlowTarget(
        forces: ExternalMarketForces,
        liquidityStress: Double,
        momentumSignal: Double,
        sentimentMemory: Double,
    ): Double = tanh(
        0.10 +
            2.70 * centered(forces.institutionalBuyingPower) +
            0.62 * centered(forces.economicMomentum) -
            0.42 * centered(forces.worldTension) -
            0.68 * liquidityStress +
            0.32 * sentimentMemory -
            0.16 * momentumSignal,
    )

    private companion object {
        const val LN_2: Double = 0.6931471805599453
        const val BASE_HOURLY_VOLATILITY: Double = 0.0016
        const val MAX_STANDARDIZED_RETURN: Double = 8.0
        const val GARCH_TARGET_WEIGHT: Double = 0.05
        const val GARCH_ALPHA: Double = 0.035
        const val GARCH_BETA: Double = 0.90
        const val GARCH_LEVERAGE: Double = 0.030
        const val NEWS_VARIANCE_LOADING: Double = 0.018
        const val DOWNSIDE_MEMORY_PERSISTENCE: Double = 0.92
        const val NEWS_EXCITATION_HALF_LIFE_HOURS: Double = 18.0
        const val EVENT_SENTIMENT_HALF_LIFE_HOURS: Double = 30.0
        const val NEWS_EXCITATION_LOADING: Double = 0.18
        const val LIQUIDITY_STRESS_PERSISTENCE: Double = 0.94
        const val RETAIL_FLOW_PERSISTENCE: Double = 0.91
        const val INSTITUTIONAL_FLOW_PERSISTENCE: Double = 0.95
        const val FAST_FORCE_HALF_LIFE_HOURS: Double = 24.0
        const val SLOW_FORCE_HALF_LIFE_HOURS: Double = 72.0
        const val REGIME_OBSERVATION_RATE: Double = 0.10
        const val REGIME_MISSING_OBSERVATION_RATE: Double = 0.025
        const val AUGUST_2026_BASELINE_LIQUIDITY_STRESS: Double = 0.22
        const val VENUE_RESIDUAL_VOLATILITY: Double = 0.00018
        const val SECTOR_HOURLY_VOLATILITY: Double = 0.00072
        const val MAX_FACTOR_RETURN: Double = 0.075
        const val CORE_INNOVATION_SCALE: Double = 0.92
    }
}

private fun longRunVariance(forces: ExternalMarketForces, liquidityStress: Double): Double = exp(
    1.05 * (forces.chaos - 0.5) +
        0.52 * (forces.worldTension - 0.5) -
        0.42 * (forces.marketLiquidity - 0.5) +
        0.38 * liquidityStress,
).coerceIn(MarketDynamicsSnapshot.MIN_VARIANCE, 4.5)

private fun baselineNewsIntensity(forces: ExternalMarketForces, regimeStress: Double): Double = (
    0.76 * exp(
        0.72 * (forces.chaos - 0.5) +
            0.58 * (forces.worldTension - 0.5) -
            0.18 * (forces.marketLiquidity - 0.5) +
            0.42 * regimeStress,
    )
    ).coerceIn(
    MarketDynamicsSnapshot.MIN_NEWS_INTENSITY,
    MarketDynamicsSnapshot.MAX_NEWS_INTENSITY,
)
