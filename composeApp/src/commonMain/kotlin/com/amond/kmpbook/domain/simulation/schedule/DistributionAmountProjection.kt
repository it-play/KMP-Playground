package com.amond.kmpbook.domain.simulation.schedule

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import kotlin.math.pow
import kotlin.math.round
import kotlinx.datetime.LocalDate

/**
 * 운용사가 아직 정하지 않은 미래 좌당 분배금을 카탈로그와 ex-date만으로 재생하는 게임 가정이다.
 * 시작가격×표시 연 분배수익률÷연간 횟수를 2026 anchor로 쓰며, 이는 공식 공시나 수익률 전망이 아니다.
 */
object DistributionAmountProjection {
    fun projectedGrossPerUnit(stock: StockDefinition, exDate: LocalDate): Double {
        val periodsPerYear = stock.behavior.distributionFrequency.periodsPerYear
        require(periodsPerYear > 0)
        val policy = stock.behavior.distributionPolicy
        val anchor = stock.initialPrice * stock.dividendYield / periodsPerYear
        require(anchor.isFinite() && anchor >= 0.0)
        if (anchor == 0.0) return 0.0
        val elapsedYears = (exDate.year - PROJECTION_ANCHOR_YEAR).coerceAtLeast(0).toDouble()
        val growthFactor = (1.0 + policy.projectedAnnualNominalGrowthRate).pow(elapsedYears)
        val unitVariation = stableUnitInterval(
            "${policy.projectionAssumption}:${stock.id}:$exDate",
        ) * 2.0 - 1.0
        val variationFactor = 1.0 + unitVariation * policy.projectedAmountVariationRate
        val raw = anchor * growthFactor * variationFactor
        val minorUnitFactor = if (stock.currency == Currency.KRW) 1.0 else USD_PROJECTION_SCALE
        return (round(raw * minorUnitFactor) / minorUnitFactor)
            .coerceAtLeast(1.0 / minorUnitFactor)
    }

    private fun stableUnitInterval(value: String): Double {
        var hash = FNV_OFFSET_BASIS
        value.forEach { character ->
            hash = (hash xor character.code.toLong()) * FNV_PRIME
        }
        return (hash and Long.MAX_VALUE).toDouble() / Long.MAX_VALUE.toDouble()
    }

    private const val PROJECTION_ANCHOR_YEAR: Int = 2026
    private const val USD_PROJECTION_SCALE: Double = 10_000.0
    private const val FNV_OFFSET_BASIS: Long = -3750763034362895579L
    private const val FNV_PRIME: Long = 0x100000001B3L
}
