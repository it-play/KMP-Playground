package com.amond.kmpbook.domain.tax.fee

import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.model.trading.OrderSide
import com.amond.kmpbook.domain.tax.core.EffectiveDateRange
import com.amond.kmpbook.domain.tax.core.RuleSource
import kotlin.math.min
import kotlinx.datetime.LocalDate

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
                "2026-08-01에 확인한 fee를 동결했으므로 이후 SEC·FINRA 변경은 정책팩 갱신이 필요합니다.",
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
