package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.Market
import com.amond.kmpbook.domain.model.OrderSide
import kotlinx.datetime.LocalDate
import kotlin.math.min

enum class FeeJurisdiction(val displayName: String) {
    BROKER_CONTRACT("증권사 약정"),
    UNITED_STATES_REGULATORY("미국 규제기관"),
}

enum class FeeCategory(val displayName: String) {
    BROKER_COMMISSION("매매수수료"),
    FX_SPREAD("환전 스프레드"),
    SEC_SECTION_31("SEC Section 31 fee"),
    FINRA_TAF("FINRA Trading Activity Fee"),
}

data class FeeLineItem(
    val id: String,
    val label: String,
    val amount: MoneyAmount,
    val jurisdiction: FeeJurisdiction,
    val category: FeeCategory,
    val source: RuleSource,
    val effectiveRange: EffectiveDateRange,
) {
    init {
        require(id.isNotBlank() && label.isNotBlank()) { "A fee line needs an id and label." }
        require(amount.minorUnits >= 0L) { "A fee cannot be negative." }
    }
}

data class FeeBreakdown(
    val calculatedOn: LocalDate,
    val currency: Currency,
    val items: List<FeeLineItem>,
    val warnings: List<String> = emptyList(),
) {
    init {
        require(items.all { it.amount.currency == currency }) {
            "Every fee line must use the breakdown currency."
        }
    }

    val totalFees: MoneyAmount
        get() = items.fold(MoneyAmount.zero(currency)) { total, item -> total + item.amount }
}

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

data class BrokerFeeRequest(
    val market: Market,
    val side: OrderSide,
    val grossAmount: MoneyAmount,
    val quantity: Double,
    val tradedOn: LocalDate,
    /** Principal actually exchanged in the same currency; null means no FX conversion in this event. */
    val fxConversionAmount: MoneyAmount? = null,
) {
    init {
        require(quantity > 0.0) { "Trade quantity must be positive." }
        require(grossAmount.minorUnits >= 0L) { "Gross trade amount cannot be negative." }
        require(grossAmount.currency == market.currency) { "Gross currency must match the market." }
        require(fxConversionAmount == null || fxConversionAmount.currency == grossAmount.currency) {
            "FX principal must use the trade currency."
        }
        require(fxConversionAmount == null || fxConversionAmount.minorUnits >= 0L) {
            "FX principal cannot be negative."
        }
    }
}

class BrokerFeeCalculator(
    private val schedule: BrokerFeeSchedule = BrokerFeeSchedule.REGULATORY_ONLY_2026,
) {
    fun calculate(request: BrokerFeeRequest): FeeBreakdown {
        val commissionRate = if (request.market.isKorean) {
            schedule.domesticCommissionRate
        } else {
            schedule.usCommissionRate
        }
        val broadRange = EffectiveDateRange(LocalDate(2026, 1, 1), LocalDate(2040, 12, 31))
        val items = buildList {
            add(
                FeeLineItem(
                    id = "broker-commission",
                    label = "${schedule.brokerName} 매매수수료",
                    amount = commissionRate.apply(
                        request.grossAmount.minorUnits,
                        request.grossAmount.currency,
                        schedule.commissionRounding,
                    ),
                    jurisdiction = FeeJurisdiction.BROKER_CONTRACT,
                    category = FeeCategory.BROKER_COMMISSION,
                    source = schedule.brokerSource,
                    effectiveRange = broadRange,
                ),
            )
            request.fxConversionAmount?.let { principal ->
                add(
                    FeeLineItem(
                        id = "broker-fx-spread",
                        label = "${schedule.brokerName} 환전 스프레드",
                        amount = schedule.fxSpreadRate.apply(
                            principal.minorUnits,
                            principal.currency,
                            schedule.commissionRounding,
                        ),
                        jurisdiction = FeeJurisdiction.BROKER_CONTRACT,
                        category = FeeCategory.FX_SPREAD,
                        source = schedule.brokerSource,
                        effectiveRange = broadRange,
                    ),
                )
            }
            if (request.market.isUnitedStates && request.side == OrderSide.SELL) {
                addUnitedStatesRegulatoryFees(request)
            }
        }
        return FeeBreakdown(
            calculatedOn = request.tradedOn,
            currency = request.grossAmount.currency,
            items = items,
            warnings = listOf(
                "수수료·환전 스프레드·미국 규제기관 fee는 세금이 아니며 증권사별 실제 부과 여부가 다릅니다.",
                "2026-08-07에 확인한 fee를 동결했으므로 이후 SEC·FINRA 변경은 정책팩 갱신이 필요합니다.",
            ),
        )
    }

    private fun MutableList<FeeLineItem>.addUnitedStatesRegulatoryFees(request: BrokerFeeRequest) {
        val secRange = EffectiveDateRange(LocalDate(2026, 4, 4), LocalDate(2040, 12, 31))
        require(request.tradedOn in secRange) {
            "The frozen SEC USD 20.60/million rule is only sourced from 2026-04-04 onward."
        }
        val finraRange = EffectiveDateRange(LocalDate(2026, 1, 1), LocalDate(2040, 12, 31))
        val secSource = RuleSource(
            title = "SEC 2026 Fee Rate Advisory #2",
            url = "https://www.sec.gov/rules-regulations/fee-rate-advisories/2026-2",
        )
        val finraSource = RuleSource(
            title = "FINRA 2026 Trading Activity Fee schedule",
            url = "https://www.finra.org/rules-guidance/rule-filings/sr-finra-2024-019/fee-adjustment-schedule",
        )
        val secFeeUsd = request.grossAmount.amount * schedule.secSection31UsdPerMillionSale / 1_000_000.0
        val finraFeeUsd = min(
            request.quantity * schedule.finraTafUsdPerShare,
            schedule.finraTafMaximumUsdPerTrade,
        )
        add(
            FeeLineItem(
                id = "us-sec-section-31",
                label = "미국 SEC Section 31 fee",
                amount = schedule.regulatoryFeeRounding.fromMajorUnits(secFeeUsd, Currency.USD),
                jurisdiction = FeeJurisdiction.UNITED_STATES_REGULATORY,
                category = FeeCategory.SEC_SECTION_31,
                source = secSource,
                effectiveRange = secRange,
            ),
        )
        add(
            FeeLineItem(
                id = "us-finra-taf",
                label = "미국 FINRA Trading Activity Fee",
                amount = schedule.regulatoryFeeRounding.fromMajorUnits(finraFeeUsd, Currency.USD),
                jurisdiction = FeeJurisdiction.UNITED_STATES_REGULATORY,
                category = FeeCategory.FINRA_TAF,
                source = finraSource,
                effectiveRange = finraRange,
            ),
        )
    }
}
