package com.amond.kmpbook.domain.simulation.fundproduct

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Deterministic Black-Scholes marks for options written on a total-return reference level. */
internal object OptionValuation {
    fun call(
        spot: Double,
        strike: Double,
        cashRateAnnual: Double,
        annualizedVolatility: Double,
        timeYears: Double,
    ): Double = optionMark(
        spot = spot,
        strike = strike,
        cashRateAnnual = cashRateAnnual,
        annualizedVolatility = annualizedVolatility,
        timeYears = timeYears,
        isCall = true,
    )

    fun put(
        spot: Double,
        strike: Double,
        cashRateAnnual: Double,
        annualizedVolatility: Double,
        timeYears: Double,
    ): Double = optionMark(
        spot = spot,
        strike = strike,
        cashRateAnnual = cashRateAnnual,
        annualizedVolatility = annualizedVolatility,
        timeYears = timeYears,
        isCall = false,
    )

    private fun optionMark(
        spot: Double,
        strike: Double,
        cashRateAnnual: Double,
        annualizedVolatility: Double,
        timeYears: Double,
        isCall: Boolean,
    ): Double {
        require(spot.isFinite() && spot > 0.0)
        require(strike.isFinite() && strike > 0.0)
        require(cashRateAnnual.isFinite())
        require(annualizedVolatility.isFinite() && annualizedVolatility >= 0.0)
        require(timeYears.isFinite() && timeYears >= 0.0)

        if (timeYears <= TIME_EPSILON) {
            return if (isCall) max(spot - strike, 0.0) else max(strike - spot, 0.0)
        }

        val discountedStrike = strike * exp(-cashRateAnnual * timeYears)
        if (annualizedVolatility <= VOLATILITY_EPSILON) {
            return if (isCall) {
                max(spot - discountedStrike, 0.0)
            } else {
                max(discountedStrike - spot, 0.0)
            }
        }

        val standardDeviation = annualizedVolatility * sqrt(timeYears)
        val d1 =
            (ln(spot / strike) +
                (cashRateAnnual + 0.5 * annualizedVolatility * annualizedVolatility) * timeYears) /
                standardDeviation
        val d2 = d1 - standardDeviation
        val rawMark = if (isCall) {
            spot * normalCdf(d1) - discountedStrike * normalCdf(d2)
        } else {
            discountedStrike * normalCdf(-d2) - spot * normalCdf(-d1)
        }
        val upperBound = if (isCall) spot else discountedStrike
        return rawMark.coerceIn(0.0, upperBound)
    }

    /** Abramowitz-Stegun 7.1.26; symmetry makes its small approximation error deterministic. */
    private fun normalCdf(value: Double): Double {
        if (value >= CDF_CUTOFF) return 1.0
        if (value <= -CDF_CUTOFF) return 0.0
        val magnitude = abs(value)
        val t = 1.0 / (1.0 + CDF_P * magnitude)
        val polynomial =
            ((((CDF_B5 * t + CDF_B4) * t + CDF_B3) * t + CDF_B2) * t + CDF_B1) * t
        val density = INV_SQRT_TWO_PI * exp(-0.5 * magnitude * magnitude)
        val positiveCdf = 1.0 - density * polynomial
        return if (value >= 0.0) positiveCdf else 1.0 - positiveCdf
    }

    private const val TIME_EPSILON: Double = 1e-12
    private const val VOLATILITY_EPSILON: Double = 1e-12
    private const val CDF_CUTOFF: Double = 10.0
    private const val INV_SQRT_TWO_PI: Double = 0.3989422804014327
    private const val CDF_P: Double = 0.2316419
    private const val CDF_B1: Double = 0.319381530
    private const val CDF_B2: Double = -0.356563782
    private const val CDF_B3: Double = 1.781477937
    private const val CDF_B4: Double = -1.821255978
    private const val CDF_B5: Double = 1.330274429
}
