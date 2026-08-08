package com.amond.kmpbook.domain.simulation

import com.amond.kmpbook.domain.model.CausalEconomicFactor
import com.amond.kmpbook.domain.model.CausalMarketTransmissionTrace
import com.amond.kmpbook.domain.model.CausalSignalSeed
import com.amond.kmpbook.domain.model.CausalStockImpact
import com.amond.kmpbook.domain.model.CausalTransmissionProfile
import com.amond.kmpbook.domain.model.EtfExposureRegion
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.StockDefinition
import kotlin.math.roundToInt

/** 거래소 사이에서 같은 방향의 금융 신호가 이동하는 통로다. */
data class MarketContagionEdge(
    val from: Market,
    val to: Market,
    val weight: Double,
) {
    init {
        require(from != to) { "시장 전염 그래프의 자기 간선은 허용하지 않습니다." }
        require(weight.isFinite() && weight > 0.0 && weight <= 1.0) {
            "시장 연결 가중치는 0보다 크고 1 이하여야 합니다."
        }
    }
}

data class MarketContagionPath(
    val markets: List<Market>,
    val contribution: Double,
) {
    init {
        require(markets.size >= 2) { "해외 시장 전염 경로에는 출발과 도착 시장이 필요합니다." }
        require(markets.distinct().size == markets.size) { "시장 전염 경로는 시장을 재방문할 수 없습니다." }
        require(contribution.isFinite() && contribution > 0.0 && contribution <= 1.0)
    }
}

data class MarketSignalTransmission(
    val originalSeed: CausalSignalSeed,
    val transmittedSeed: CausalSignalSeed,
    val reach: Double,
    val representativePath: List<Market>,
    val dominantPathContribution: Double,
    val directExposure: Boolean,
) {
    init {
        require(originalSeed.factor == transmittedSeed.factor)
        require(originalSeed.direction == transmittedSeed.direction)
        require(reach.isFinite() && reach > 0.0 && reach <= 1.0)
        require(representativePath.isNotEmpty())
        require(dominantPathContribution.isFinite() && dominantPathContribution > 0.0)
        require(dominantPathContribution <= reach)
    }

    val trace: CausalMarketTransmissionTrace
        get() = CausalMarketTransmissionTrace(
            markets = representativePath,
            reach = reach,
            dominantPathContribution = dominantPathContribution,
        )
}

data class MarketContagionResult(
    val transmissions: List<MarketSignalTransmission>,
) {
    val transmittedSeeds: List<CausalSignalSeed>
        get() = transmissions.map(MarketSignalTransmission::transmittedSeed)
}

/**
 * 국가·거래소 범위의 신호를 요인별 전염률과 방향성 시장 연결도로 감쇠한다.
 *
 * 경로 p의 순수 이동률은 `u_p = product(tau_f * mobility_profile * link_e)`다. 출발시장 s의
 * 대체 경로는 `R_s = 1 - product(1 - u_p)`로 합치고, 최종 전염도는
 * `q * sum(a_s * R_s)`로 계산한다. 따라서 신뢰도 q와 국가 사건의 출발 질량 a_s는 정확히
 * 한 번만 곱해지고 결과는 [0, 1]에 머문다. seed 강도는 뒤의 경제 요인 그래프에서 한 번만
 * 곱하므로 여기서 중복하지 않는다.
 */
object MarketContagionEngine {
    const val MAX_MARKET_DEPTH: Int = 3
    const val MIN_MARKET_REACH: Double = 0.015

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
    ): MarketContagionResult {
        if (seeds.isEmpty() || sourceMarkets.isEmpty()) return MarketContagionResult(emptyList())

        val sourceMass = canonicalSourceMass(sourceMarkets)
        val transmissions = seeds.sortedBy { it.factor.ordinal }.mapNotNull { seed ->
            val direct = directExposureFor(seed.transmissionProfile, stock, sourceMarkets, sourceMass)
            val spatial = if (direct?.preferOverSpatial == true) {
                null
            } else {
                spatialReachFor(seed, sourceMass, targetMarketsFor(seed.transmissionProfile, stock))
            }
            val selected = listOfNotNull(direct?.asReach(), spatial).maxWithOrNull(
                compareBy<TransmissionReach>(TransmissionReach::reach)
                    .thenBy(TransmissionReach::directExposure),
            ) ?: return@mapNotNull null
            if (selected.reach < MIN_MARKET_REACH) return@mapNotNull null

            val transmittedStrength = (seed.strength * selected.reach).coerceIn(Double.MIN_VALUE, 1.0)
            val transmittedConfidence = if (selected.directExposure) {
                seed.confidence * selected.reach
            } else {
                selected.reach
            }.coerceIn(Double.MIN_VALUE, 1.0)
            MarketSignalTransmission(
                originalSeed = seed,
                transmittedSeed = seed.copy(
                    strength = transmittedStrength,
                    confidence = transmittedConfidence,
                ),
                reach = selected.reach,
                representativePath = selected.path,
                dominantPathContribution = selected.dominantPathContribution,
                directExposure = selected.directExposure,
            )
        }
        return MarketContagionResult(transmissions)
    }

    fun impactFor(
        seeds: List<CausalSignalSeed>,
        sourceMarkets: Set<Market>,
        stock: StockDefinition,
    ): CausalStockImpact? {
        val result = transmit(seeds, sourceMarkets, stock)
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
                val marketTrace = transmission.trace
                val transmissionReason = if (marketTrace.isCrossMarket) {
                    val route = marketTrace.labels.joinToString(" → ")
                    val percent = (marketTrace.reach * 100.0).roundToInt()
                    "$route 시장 경로에서 ${factor.displayName} 신호가 전염도 $percent%로 감쇠됐습니다."
                } else if (marketTrace.reach < 0.999) {
                    val percent = (marketTrace.reach * 100.0).roundToInt()
                    "${marketTrace.labels.single()} 기초·상장 노출 중 $percent%가 직접 연결됩니다."
                } else {
                    "${marketTrace.labels.single()}의 직접 시장 노출입니다."
                }
                trace.copy(
                    rationale = "$transmissionReason ${trace.rationale}",
                    marketTransmission = marketTrace,
                )
            },
        )
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

    private fun spatialReachFor(
        seed: CausalSignalSeed,
        sourceMass: Map<Market, Double>,
        targetMarkets: Set<Market>,
    ): TransmissionReach? {
        if (targetMarkets.isEmpty()) return null
        val sourceResults = sourceMass.mapNotNull { (source, mass) ->
            val bestTarget = targetMarkets.mapNotNull { target ->
                val paths = pathsFor(
                    factor = seed.factor,
                    transmissionProfile = seed.transmissionProfile,
                    source = source,
                    target = target,
                ).distinctBy(MarketContagionPath::markets)
                if (paths.isEmpty()) return@mapNotNull null
                val sourceReach = noisyOr(paths.map(MarketContagionPath::contribution))
                TargetReach(sourceReach, paths.maxBy(MarketContagionPath::contribution))
            }.maxByOrNull(TargetReach::reach) ?: return@mapNotNull null
            SourceReach(
                weightedReach = mass * bestTarget.reach,
                representative = bestTarget.representative,
                representativeEffectiveContribution = seed.confidence * mass * bestTarget.representative.contribution,
            )
        }
        if (sourceResults.isEmpty()) return null
        val reach = (seed.confidence * sourceResults.sumOf(SourceReach::weightedReach)).coerceIn(0.0, 1.0)
        val dominant = sourceResults.maxBy(SourceReach::representativeEffectiveContribution)
        return TransmissionReach(
            reach = reach,
            path = dominant.representative.markets,
            dominantPathContribution = dominant.representativeEffectiveContribution.coerceAtMost(reach),
            directExposure = false,
        )
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

    private data class TargetReach(
        val reach: Double,
        val representative: MarketContagionPath,
    )

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
}
