package com.amond.kmpbook.domain.simulation.causal

import com.amond.kmpbook.domain.data.CausalExposureCatalog
import com.amond.kmpbook.domain.data.CausalStockExposure
import com.amond.kmpbook.domain.model.causal.CausalEconomicFactor
import com.amond.kmpbook.domain.model.causal.CausalImpactTrace
import com.amond.kmpbook.domain.model.causal.CausalSignalDirection
import com.amond.kmpbook.domain.model.causal.CausalSignalSeed
import com.amond.kmpbook.domain.model.causal.CausalStockImpact
import com.amond.kmpbook.domain.model.causal.CausalTraceNode
import com.amond.kmpbook.domain.model.causal.CausalTraceNodeKind
import com.amond.kmpbook.domain.model.event.ImpactDirection
import com.amond.kmpbook.domain.model.instrument.StockDefinition
import kotlin.math.abs
import kotlin.math.pow

/**
 * 상태·시계·난수를 갖지 않는 감쇠 가중 전파기다.
 *
 * 한 단순 경로 p의 종목 기여는 `s * decay^|p| * product(edge weight)`다. 산업/종목
 * 노출도 마지막 간선으로 세며, 경로 안에서 경제 요인을 재방문하지 않고 [MAX_FACTOR_DEPTH]를
 * 넘지 않는다. 작은 경로를 제거하고 경로·합성 민감도를 각각 제한해 합성이 안정적이다.
 */
object CausalMarketEngine {
    const val DECAY: Double = 0.72
    const val MAX_FACTOR_DEPTH: Int = 6
    const val MAX_PATH_CONTRIBUTION: Double = 1.0
    const val MAX_STOCK_SENSITIVITY: Double = 1.5
    const val MIN_PATH_CONTRIBUTION: Double = 0.004
    const val MAX_TRACES_PER_STOCK: Int = 8

    private const val CONFIDENCE_DECAY: Double = 0.90
    private const val DIRECTION_EPSILON: Double = 1e-8

    val edges: List<CausalFactorEdge> = listOf(
        CausalFactorEdge(
            CausalEconomicFactor.CRUDE_OIL_PRICE,
            CausalEconomicFactor.TRANSPORT_FUEL_COST,
            0.92,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.CRUDE_OIL_PRICE,
            CausalEconomicFactor.PETROCHEMICAL_INPUT_COST,
            0.82,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.PETROCHEMICAL_INPUT_COST,
            CausalEconomicFactor.PLASTIC_PACKAGING_COST,
            0.76,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.CRUDE_OIL_PRICE,
            CausalEconomicFactor.HOUSEHOLD_ENERGY_BURDEN,
            0.74,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.HOUSEHOLD_ENERGY_BURDEN,
            CausalEconomicFactor.CONSUMER_DEMAND,
            -0.68,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.FREIGHT_RATE,
            CausalEconomicFactor.LOGISTICS_INPUT_COST,
            0.86,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.GAME_SOFTWARE_DEMAND,
            CausalEconomicFactor.HIGH_END_PC_DEMAND,
            0.66,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.HIGH_END_PC_DEMAND,
            CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
            0.78,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.COMPUTING_HARDWARE_DEMAND,
            CausalEconomicFactor.SEMICONDUCTOR_DEMAND,
            0.72,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.CREDIT_AVAILABILITY,
            CausalEconomicFactor.BUSINESS_INVESTMENT,
            0.78,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.CREDIT_AVAILABILITY,
            CausalEconomicFactor.CONSUMER_DEMAND,
            0.58,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.CREDIT_AVAILABILITY,
            CausalEconomicFactor.RISK_APPETITE,
            0.72,
        ),
        CausalFactorEdge(
            CausalEconomicFactor.RISK_APPETITE,
            CausalEconomicFactor.BUSINESS_INVESTMENT,
            0.46,
        ),
    ).sortedWith(compareBy<CausalFactorEdge> { it.from.ordinal }.thenBy { it.to.ordinal })

    private val outgoing: Map<CausalEconomicFactor, List<CausalFactorEdge>> = edges.groupBy(CausalFactorEdge::from)

    init {
        check(DECAY in 0.0..<1.0)
        check(edges.distinctBy { it.from to it.to }.size == edges.size)
    }

    fun propagate(
        seeds: List<CausalSignalSeed>,
        stocks: List<StockDefinition>,
    ): CausalPropagationResult {
        if (seeds.isEmpty() || stocks.isEmpty()) {
            return CausalPropagationResult(emptyMap(), DECAY, MAX_FACTOR_DEPTH)
        }
        require(seeds.map(CausalSignalSeed::factor).distinct().size == seeds.size) {
            "한 사건에는 같은 경제 요인의 인과 신호를 중복 선언할 수 없습니다."
        }
        require(stocks.map(StockDefinition::id).distinct().size == stocks.size) {
            "인과 전파 대상 종목 ID는 중복될 수 없습니다."
        }

        val exposuresByFactor = stocks
            .flatMap { stock ->
                CausalExposureCatalog.exposuresFor(stock).map { exposure ->
                    StockExposure(stock, exposure)
                }
            }
            .groupBy { it.exposure.factor }
        val tracesByStockId = linkedMapOf<String, MutableList<CausalImpactTrace>>()

        seeds.sortedBy { it.factor.ordinal }.forEach { seed ->
            walk(
                seed = seed,
                current = seed.factor,
                factorDepth = 0,
                edgeProduct = 1.0,
                visited = linkedSetOf(seed.factor),
                factorPath = listOf(seed.factor),
                factorDirections = listOf(seed.direction),
                exposuresByFactor = exposuresByFactor,
                tracesByStockId = tracesByStockId,
            )
        }

        val impacts = stocks.sortedBy(StockDefinition::id).mapNotNull { stock ->
            val traceComparator = compareByDescending<CausalImpactTrace> { abs(it.contribution) }
                .thenBy { it.nodes.joinToString("|") { node -> node.displayLabel } }
            val traces = tracesByStockId[stock.id].orEmpty().sortedWith(traceComparator)
            if (traces.isEmpty()) return@mapNotNull null
            val rawSigned = traces.sumOf(CausalImpactTrace::contribution)
            val signed = rawSigned.coerceIn(-MAX_STOCK_SENSITIVITY, MAX_STOCK_SENSITIVITY)
            if (abs(signed) <= DIRECTION_EPSILON) return@mapNotNull null
            val totalAbsolute = traces.sumOf { abs(it.contribution) }
            val confidence = if (totalAbsolute == 0.0) 0.0 else {
                traces.sumOf { abs(it.contribution) * it.confidence } / totalAbsolute
            }
            val direction = if (signed > 0.0) ImpactDirection.POSITIVE else ImpactDirection.NEGATIVE
            val retainedTraces = traces.take(MAX_TRACES_PER_STOCK).let { strongest ->
                if (strongest.any { it.contribution * signed > 0.0 }) {
                    strongest
                } else {
                    val explanatory = traces.first { it.contribution * signed > 0.0 }
                    (strongest.dropLast(1) + explanatory).sortedWith(traceComparator)
                }
            }
            stock.id to CausalStockImpact(
                stockId = stock.id,
                direction = direction,
                signedSensitivity = signed,
                relativeSensitivity = abs(signed),
                confidence = confidence.coerceIn(0.0, 1.0),
                specificity = traces.maxOf { trace -> trace.nodes.last().kind.specificity },
                traces = retainedTraces,
                contributingFactors = traces.mapNotNullTo(linkedSetOf()) { trace ->
                    trace.nodes.firstOrNull()?.factor
                },
            )
        }.toMap(linkedMapOf())
        return CausalPropagationResult(impacts, DECAY, MAX_FACTOR_DEPTH)
    }

    fun impactFor(
        seeds: List<CausalSignalSeed>,
        stock: StockDefinition,
    ): CausalStockImpact? = propagate(seeds, listOf(stock)).impactFor(stock.id)

    private fun walk(
        seed: CausalSignalSeed,
        current: CausalEconomicFactor,
        factorDepth: Int,
        edgeProduct: Double,
        visited: LinkedHashSet<CausalEconomicFactor>,
        factorPath: List<CausalEconomicFactor>,
        factorDirections: List<CausalSignalDirection>,
        exposuresByFactor: Map<CausalEconomicFactor, List<StockExposure>>,
        tracesByStockId: MutableMap<String, MutableList<CausalImpactTrace>>,
    ) {
        val exposureDepth = factorDepth + 1
        exposuresByFactor[current].orEmpty()
            .sortedWith(compareBy<StockExposure> { it.stock.id }.thenBy { it.exposure.targetLabel })
            .forEach { terminal ->
                val contribution = (
                    seed.signedStrength * DECAY.pow(exposureDepth) * edgeProduct * terminal.exposure.weight
                    ).coerceIn(-MAX_PATH_CONTRIBUTION, MAX_PATH_CONTRIBUTION)
                if (abs(contribution) < MIN_PATH_CONTRIBUTION) return@forEach
                check(factorPath.size == factorDirections.size)
                val nodes = factorPath.zip(factorDirections).map { (factor, direction) ->
                    CausalTraceNode(
                        kind = CausalTraceNodeKind.ECONOMIC_FACTOR,
                        label = factor.displayName,
                        factor = factor,
                        factorDirection = direction,
                    )
                } + terminal.exposure.toTraceNode(terminal.stock)
                tracesByStockId.getOrPut(terminal.stock.id, ::mutableListOf) += CausalImpactTrace(
                    contribution = contribution,
                    confidence = (seed.confidence * CONFIDENCE_DECAY.pow(exposureDepth)).coerceIn(0.0, 1.0),
                    nodes = nodes,
                    rationale = terminal.exposure.rationale,
                    exposureMechanism = terminal.exposure.mechanism,
                )
            }

        if (factorDepth >= MAX_FACTOR_DEPTH) return
        outgoing[current].orEmpty().forEach { edge ->
            if (edge.to in visited) return@forEach
            val nextProduct = edgeProduct * edge.weight
            if (abs(seed.signedStrength * DECAY.pow(factorDepth + 1) * nextProduct) < MIN_PATH_CONTRIBUTION) {
                return@forEach
            }
            val nextVisited = LinkedHashSet(visited).apply { add(edge.to) }
            val nextDirection = if (edge.weight > 0.0) {
                factorDirections.last()
            } else {
                factorDirections.last().opposite()
            }
            walk(
                seed = seed,
                current = edge.to,
                factorDepth = factorDepth + 1,
                edgeProduct = nextProduct,
                visited = nextVisited,
                factorPath = factorPath + edge.to,
                factorDirections = factorDirections + nextDirection,
                exposuresByFactor = exposuresByFactor,
                tracesByStockId = tracesByStockId,
            )
        }
    }

    private fun CausalStockExposure.toTraceNode(stock: StockDefinition): CausalTraceNode = when (targetKind) {
        CausalTraceNodeKind.INDUSTRY -> CausalTraceNode(
            kind = targetKind,
            label = targetLabel,
            sector = requireNotNull(sector),
        )
        CausalTraceNodeKind.INDUSTRY_SEGMENT -> CausalTraceNode(
            kind = targetKind,
            label = targetLabel,
            sector = requireNotNull(sector),
            industrySegment = requireNotNull(industrySegment),
        )
        CausalTraceNodeKind.STOCK -> CausalTraceNode(
            kind = targetKind,
            label = targetLabel,
            stockId = stock.id,
            companySpecificExposure = explicitCompanyOverride,
        )
        CausalTraceNodeKind.ECONOMIC_FACTOR -> error("경제 요인은 종목 노출의 최종 대상일 수 없습니다.")
    }

    private data class StockExposure(
        val stock: StockDefinition,
        val exposure: CausalStockExposure,
    )
}

private fun CausalSignalDirection.opposite(): CausalSignalDirection = when (this) {
    CausalSignalDirection.INCREASE -> CausalSignalDirection.DECREASE
    CausalSignalDirection.DECREASE -> CausalSignalDirection.INCREASE
}

private val CausalTraceNodeKind.specificity: Int
    get() = when (this) {
        CausalTraceNodeKind.ECONOMIC_FACTOR -> 1
        CausalTraceNodeKind.INDUSTRY -> 2
        CausalTraceNodeKind.INDUSTRY_SEGMENT -> 3
        CausalTraceNodeKind.STOCK -> 4
    }
