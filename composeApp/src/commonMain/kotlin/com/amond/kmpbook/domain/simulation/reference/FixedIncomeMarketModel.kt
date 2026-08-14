package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import com.amond.kmpbook.domain.model.reference.CreditQuality
import com.amond.kmpbook.domain.model.reference.CreditSpreadSnapshot
import com.amond.kmpbook.domain.model.reference.YieldCurveSnapshot
import com.amond.kmpbook.domain.model.reference.YieldCurveTenor
import com.amond.kmpbook.domain.simulation.market.MacroEnvironment
import kotlin.math.ln
import kotlin.time.Instant

/**
 * 게임의 거시 상태를 채권 엔진이 소비하는 곡선 수준으로 변환한다.
 *
 * KRW는 한국 정책금리를 직접 기준점으로 쓰고, 아직 별도 정책금리 상태가 없는 다른 통화만
 * USD 정책금리에 명시적인 게임 보정값을 더한다. 공식 곡선으로 가장하지 않으며 정책 결정·
 * 물가·성장·유동성 변화가 만기와 신용등급별로 다르게 전달되게 하는 경계다.
 */
class FixedIncomeMarketModel {
    fun frame(
        currencies: Set<ReferenceCurrency>,
        macro: MacroEnvironment,
        at: Instant,
    ): FixedIncomeMarketFrame {
        require(currencies.isNotEmpty())
        val nominal = currencies.associateWith { currency ->
            YieldCurveSnapshot(
                currency = currency,
                annualZeroRates = YieldCurveTenor.entries.associateWith { tenor ->
                    nominalRate(currency, tenor, macro)
                },
                asOf = at,
            )
        }
        val real = currencies.associateWith { currency ->
            YieldCurveSnapshot(
                currency = currency,
                annualZeroRates = YieldCurveTenor.entries.associateWith { tenor ->
                    val nominalRate = nominal.getValue(currency).annualZeroRates.getValue(tenor)
                    (nominalRate - macro.inflationRate).coerceIn(MIN_RATE, MAX_RATE)
                },
                asOf = at,
            )
        }
        val spreads = currencies.associateWith { currency ->
            CreditSpreadSnapshot(
                currency = currency,
                annualSpreads = creditSpreads(macro),
                asOf = at,
            )
        }
        return FixedIncomeMarketFrame(nominal, real, spreads)
    }

    private fun nominalRate(
        currency: ReferenceCurrency,
        tenor: YieldCurveTenor,
        macro: MacroEnvironment,
    ): Double {
        val currencyOffset = CURRENCY_POLICY_OFFSETS.getValue(currency)
        val termPremium = BASE_TERM_PREMIUM * ln(1.0 + tenor.years)
        val inflationSlope = (macro.inflationRate - INFLATION_ANCHOR) *
            (tenor.years / 10.0).coerceIn(0.0, 1.0) * INFLATION_TERM_LOADING
        val growthSlope = macro.growthRate *
            (tenor.years / 30.0).coerceIn(0.0, 1.0) * GROWTH_TERM_LOADING
        val policyAnchor = if (currency == ReferenceCurrency.KRW) {
            macro.koreanPolicyRate
        } else {
            macro.policyRate
        }
        return (policyAnchor + currencyOffset + termPremium + inflationSlope + growthSlope)
            .coerceIn(MIN_RATE, MAX_RATE)
    }

    private fun creditSpreads(macro: MacroEnvironment): Map<CreditQuality, Double> {
        val commonStress = (
            macro.liquidityStress * LIQUIDITY_SPREAD_STRESS -
                macro.riskSentiment * SENTIMENT_SPREAD_LOADING -
                macro.growthSurprise * GROWTH_SPREAD_LOADING
            ).coerceIn(-MAX_SPREAD_RELIEF, MAX_SPREAD_STRESS)
        return CreditQuality.entries.associateWith { quality ->
            if (quality == CreditQuality.SOVEREIGN) {
                0.0
            } else {
                val rank = quality.ordinal.toDouble() / CreditQuality.CCC.ordinal.toDouble()
                (BASE_CREDIT_SPREADS.getValue(quality) + commonStress * rank)
                    .coerceIn(0.0, MAX_CREDIT_SPREAD)
            }
        }
    }

    companion object {
        private const val MIN_RATE = -0.10
        private const val MAX_RATE = 1.00
        private const val INFLATION_ANCHOR = 0.02
        private const val BASE_TERM_PREMIUM = 0.0020
        private const val INFLATION_TERM_LOADING = 0.45
        private const val GROWTH_TERM_LOADING = 0.12
        private const val LIQUIDITY_SPREAD_STRESS = 0.08
        private const val SENTIMENT_SPREAD_LOADING = 0.018
        private const val GROWTH_SPREAD_LOADING = 0.60
        private const val MAX_SPREAD_RELIEF = 0.005
        private const val MAX_SPREAD_STRESS = 0.20
        private const val MAX_CREDIT_SPREAD = 2.0

        private val BASE_CREDIT_SPREADS = mapOf(
            CreditQuality.AAA to 0.0035,
            CreditQuality.AA to 0.0055,
            CreditQuality.A to 0.0085,
            CreditQuality.BBB to 0.0140,
            CreditQuality.BB to 0.0320,
            CreditQuality.B to 0.0550,
            CreditQuality.CCC to 0.1050,
        )

        private val CURRENCY_POLICY_OFFSETS = ReferenceCurrency.entries.associateWith { currency ->
            when (currency) {
                ReferenceCurrency.KRW -> 0.0
                ReferenceCurrency.USD -> 0.0
                ReferenceCurrency.EUR -> -0.0100
                ReferenceCurrency.JPY -> -0.0200
                ReferenceCurrency.CNY -> -0.0020
                ReferenceCurrency.HKD -> 0.0010
                ReferenceCurrency.GBP -> 0.0020
                ReferenceCurrency.CAD -> -0.0010
                ReferenceCurrency.CHF -> -0.0150
                ReferenceCurrency.AUD -> 0.0015
                ReferenceCurrency.SGD -> -0.0015
                ReferenceCurrency.TWD -> -0.0045
                ReferenceCurrency.INR -> 0.0250
                ReferenceCurrency.BRL -> 0.0600
            }
        }
    }
}
