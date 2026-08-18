package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.tax.core.MoneyRoundingPolicy
import com.amond.kmpbook.domain.tax.core.TaxRate
import com.amond.kmpbook.domain.tax.domestic.DomesticEtfSaleTaxCalculator
import com.amond.kmpbook.domain.tax.domestic.DomesticEtfSaleTaxRequest
import com.amond.kmpbook.domain.tax.domestic.DomesticSaleTaxCalculator
import com.amond.kmpbook.domain.tax.domestic.DomesticSaleTaxRequest
import com.amond.kmpbook.domain.tax.fee.BrokerFeeCalculator
import com.amond.kmpbook.domain.tax.fee.BrokerFeeRequest
import com.amond.kmpbook.domain.tax.fee.BrokerFeeSchedule
import kotlin.math.round
import kotlinx.datetime.LocalDate

/**
 * Single deterministic source for the fee and immediate Korean-tax amounts attached to a trade.
 * The validated provenance selects whether the disposition went through an exchange or is a
 * zero-commission contractual/fractional cash settlement.
 */
object CanonicalTradeCostProjection {
    fun project(
        stock: StockDefinition,
        side: OrderSide,
        quantity: Double,
        grossCash: Double,
        tradedOn: LocalDate,
        preSaleAveragePrice: Double?,
        mode: CanonicalTradeCostMode,
    ): CanonicalTradeCostResult {
        require(quantity.isFinite() && quantity > 0.0)
        require(grossCash.isFinite() && grossCash >= 0.0)
        require(side != OrderSide.SELL || preSaleAveragePrice?.isFinite() == true)

        val feeBreakdown = if (mode == CanonicalTradeCostMode.REGULAR_EXCHANGE) {
            BROKER_FEE_CALCULATOR.calculate(
                BrokerFeeRequest(
                    market = stock.market,
                    side = side,
                    grossAmount = MoneyRoundingPolicy.MINOR_UNIT_HALF_UP.fromMajorUnits(
                        grossCash,
                        stock.currency,
                    ),
                    quantity = quantity,
                    tradedOn = tradedOn,
                ),
            )
        } else {
            null
        }
        val taxBreakdown = when {
            side != OrderSide.SELL || !stock.market.isKorean -> null
            stock.etfProfile != null -> {
                val acquisitionValueKrw = round(requireNotNull(preSaleAveragePrice) * quantity).toLong()
                val grossProceedsKrw = round(grossCash).toLong()
                val positiveTradingGain = (grossProceedsKrw - acquisitionValueKrw).coerceAtLeast(0L)
                DOMESTIC_ETF_SALE_TAX_CALCULATOR.calculate(
                    DomesticEtfSaleTaxRequest(
                        taxCategory = stock.etfProfile.taxCategory,
                        grossProceedsKrw = grossProceedsKrw,
                        acquisitionValueKrw = acquisitionValueKrw,
                        taxableStandardGainKrw = round(
                            positiveTradingGain * stock.etfProfile.taxablePriceGainRatio,
                        ).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            }
            mode != CanonicalTradeCostMode.CONTRACTUAL_CASH_SETTLEMENT ->
                DOMESTIC_SALE_TAX_CALCULATOR.calculate(
                    DomesticSaleTaxRequest(
                        market = stock.market,
                        grossProceedsKrw = round(grossCash).toLong(),
                        soldOn = tradedOn,
                    ),
                )
            else -> null
        }
        return CanonicalTradeCostResult(
            commission = feeBreakdown?.totalFees?.amount ?: 0.0,
            saleTax = taxBreakdown?.totalTax?.amount ?: 0.0,
            feeBreakdown = feeBreakdown,
            taxBreakdown = taxBreakdown,
        )
    }

    private val BROKER_FEE_CALCULATOR = BrokerFeeCalculator(
        BrokerFeeSchedule(
            id = "simulator-general-account-2026",
            brokerName = "일반계좌",
            domesticCommissionRate = TaxRate(150L),
            usCommissionRate = TaxRate(700L),
            fxSpreadRate = TaxRate(1_000L),
        ),
    )
    private val DOMESTIC_SALE_TAX_CALCULATOR = DomesticSaleTaxCalculator()
    private val DOMESTIC_ETF_SALE_TAX_CALCULATOR = DomesticEtfSaleTaxCalculator()
}
