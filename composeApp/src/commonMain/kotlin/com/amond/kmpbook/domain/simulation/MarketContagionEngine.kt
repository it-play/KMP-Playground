package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalMarketRegimeSnapshot
import com.amond.kmpbook.domain.model.CausalMarketTransmissionTrace
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.CausalStockImpact
import com.amond.kmpbook.domain.model.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.MAX_CAUSAL_MARKET_RESPONSE_INTENSITY
import com.amond.kmpbook.domain.model.MIN_CAUSAL_SIGNAL_STRENGTH
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.pow

/**
 * 국가·거래소 범위의 신호를 요인별 전염률과 방향성 시장 연결도로 감쇠한다.
 *
 * 경로 p의 순수 이동률은 `u_p = product(tau_f * mobility_profile * link_e)`다. 출발시장 s의
 * 대체 경로는 `R_s = 1 - product(1 - u_p)`로 합치고, 최종 전염도는
 * `q * sum(a_s * R_s)`로 계산한다. 따라서 신뢰도 q와 국가 사건의 출발 질량 a_s는 정확히
 * 한 번만 곱해지고 결과는 [0, 1]에 머문다. 그 뒤 발생 시점 도착시장의 취약도에 따라
 * `A(x,g) = gx / (1 + (g - 1)x)`로 유효 신호를 포화 증폭한다. 연결 확률과 반응 강도를
 * 분리하므로 작은 충격은 커질 수 있지만 도착 반응은 출발 신호의 1.5배를 넘지 않는다.
 */
object MarketContagionEngine {
    const val MAX_MARKET_DEPTH: Int = 3
    const val MIN_MARKET_REACH: Double = 0.015
    const val MAX_RESPONSE_INTENSITY: Double = MAX_CAUSAL_MARKET_RESPONSE_INTENSITY

    private const val MIN_PATH_CONTRIBUTION: Double = 0.002

    val edges: List<MarketContagionEdge> = listOf(
        MarketContagionEdge(Market.KOSPI, Market.KOSDAQ, 0.84),
        MarketContagionEdge(Market.KOSDAQ, Market.KOSPI, 0.76),
        MarketContagionEdge(Market.NASDAQ, Market.NYSE, 0.86),
        MarketContagionEdge(Market.NYSE, Market.NASDAQ, 0.83),
        MarketContagionEdge(Market.NYSE, Market.NYSE_ARCA, 0.88),
        MarketContagionEdge(Market.NYSE_ARCA, Market.NYSE, 0.86),
        MarketContagionEdge(Market.NASDAQ, Market.CBOE_BZX, 0.80),
        MarketContagionEdge(Market.CBOE_BZX, Market.NASDAQ, 0.78),
        MarketContagionEdge(Market.NYSE, Market.NYSE_AMERICAN, 0.72),
        MarketContagionEdge(Market.NYSE_AMERICAN, Market.NYSE, 0.74),
        // 미국발 위험 신호의 한국 전달이 반대 방향보다 크도록 비대칭으로 둔다.
        MarketContagionEdge(Market.NASDAQ, Market.KOSPI, 0.46),
        MarketContagionEdge(Market.NYSE, Market.KOSPI, 0.43),
        MarketContagionEdge(Market.KOSPI, Market.NASDAQ, 0.27),
        MarketContagionEdge(Market.KOSPI, Market.NYSE, 0.24),
    ).sortedWith(compareBy<MarketContagionEdge> { it.from.ordinal }.thenBy { it.to.ordinal })

    val factorTransmissibility: Map<CausalEconomicFactor, Double> = mapOf(
        CausalEconomicFactor.CRUDE_OIL_PRICE to 0.90,
        CausalEconomicFactor.TRANSPORT_FUEL_COST to 0.72,
        CausalEconomicFactor.PETROCHEMICAL_INPUT_COST to 0.68,
        CausalEconomicFactor.PLASTIC_PACKAGING_COST to 0.42,
        CausalEconomicFactor.HOUSEHOLD_ENERGY_BURDEN to 0.32,
        CausalEconomicFactor.CONSUMER_DEMAND to 0.28,
        CausalEconomicFactor.FREIGHT_RATE to 0.70,
        CausalEconomicFactor.LOGISTICS_INPUT_COST to 0.48,
        CausalEconomicFactor.GAME_SOFTWARE_DEMAND to 0.34,
        CausalEconomicFactor.HIGH_END_PC_DEMAND to 0.30,
        CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND to 0.42,
        CausalEconomicFactor.SEMICONDUCTOR_DEMAND to 0.55,
        CausalEconomicFactor.CREDIT_AVAILABILITY to 0.76,
        CausalEconomicFactor.BUSINESS_INVESTMENT to 0.52,
        CausalEconomicFactor.RISK_APPETITE to 0.84,
    )

    /** 같은 요인도 거래소 장애인지 글로벌 자금경색인지에 따라 공간 이동성이 달라진다. */
    val profileMobility: Map<CausalTransmissionProfile, Pair<Double, Double>> = mapOf(
        // Pair(same-country, cross-country)
        CausalTransmissionProfile.LOCAL_MICROSTRUCTURE to (0.18 to 0.01),
        CausalTransmissionProfile.PORTFOLIO_DELEVERAGING to (1.00 to 0.82),
        CausalTransmissionProfile.FUNDING_STRESS to (1.00 to 1.00),
        CausalTransmissionProfile.GLOBAL_REAL_ECONOMY to (0.78 to 0.62),
        CausalTransmissionProfile.GLOBAL_REFERENCE_PRICE to (1.00 to 0.95),
    )

    private val outgoing = edges.groupBy(MarketContagionEdge::from)

    init {
        check(edges.distinctBy { it.from to it.to }.size == edges.size)
        check(CausalEconomicFactor.entries.all(factorTransmissibility::containsKey))
        check(factorTransmissibility.values.all { it in 0.0..<1.0 })
        check(CausalTransmissionProfile.entries.all(profileMobility::containsKey))
        check(profileMobility.values.flatMap { listOf(it.first, it.second) }.all { it in 0.0..1.0 })
    }

    fun transmit(
        seeds: List<CausalSignalSeed>,
        sourceMarkets: Set<Market>,
        stock: StockDefinition,
        regimeSnapshot: CausalMarketRegimeSnapshot = CausalMarketRegimeSnapshot(),
    ): MarketContagionResult {
        if (seeds.isEmpty() || sourceMarkets.isEmpty()) return MarketContagionResult(emptyList())
        require(seeds.all { it.strength >= MIN_CAUSAL_SIGNAL_STRENGTH }) {
            "시장 전염의 시작 신호는 $MIN_CAUSAL_SIGNAL_STRENGTH 이상의 강도여야 합니다."
        }

        val sourceMass = canonicalSourceMass(sourceMarkets)
        val transmissions = seeds.sortedBy { it.factor.ordinal }.mapNotNull { seed ->
            val direct = directExposureFor(seed.transmissionProfile, stock, sourceMarkets, sourceMass)
            val candidates = if (direct?.preferOverSpatial == true) {
                listOf(direct.asReach())
            } else {
                listOfNotNull(direct?.asReach()) + spatialReachesFor(
                    seed = seed,
                    sourceMass = sourceMass,
                    targetMarkets = targetMarketsFor(seed.transmissionProfile, stock),
                )
            }
            val selected = candidates.map { transmission ->
                ResponseCandidate(
                    transmission = transmission,
                    response = responseFor(seed, transmission, regimeSnapshot),
                )
            }.maxWithOrNull(
                compareBy<ResponseCandidate> { it.response.effectiveStrength }
                    .thenBy { it.transmission.reach }
                    .thenBy { it.transmission.directExposure },
            ) ?: return@mapNotNull null
            val transmission = selected.transmission
            val response = selected.response
            if (response.intensity < MIN_MARKET_REACH) return@mapNotNull null

            val transmittedConfidence = if (transmission.directExposure) {
                seed.confidence * transmission.reach
            } else {
                transmission.reach
            }.coerceIn(Double.MIN_VALUE, 1.0)
            MarketSignalTransmission(
                originalSeed = seed,
                transmittedSeed = seed.copy(
                    strength = response.effectiveStrength,
                    confidence = transmittedConfidence,
                ),
                reach = transmission.reach,
                responseIntensity = response.intensity,
                representativePath = transmission.path,
                dominantPathContribution = transmission.dominantPathContribution,
                directExposure = transmission.directExposure,
            )
        }
        return MarketContagionResult(transmissions)
    }

    fun impactFor(
        seeds: List<CausalSignalSeed>,
        sourceMarkets: Set<Market>,
        stock: StockDefinition,
        regimeSnapshot: CausalMarketRegimeSnapshot = CausalMarketRegimeSnapshot(),
    ): CausalStockImpact? {
        val result = transmit(seeds, sourceMarkets, stock, regimeSnapshot)
        if (result.transmissions.isEmpty()) return null
        val impact = CausalMarketEngine.impactFor(result.transmittedSeeds, stock) ?: return null
        val transmissionByFactor = result.transmissions.associateBy { it.originalSeed.factor }
        return impact.copy(
            marketTransmissionsByFactor = impact.contributingFactors
                .sortedBy { factor -> factor.ordinal }
                .mapNotNull { factor -> transmissionByFactor[factor]?.let { factor to it.trace } }
                .toMap(linkedMapOf()),
            traces = impact.traces.map { trace ->
                val factor = trace.nodes.firstOrNull()?.factor ?: return@map trace
                val transmission = transmissionByFactor[factor] ?: return@map trace
                trace.copy(
                    marketTransmission = transmission.trace,
                )
            },
        )
    }

    /**
     * 작은 도착 신호만 크게 확대되고 큰 신호는 자연스럽게 포화되는 결정론적 반응 함수다.
     * 중립 국면(g=1)에는 effective=s*reach가 되어 기존 선형 결과를 정확히 보존한다.
     */
    private fun responseFor(
        seed: CausalSignalSeed,
        transmission: TransmissionReach,
        regime: CausalMarketRegimeSnapshot,
    ): MarketResponse {
        val rawStrength = seed.strength * transmission.reach
        if (transmission.directExposure) {
            return MarketResponse(
                effectiveStrength = rawStrength.coerceIn(Double.MIN_VALUE, 1.0),
                intensity = transmission.reach,
            )
        }

        val target = transmission.path.last()
        val fragility = fragilityFor(target, seed.transmissionProfile, regime)
        val threshold = fragilityThresholdFor(target)
        val normalized = ((fragility - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)
        val activation = normalized * normalized * (3.0 - 2.0 * normalized)
        val pathConvergence = (
            (transmission.reach - transmission.dominantPathContribution) /
                transmission.reach
            ).coerceIn(0.0, 1.0)
        val networkPressure = 0.85 + 0.15 * pathConvergence
        val gain = 1.0 +
            (maxGainFor(seed.transmissionProfile) - 1.0) *
            activation * reflexivityFor(target) * networkPressure
        val saturated = gain * rawStrength / (1.0 + (gain - 1.0) * rawStrength)
        val effective = minOf(
            saturated,
            seed.strength * MAX_RESPONSE_INTENSITY,
            1.0,
        ).coerceAtLeast(Double.MIN_VALUE)
        return MarketResponse(
            effectiveStrength = effective,
            intensity = (effective / seed.strength).coerceIn(Double.MIN_VALUE, MAX_RESPONSE_INTENSITY),
        )
    }

    /** 가중 생존함수는 여러 약한 스트레스가 겹칠 때도 매끄럽게 취약도를 끌어올린다. */
    private fun fragilityFor(
        target: Market,
        profile: CausalTransmissionProfile,
        regime: CausalMarketRegimeSnapshot,
    ): Double {
        val drawdown = ((-regime.marketChangeFromPreviousClose.getOrElse(target) { 0.0 }) / 0.08)
            .coerceIn(0.0, 0.98)
        val hourlySelloff = ((-regime.marketHourlyReturns.getOrElse(target) { 0.0 }) / 0.04)
            .coerceIn(0.0, 0.98)
        val volatility = ((regime.volatilityRegime - 1.0) / 1.5).coerceIn(0.0, 0.98)
        val riskOff = ((-regime.riskSentiment) / 0.90).coerceIn(0.0, 0.98)
        val wonFundingStress = if (target.isKorean && profile == CausalTransmissionProfile.FUNDING_STRESS) {
            (regime.usdKrwChangeRate / 0.05).coerceIn(0.0, 0.98)
        } else {
            0.0
        }
        val stresses = listOf(
            volatility to 0.30,
            drawdown to 0.34,
            riskOff to 0.22,
            hourlySelloff to 0.08,
            wonFundingStress to 0.06,
        )
        val survival = stresses.fold(1.0) { product, (stress, weight) ->
            product * (1.0 - stress).pow(weight)
        }
        return (1.0 - survival).coerceIn(0.0, 1.0)
    }

    private fun fragilityThresholdFor(market: Market): Double = when (market) {
        Market.KOSDAQ -> 0.18
        Market.NASDAQ -> 0.21
        Market.KOSPI -> 0.24
        Market.NYSE -> 0.28
        Market.NYSE_ARCA,
        Market.CBOE_BZX,
        Market.NYSE_AMERICAN,
        -> 0.25
    }

    private fun reflexivityFor(market: Market): Double = when (market) {
        Market.KOSDAQ -> 1.00
        Market.NASDAQ -> 0.95
        Market.KOSPI -> 0.86
        Market.NYSE -> 0.76
        Market.NYSE_ARCA,
        Market.CBOE_BZX,
        Market.NYSE_AMERICAN,
        -> 0.82
    }

    private fun maxGainFor(profile: CausalTransmissionProfile): Double = when (profile) {
        CausalTransmissionProfile.LOCAL_MICROSTRUCTURE -> 1.10
        CausalTransmissionProfile.PORTFOLIO_DELEVERAGING -> 5.50
        CausalTransmissionProfile.FUNDING_STRESS -> 6.00
        CausalTransmissionProfile.GLOBAL_REAL_ECONOMY -> 3.00
        CausalTransmissionProfile.GLOBAL_REFERENCE_PRICE -> 2.20
    }

    private fun directExposureFor(
        transmissionProfile: CausalTransmissionProfile,
        stock: StockDefinition,
        sources: Set<Market>,
        sourceMass: Map<Market, Double>,
    ): DirectExposure? {
        if (transmissionProfile == CausalTransmissionProfile.LOCAL_MICROSTRUCTURE) {
            return stock.market.takeIf(sources::contains)?.let { DirectExposure(it, 1.0, true) }
        }
        if (!stock.isFundLike) {
            return stock.market.takeIf(sources::contains)?.let { DirectExposure(it, 1.0, true) }
        }

        val fundProfile = stock.etfProfile
        val underlyingWeight = fundProfile?.exposureRegion?.let { region ->
            when (region) {
                EtfExposureRegion.KOREA -> 1.0.takeIf { sources.any(Market::isKorean) }
                EtfExposureRegion.UNITED_STATES -> 1.0.takeIf { sources.any(Market::isUnitedStates) }
                EtfExposureRegion.GLOBAL -> 0.45.takeIf { sources.isNotEmpty() }
                EtfExposureRegion.DEVELOPED_EX_US,
                EtfExposureRegion.EMERGING_MARKETS,
                -> null
            }
        }
        if (underlyingWeight != null) {
            val representative = sourceMass.keys.firstOrNull { source ->
                fundProfile.isExposedTo(source)
            } ?: sourceMass.keys.first()
            return DirectExposure(representative, underlyingWeight, true)
        }

        return null
    }

    private fun targetMarketsFor(
        transmissionProfile: CausalTransmissionProfile,
        stock: StockDefinition,
    ): Set<Market> {
        if (transmissionProfile == CausalTransmissionProfile.LOCAL_MICROSTRUCTURE || !stock.isFundLike) {
            return setOf(stock.market)
        }
        return when (stock.etfProfile?.exposureRegion) {
            EtfExposureRegion.KOREA -> Market.entries.filterTo(linkedSetOf(), Market::isKorean)
            EtfExposureRegion.UNITED_STATES -> Market.entries.filterTo(linkedSetOf(), Market::isUnitedStates)
            EtfExposureRegion.GLOBAL -> Market.entries.toCollection(linkedSetOf())
            EtfExposureRegion.DEVELOPED_EX_US,
            EtfExposureRegion.EMERGING_MARKETS,
            null,
            -> emptySet()
        }
    }

    /** 국가 사건의 여러 거래소는 별개 충격이 아니므로 핵심 시장에 총 질량 1을 나눠 준다. */
    private fun canonicalSourceMass(sources: Set<Market>): Map<Market, Double> {
        val roots = when {
            sources.size <= 1 -> sources.toList()
            sources.all(Market::isUnitedStates) -> listOf(Market.NASDAQ, Market.NYSE).filter(sources::contains)
                .ifEmpty { sources.sortedBy(Market::ordinal) }
            sources.all(Market::isKorean) -> listOfNotNull(
                Market.KOSPI.takeIf(sources::contains) ?: Market.KOSDAQ.takeIf(sources::contains),
            )
            else -> sources.sortedBy(Market::ordinal)
        }
        val mass = 1.0 / roots.size
        return roots.associateWith { mass }
    }

    /** 기초 목적시장마다 반응을 따로 계산할 수 있도록 topology 후보를 합치지 않고 보존한다. */
    private fun spatialReachesFor(
        seed: CausalSignalSeed,
        sourceMass: Map<Market, Double>,
        targetMarkets: Set<Market>,
    ): List<TransmissionReach> {
        if (targetMarkets.isEmpty()) return emptyList()
        return targetMarkets.sortedBy(Market::ordinal).mapNotNull targetCandidate@ { target ->
            val sourceResults = sourceMass.mapNotNull sourceCandidate@ { (source, mass) ->
                val paths = pathsFor(
                    factor = seed.factor,
                    transmissionProfile = seed.transmissionProfile,
                    source = source,
                    target = target,
                ).distinctBy(MarketContagionPath::markets)
                if (paths.isEmpty()) return@sourceCandidate null
                val sourceReach = noisyOr(paths.map(MarketContagionPath::contribution))
                val representative = paths.maxBy(MarketContagionPath::contribution)
                SourceReach(
                    weightedReach = mass * sourceReach,
                    representative = representative,
                    representativeEffectiveContribution = seed.confidence * mass * representative.contribution,
                )
            }
            if (sourceResults.isEmpty()) return@targetCandidate null
            val reach = (seed.confidence * sourceResults.sumOf(SourceReach::weightedReach)).coerceIn(0.0, 1.0)
            val dominant = sourceResults.maxBy(SourceReach::representativeEffectiveContribution)
            TransmissionReach(
                reach = reach,
                path = dominant.representative.markets,
                dominantPathContribution = dominant.representativeEffectiveContribution.coerceAtMost(reach),
                directExposure = false,
            )
        }
    }

    private fun pathsFor(
        factor: CausalEconomicFactor,
        transmissionProfile: CausalTransmissionProfile,
        source: Market,
        target: Market,
    ): List<MarketContagionPath> {
        if (source == target) return emptyList()
        val found = mutableListOf<MarketContagionPath>()
        walk(
            current = source,
            target = target,
            factorTransmission = requireNotNull(factorTransmissibility[factor]),
            transmissionProfile = transmissionProfile,
            contribution = 1.0,
            markets = listOf(source),
            visited = linkedSetOf(source),
            found = found,
        )
        return found
    }

    private fun walk(
        current: Market,
        target: Market,
        factorTransmission: Double,
        transmissionProfile: CausalTransmissionProfile,
        contribution: Double,
        markets: List<Market>,
        visited: LinkedHashSet<Market>,
        found: MutableList<MarketContagionPath>,
    ) {
        val depth = markets.size - 1
        if (depth >= MAX_MARKET_DEPTH) return
        outgoing[current].orEmpty().forEach { edge ->
            if (edge.to in visited) return@forEach
            val mobility = requireNotNull(profileMobility[transmissionProfile]).let { (sameCountry, crossCountry) ->
                if (edge.from.countryName == edge.to.countryName) sameCountry else crossCountry
            }
            val nextContribution = contribution * factorTransmission * mobility * edge.weight
            if (nextContribution < MIN_PATH_CONTRIBUTION) return@forEach
            val nextMarkets = markets + edge.to
            if (edge.to == target) {
                found += MarketContagionPath(nextMarkets, nextContribution.coerceAtMost(1.0))
                return@forEach
            }
            val nextVisited = LinkedHashSet(visited).apply { add(edge.to) }
            walk(
                current = edge.to,
                target = target,
                factorTransmission = factorTransmission,
                transmissionProfile = transmissionProfile,
                contribution = nextContribution,
                markets = nextMarkets,
                visited = nextVisited,
                found = found,
            )
        }
    }

    private fun noisyOr(contributions: List<Double>): Double = contributions.fold(1.0) { survival, value ->
        survival * (1.0 - value.coerceIn(0.0, 1.0))
    }.let { survival -> (1.0 - survival).coerceIn(0.0, 1.0) }

    private data class DirectExposure(
        val market: Market,
        val reach: Double,
        val preferOverSpatial: Boolean,
    ) {
        fun asReach(): TransmissionReach = TransmissionReach(
            reach = reach,
            path = listOf(market),
            dominantPathContribution = reach,
            directExposure = true,
        )
    }

    private data class SourceReach(
        val weightedReach: Double,
        val representative: MarketContagionPath,
        val representativeEffectiveContribution: Double,
    )

    private data class TransmissionReach(
        val reach: Double,
        val path: List<Market>,
        val dominantPathContribution: Double,
        val directExposure: Boolean,
    )

    private data class MarketResponse(
        val effectiveStrength: Double,
        val intensity: Double,
    )

    private data class ResponseCandidate(
        val transmission: TransmissionReach,
        val response: MarketResponse,
    )
}
