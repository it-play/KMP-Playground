package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceAdvance
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceState
import com.amond.kmpbook.domain.model.reference.CommoditySpotReferenceTerms
import kotlin.math.exp
import kotlin.math.ln
import kotlin.time.Instant

/** Stateless spot-price plus explicit holding/collateral carry calculation. */
class CommoditySpotReferenceEngine {
    fun initialState(
        terms: CommoditySpotReferenceTerms,
        spotLevel: Double,
        referenceLevel: Double,
        cashRateAnnual: Double,
        at: Instant,
    ): CommoditySpotReferenceState {
        require(spotLevel.isFinite() && spotLevel > 0.0)
        require(referenceLevel.isFinite() && referenceLevel > 0.0)
        require(cashRateAnnual.isFinite() && cashRateAnnual in -0.10..1.0)
        return CommoditySpotReferenceState(
            benchmarkRef = terms.benchmarkRef,
            assetClass = terms.assetClass,
            baseCurrency = terms.baseCurrency,
            currentSpotLevel = spotLevel,
            currentReferenceLevel = referenceLevel,
            currentSpotWeight = terms.spotAllocation,
            currentCollateralWeight = terms.collateralAllocation,
            annualizedNetCarryRate = annualizedNetCarry(
                terms = terms,
                spotWeight = terms.spotAllocation,
                collateralWeight = terms.collateralAllocation,
                cashRateAnnual = cashRateAnnual,
            ),
            asOf = at,
        )
    }

    fun advance(input: CommoditySpotAdvanceInput): CommoditySpotReferenceAdvance {
        val spotRatio = input.currentSpotLevel / input.state.currentSpotLevel
        val physicalCarryRate =
            input.terms.annualConvenienceYieldRate -
                input.terms.annualStorageCostRate -
                input.terms.annualCustodyAndInsuranceCostRate
        val collateralRate = input.cashRateAnnual * input.terms.collateralYieldParticipation
        val priceOnlyFactor =
            input.state.currentSpotWeight * spotRatio + input.state.currentCollateralWeight
        val grossFactor =
            input.state.currentSpotWeight * spotRatio *
                exp(physicalCarryRate * input.elapsedYearFraction) +
                input.state.currentCollateralWeight *
                exp(collateralRate * input.elapsedYearFraction)
        require(priceOnlyFactor.isFinite() && priceOnlyFactor > 0.0)
        require(grossFactor.isFinite() && grossFactor > 0.0)
        val priceOnlyLogReturn = ln(priceOnlyFactor)
        val grossLogReturn = ln(grossFactor)
        val nextReferenceLevel = input.state.currentReferenceLevel * grossFactor
        require(nextReferenceLevel.isFinite() && nextReferenceLevel > 0.0)
        val nextSpotWeight =
            input.state.currentSpotWeight * spotRatio *
                exp(physicalCarryRate * input.elapsedYearFraction) / grossFactor
        val next = input.state.copy(
            currentSpotLevel = input.currentSpotLevel,
            currentReferenceLevel = nextReferenceLevel,
            currentSpotWeight = nextSpotWeight,
            currentCollateralWeight = 1.0 - nextSpotWeight,
            annualizedNetCarryRate = annualizedNetCarry(
                terms = input.terms,
                spotWeight = nextSpotWeight,
                collateralWeight = 1.0 - nextSpotWeight,
                cashRateAnnual = input.cashRateAnnual,
            ),
            asOf = input.to,
        )
        return CommoditySpotReferenceAdvance(
            state = next,
            grossReferenceLogReturn = grossLogReturn,
            priceOnlyLogReturn = priceOnlyLogReturn,
            netCarryLogReturn = grossLogReturn - priceOnlyLogReturn,
        )
    }

    private fun annualizedNetCarry(
        terms: CommoditySpotReferenceTerms,
        spotWeight: Double,
        collateralWeight: Double,
        cashRateAnnual: Double,
    ): Double =
        spotWeight * (
            terms.annualConvenienceYieldRate -
                terms.annualStorageCostRate - terms.annualCustodyAndInsuranceCostRate
            ) +
            collateralWeight * cashRateAnnual * terms.collateralYieldParticipation
}
