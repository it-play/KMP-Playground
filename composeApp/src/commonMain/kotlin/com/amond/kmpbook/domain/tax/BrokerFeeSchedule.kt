package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import kotlinx.datetime.LocalDate
import kotlin.math.min

data class BrokerFeeSchedule(
    val id: String,
    val brokerName: String,
    val domesticCommissionRate: TaxRate = TaxRate.ZERO,
    val usCommissionRate: TaxRate = TaxRate.ZERO,
    /** Applied only when [BrokerFeeRequest.fxConversionAmount] is supplied. */
    val fxSpreadRate: TaxRate = TaxRate.ZERO,
    val secSection31UsdPerMillionSale: Double = 20.60,
    val finraTafUsdPerShare: Double = 0.000195,
    val finraTafMaximumUsdPerTrade: Double = 9.79,
    val commissionRounding: MoneyRoundingPolicy = MoneyRoundingPolicy.MINOR_UNIT_HALF_UP,
    val regulatoryFeeRounding: MoneyRoundingPolicy = MoneyRoundingPolicy.REGULATORY_FEE_UP,
    val brokerSource: RuleSource = RuleSource("증권사별 설정 수수료표"),
) {
    init {
        require(id.isNotBlank() && brokerName.isNotBlank()) { "A fee schedule needs an id and broker name." }
        require(secSection31UsdPerMillionSale >= 0.0) { "The SEC fee cannot be negative." }
        require(finraTafUsdPerShare >= 0.0 && finraTafMaximumUsdPerTrade >= 0.0) {
            "FINRA fee parameters cannot be negative."
        }
    }

    companion object {
        /** Commissions and FX spread deliberately remain zero until a real broker schedule is chosen. */
        val REGULATORY_ONLY_2026 = BrokerFeeSchedule(
            id = "us-regulatory-only-2026-08-07",
            brokerName = "사용자 지정 증권사",
        )
    }
}
