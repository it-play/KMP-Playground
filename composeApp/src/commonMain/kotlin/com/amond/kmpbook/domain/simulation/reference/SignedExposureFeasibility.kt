package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.CompositeExposureConstraint
import kotlin.math.max
import kotlin.math.min

/** Exact joint feasibility of long, short, gross and signed-net exposure intervals. */
object SignedExposureFeasibility {
    fun isFeasible(
        longMinimum: Double,
        longMaximum: Double,
        shortMinimum: Double,
        shortMaximum: Double,
        grossConstraint: CompositeExposureConstraint,
        netConstraint: CompositeExposureConstraint,
    ): Boolean = resolveTargetsOrNull(
        longMinimum,
        longMaximum,
        shortMinimum,
        shortMaximum,
        grossConstraint,
        netConstraint,
    ) != null

    fun requireFeasible(
        longMinimum: Double,
        longMaximum: Double,
        shortMinimum: Double,
        shortMaximum: Double,
        grossConstraint: CompositeExposureConstraint,
        netConstraint: CompositeExposureConstraint,
    ) {
        require(
            isFeasible(
                longMinimum,
                longMaximum,
                shortMinimum,
                shortMaximum,
                grossConstraint,
                netConstraint,
            ),
        ) { "Signed exposure constraints have no jointly feasible allocation." }
    }

    internal fun resolveTargets(
        longMinimum: Double,
        longMaximum: Double,
        shortMinimum: Double,
        shortMaximum: Double,
        grossConstraint: CompositeExposureConstraint,
        netConstraint: CompositeExposureConstraint,
    ): Pair<Double, Double> = requireNotNull(
        resolveTargetsOrNull(
            longMinimum,
            longMaximum,
            shortMinimum,
            shortMaximum,
            grossConstraint,
            netConstraint,
        ),
    ) { "Signed exposure constraints have no jointly feasible allocation." }

    private fun resolveTargetsOrNull(
        longMinimum: Double,
        longMaximum: Double,
        shortMinimum: Double,
        shortMaximum: Double,
        grossConstraint: CompositeExposureConstraint,
        netConstraint: CompositeExposureConstraint,
    ): Pair<Double, Double>? {
        require(longMinimum.isFinite() && longMaximum.isFinite())
        require(shortMinimum.isFinite() && shortMaximum.isFinite())
        require(longMinimum >= 0.0 && shortMinimum >= 0.0)
        require(longMinimum <= longMaximum && shortMinimum <= shortMaximum)
        val grossMinimum = grossConstraint.target ?: grossConstraint.minimum
        val grossMaximum = grossConstraint.target ?: grossConstraint.maximum
        val netMinimum = netConstraint.target ?: netConstraint.minimum
        val netMaximum = netConstraint.target ?: netConstraint.maximum
        val desiredGross = grossConstraint.target ?: (grossMinimum + grossMaximum) / 2.0
        val desiredNet = netConstraint.target ?: (netMinimum + netMaximum) / 2.0
        val desiredLong = (desiredGross + desiredNet) / 2.0
        val desiredShort = (desiredGross - desiredNet) / 2.0
        val candidates = linkedSetOf(
            longMinimum,
            longMaximum,
            desiredLong.coerceIn(longMinimum, longMaximum),
            grossMinimum - shortMinimum,
            grossMinimum - shortMaximum,
            grossMaximum - shortMinimum,
            grossMaximum - shortMaximum,
            netMinimum + shortMinimum,
            netMinimum + shortMaximum,
            netMaximum + shortMinimum,
            netMaximum + shortMaximum,
        ).map { it.coerceIn(longMinimum, longMaximum) }.distinct().sorted()
        var best: Pair<Double, Double>? = null
        var bestDistance = Double.POSITIVE_INFINITY
        candidates.forEach { long ->
            val feasibleShortMinimum = max(
                shortMinimum,
                max(grossMinimum - long, long - netMaximum),
            )
            val feasibleShortMaximum = min(
                shortMaximum,
                min(grossMaximum - long, long - netMinimum),
            )
            if (feasibleShortMinimum <= feasibleShortMaximum + EPSILON) {
                val short = desiredShort.coerceIn(feasibleShortMinimum, feasibleShortMaximum)
                val distance = (long - desiredLong) * (long - desiredLong) +
                    (short - desiredShort) * (short - desiredShort)
                if (distance < bestDistance) {
                    best = long to short
                    bestDistance = distance
                }
            }
        }
        return best
    }

    private const val EPSILON: Double = 1e-8
}
