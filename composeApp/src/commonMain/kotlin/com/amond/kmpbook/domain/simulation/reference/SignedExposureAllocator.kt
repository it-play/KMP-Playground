package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint
import kotlin.math.abs
import kotlin.math.min

/** Deterministic bounded allocator shared by composite and alternative-risk-premia engines. */
internal object SignedExposureAllocator {
    fun allocate(
        orderedIds: List<String>,
        signs: Map<String, Int>,
        rawScores: Map<String, Double>,
        minimumWeights: Map<String, Double>,
        maximumWeights: Map<String, Double>,
        grossConstraint: CompositeExposureConstraint,
        netConstraint: CompositeExposureConstraint,
    ): SignedExposureTargets {
        require(orderedIds.isNotEmpty() && orderedIds == orderedIds.sorted())
        require(signs.keys == orderedIds.toSet())
        require(rawScores.keys == signs.keys && minimumWeights.keys == signs.keys)
        require(maximumWeights.keys == signs.keys)
        require(signs.values.all { it == -1 || it == 1 })
        val longIds = orderedIds.filter { signs.getValue(it) > 0 }
        val shortIds = orderedIds.filter { signs.getValue(it) < 0 }
        val longBounds = groupBounds(longIds, minimumWeights, maximumWeights)
        val shortBounds = groupBounds(shortIds, minimumWeights, maximumWeights)
        val groupTargets = SignedExposureFeasibility.resolveTargets(
            longMinimum = longBounds.first,
            longMaximum = longBounds.second,
            shortMinimum = shortBounds.first,
            shortMaximum = shortBounds.second,
            grossConstraint = grossConstraint,
            netConstraint = netConstraint,
        )
        val result = linkedMapOf<String, Double>()
        result += allocateGroup(
            longIds,
            rawScores,
            minimumWeights,
            maximumWeights,
            groupTargets.first,
        )
        result += allocateGroup(
            shortIds,
            rawScores,
            minimumWeights,
            maximumWeights,
            groupTargets.second,
        )
        orderedIds.filterNot(result::containsKey).forEach { result[it] = 0.0 }
        val sorted = result.toSortedMap().toMap()
        val gross = sorted.values.sum()
        val net = sorted.entries.sumOf { (id, weight) -> signs.getValue(id) * weight }
        require(gross in grossConstraint.minimum - EPSILON..grossConstraint.maximum + EPSILON)
        require(net in netConstraint.minimum - EPSILON..netConstraint.maximum + EPSILON)
        grossConstraint.target?.let { require(abs(gross - it) <= EPSILON) }
        netConstraint.target?.let { require(abs(net - it) <= EPSILON) }
        return SignedExposureTargets(sorted, gross, net)
    }

    private fun groupBounds(
        ids: List<String>,
        minimumWeights: Map<String, Double>,
        maximumWeights: Map<String, Double>,
    ): Pair<Double, Double> = ids.sumOf(minimumWeights::getValue) to
        ids.sumOf(maximumWeights::getValue)

    private fun allocateGroup(
        ids: List<String>,
        rawScores: Map<String, Double>,
        minimumWeights: Map<String, Double>,
        maximumWeights: Map<String, Double>,
        target: Double,
    ): Map<String, Double> {
        if (ids.isEmpty()) {
            require(abs(target) <= EPSILON)
            return emptyMap()
        }
        val minimum = DoubleArray(ids.size) { minimumWeights.getValue(ids[it]) }
        val headroom = DoubleArray(ids.size) {
            maximumWeights.getValue(ids[it]) - minimum[it]
        }
        val raw = DoubleArray(ids.size) { rawScores.getValue(ids[it]).coerceAtLeast(MIN_RAW) }
        val result = minimum.copyOf()
        var residual = target - result.sum()
        require(residual >= -EPSILON && residual <= headroom.sum() + EPSILON)
        if (residual > EPSILON) {
            var low = 0.0
            var high = 1.0
            fun allocated(scale: Double): Double = raw.indices.sumOf { index ->
                min(raw[index] * scale, headroom[index])
            }
            var expansions = 0
            while (allocated(high) < residual - EPSILON) {
                high *= 2.0
                expansions += 1
                require(expansions <= 128 && high.isFinite())
            }
            repeat(96) {
                val middle = (low + high) / 2.0
                if (allocated(middle) < residual) low = middle else high = middle
            }
            raw.indices.forEach { index ->
                val addition = min(raw[index] * high, headroom[index])
                result[index] += addition
                residual -= addition
            }
        }
        if (abs(residual) > 0.0) {
            val repairIndex = result.indices.firstOrNull { index ->
                result[index] + residual in minimum[index]..minimum[index] + headroom[index]
            } ?: error("Unable to repair bounded signed exposure allocation.")
            result[repairIndex] += residual
        }
        return ids.indices.associateTo(linkedMapOf()) { index -> ids[index] to result[index] }
    }

    private const val EPSILON: Double = 1e-8
    private const val MIN_RAW: Double = 1e-12
}
