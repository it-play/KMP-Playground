package com.amond.kmpbook.domain.methodology

import com.amond.kmpbook.domain.model.fund.ReferencePortfolioLimits
import kotlin.math.abs
import kotlin.math.min

/** Canonical deterministic implementations of the public methodology building blocks. */
object StandardEquityMethodologyComponents : EquityMethodologyComponentCatalog {
    override fun equalWeights(assetIds: Collection<String>): Map<String, Double> {
        require(assetIds.size in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(assetIds.distinct().size == assetIds.size)
        return normalizedWeights(assetIds.associateWith { 1.0 })
    }

    override fun proportionalWeights(rawValues: Map<String, Double>): Map<String, Double> {
        require(rawValues.size in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(rawValues.values.all { it.isFinite() && it > 0.0 })
        return normalizedWeights(rawValues)
    }

    override fun descendingOrdinalRanks(values: Map<String, Double>): Map<String, Int> {
        require(values.size in 1..EquityMethodologySelectionInput.MAX_CANDIDATES)
        require(values.values.all(Double::isFinite))
        val ordered = values.entries.sortedWith(
            compareByDescending<Map.Entry<String, Double>>(Map.Entry<String, Double>::value)
                .thenBy(Map.Entry<String, Double>::key),
        )
        return buildMap {
            ordered.forEachIndexed { index, entry -> put(entry.key, index + 1) }
        }
    }

    override fun compositeOrdinalRanks(signals: List<Map<String, Double>>): List<String> {
        require(signals.size in 1..EquityMethodologySignals.MAX_SIGNALS)
        val assetIds = signals.first().keys
        require(assetIds.size in 1..EquityMethodologySelectionInput.MAX_CANDIDATES)
        require(signals.all { it.keys == assetIds })
        val ordinalSignals = signals.map(::descendingOrdinalRanks)
        return buildList {
            addAll(
                assetIds.sortedWith(
                    compareBy<String> { assetId -> ordinalSignals.sumOf { it.getValue(assetId) } }
                        .thenBy { it },
                ),
            )
        }
    }

    override fun bufferedSelection(
        rankedAssetIds: List<String>,
        incumbentAssetIds: Set<String>,
        targetCount: Int,
        incumbentRankBuffer: Int,
    ): List<String> {
        require(rankedAssetIds.size in 1..EquityMethodologySelectionInput.MAX_CANDIDATES)
        require(incumbentAssetIds.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(rankedAssetIds.distinct().size == rankedAssetIds.size)
        require(targetCount in 1..rankedAssetIds.size)
        require(incumbentRankBuffer in targetCount..rankedAssetIds.size)
        val ranks = rankedAssetIds.withIndex().associate { (index, assetId) -> assetId to index + 1 }
        val retained = rankedAssetIds.filter { assetId ->
            assetId in incumbentAssetIds && ranks.getValue(assetId) <= incumbentRankBuffer
        }.take(targetCount)
        val retainedIds = retained.toHashSet()
        return buildList {
            addAll(retained)
            addAll(
                rankedAssetIds.asSequence()
                    .filterNot(retainedIds::contains)
                    .take(targetCount - retained.size),
            )
        }
    }

    override fun cappedGroupWeights(
        rawValues: Map<String, Double>,
        groupIds: Map<String, String>,
        groupOrder: List<String>,
        individualCap: Double,
        groupCap: Double,
    ): Map<String, Double> {
        require(rawValues.size in 1..ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(groupOrder.size <= ReferencePortfolioLimits.MAX_CONSTITUENTS)
        require(rawValues.keys == groupIds.keys)
        require(groupOrder.distinct().size == groupOrder.size)
        require(groupOrder.toSet() == groupIds.values.toSet())
        require(rawValues.values.all { it > 0.0 && it.isFinite() })
        require(individualCap.isFinite() && individualCap > 0.0)
        require(groupCap.isFinite() && groupCap >= individualCap)
        val assetIds = rawValues.keys.sorted()
        val rawTotal = rawValues.values.sum()
        val allocationValues = if (rawTotal.isFinite()) {
            rawValues
        } else {
            val maximum = rawValues.values.max()
            buildMap {
                assetIds.forEach { assetId ->
                    put(assetId, rawValues.getValue(assetId) / maximum)
                }
            }
        }
        val byGroup = assetIds.groupBy(groupIds::getValue)
        val totalCapacity = byGroup.values.sumOf { assets ->
            min(groupCap, assets.size * individualCap)
        }
        require(totalCapacity >= 1.0 - WEIGHT_EPSILON) {
            "The individual and group caps cannot allocate 100%."
        }

        fun groupScale(assets: List<String>, globalScale: Double): Double {
            if (assets.sumOf { min(individualCap, allocationValues.getValue(it) * globalScale) } <= groupCap) {
                return globalScale
            }
            var low = 0.0
            var high = globalScale
            repeat(BISECTION_STEPS) {
                val middle = (low + high) / 2.0
                val total = assets.sumOf { min(individualCap, allocationValues.getValue(it) * middle) }
                if (total < groupCap) low = middle else high = middle
            }
            return low
        }

        fun weightsAt(globalScale: Double): Map<String, Double> = buildMap {
            groupOrder.forEach { group ->
                val assets = byGroup.getValue(group)
                val scale = groupScale(assets, globalScale)
                assets.sorted().forEach { assetId ->
                    put(assetId, min(individualCap, allocationValues.getValue(assetId) * scale))
                }
            }
        }

        var low = 0.0
        var high = 1.0 / if (rawTotal.isFinite()) rawTotal else allocationValues.values.sum()
        var expansionCount = 0
        while (
            weightsAt(high).values.sum() < 1.0 - WEIGHT_EPSILON &&
            expansionCount < SCALE_EXPANSION_STEPS
        ) {
            high *= 2.0
            require(high.isFinite()) { "The capped-weight scale exceeded the finite range." }
            expansionCount += 1
        }
        require(weightsAt(high).values.sum() >= 1.0 - WEIGHT_EPSILON) {
            "No finite scale satisfies the individual and group caps."
        }
        repeat(BISECTION_STEPS) {
            val middle = (low + high) / 2.0
            if (weightsAt(middle).values.sum() < 1.0 - WEIGHT_EPSILON) low = middle else high = middle
        }
        val result = weightsAt(low).toMutableMap()
        var remaining = 1.0 - result.values.sum()
        val groupTotals = result.entries.groupBy { groupIds.getValue(it.key) }
            .mapValues { (_, entries) -> entries.sumOf(Map.Entry<String, Double>::value) }
            .toMutableMap()
        for (assetId in assetIds) {
            if (remaining <= WEIGHT_EPSILON) break
            val group = groupIds.getValue(assetId)
            val slack = min(
                individualCap - result.getValue(assetId),
                groupCap - groupTotals.getValue(group),
            ).coerceAtLeast(0.0)
            val addition = min(slack, remaining)
            result[assetId] = result.getValue(assetId) + addition
            groupTotals[group] = groupTotals.getValue(group) + addition
            remaining -= addition
        }
        require(remaining <= 1e-10 && abs(result.values.sum() - 1.0) <= 1e-10)
        require(result.values.all { it <= individualCap + 1e-10 })
        require(groupTotals.values.all { it <= groupCap + 1e-10 })
        return buildMap { putAll(result.toSortedMap()) }
    }

    private const val BISECTION_STEPS: Int = 96
    private const val SCALE_EXPANSION_STEPS: Int = 256
    private const val WEIGHT_EPSILON: Double = 1e-12

    private fun normalizedWeights(rawValues: Map<String, Double>): Map<String, Double> {
        val assetIds = rawValues.keys.sorted()
        val maximum = rawValues.values.max()
        val scaled = assetIds.associateWith { assetId -> rawValues.getValue(assetId) / maximum }
        val total = scaled.values.sum()
        require(total.isFinite() && total > 0.0)
        val result = linkedMapOf<String, Double>()
        var assigned = 0.0
        assetIds.forEachIndexed { index, assetId ->
            val weight = if (index == assetIds.lastIndex) {
                (1.0 - assigned).coerceAtLeast(0.0)
            } else {
                scaled.getValue(assetId) / total
            }
            result[assetId] = weight
            assigned += weight
        }
        require(result.values.all { it.isFinite() && it >= 0.0 })
        require(abs(result.values.sum() - 1.0) <= WEIGHT_EPSILON)
        return buildMap { putAll(result) }
    }
}
