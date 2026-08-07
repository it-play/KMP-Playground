package com.amond.kmpbook.domain.tax

import com.amond.kmpbook.domain.model.Currency
import com.amond.kmpbook.domain.model.EtfTaxCategory
import kotlinx.datetime.LocalDate

data class DomesticEtfSaleTaxRequest(
    val taxCategory: EtfTaxCategory,
    val grossProceedsKrw: Long,
    val acquisitionValueKrw: Long,
    /** Simulated increase in the ETF tax-base price for the units sold. */
    val taxableStandardGainKrw: Long,
    val soldOn: LocalDate,
    val roundingPolicy: MoneyRoundingPolicy = MoneyRoundingPolicy.TAX_WON_DOWN,
) {
    init {
        require(taxCategory != EtfTaxCategory.FOREIGN_LISTED) {
            "Foreign-listed ETFs use the foreign-stock capital-gains path."
        }
        require(grossProceedsKrw >= 0L && acquisitionValueKrw >= 0L && taxableStandardGainKrw >= 0L) {
            "ETF sale values cannot be negative."
        }
    }
}

/**
 * Withholding on a Korean-listed ETF sale. ETF units are exempt from securities transaction tax.
 * A domestic-equity ETF's exchange gain is exempt for an ordinary individual account, while an
 * other ETF uses the smaller of its positive trading gain and positive tax-base-price increase.
 */
class DomesticEtfSaleTaxCalculator(
    private val policy: TaxPolicyPack = TaxPolicyPack2026.POLICY,
) {
    fun calculate(request: DomesticEtfSaleTaxRequest): TaxBreakdown {
        policy.requireSimulationDate(request.soldOn)
        val range = policy.frozenScenarioRange
        val tradingGain = (request.grossProceedsKrw - request.acquisitionValueKrw).coerceAtLeast(0L)
        val taxableBaseKrw = when (request.taxCategory) {
            EtfTaxCategory.KOREAN_DOMESTIC_EQUITY -> 0L
            EtfTaxCategory.KOREAN_OTHER -> minOf(tradingGain, request.taxableStandardGainKrw)
            EtfTaxCategory.FOREIGN_LISTED -> error("Validated above")
        }
        val base = MoneyAmount(taxableBaseKrw, Currency.KRW)
        if (taxableBaseKrw == 0L) {
            return TaxBreakdown(
                policyId = policy.id,
                calculatedOn = request.soldOn,
                taxableBase = base,
                items = emptyList(),
                warnings = listOf(
                    if (request.taxCategory == EtfTaxCategory.KOREAN_DOMESTIC_EQUITY) {
                        "국내주식형 ETF 장내 매매차익은 일반 개인계좌에서 비과세이며 ETF 증권거래세도 면제됩니다."
                    } else {
                        "매매차익 또는 게임 과표기준가격 증가분이 없어 보유기간 과세 원천징수가 없습니다."
                    },
                ),
            )
        }

        val source = RuleSource(
            title = "한국거래소 ETF 세금제도",
            url = "https://regulation.krx.co.kr/contents/RGL/03/03060105/RGL03060105.jsp",
        )
        val national = TaxRate.PERCENT_14.apply(taxableBaseKrw, Currency.KRW, request.roundingPolicy)
        val local = TaxRate.PERCENT_1_4.apply(taxableBaseKrw, Currency.KRW, request.roundingPolicy)
        return TaxBreakdown(
            policyId = policy.id,
            calculatedOn = request.soldOn,
            taxableBase = base,
            items = listOf(
                TaxLineItem(
                    id = "kr-etf-holding-period-national",
                    label = "국내상장 기타 ETF 보유기간 배당소득세",
                    amount = national,
                    jurisdiction = TaxJurisdiction.KOREA_NATIONAL,
                    category = TaxCategory.DIVIDEND_WITHHOLDING,
                    source = source,
                    effectiveRange = range,
                ),
                TaxLineItem(
                    id = "kr-etf-holding-period-local",
                    label = "국내상장 기타 ETF 배당 지방소득세",
                    amount = local,
                    jurisdiction = TaxJurisdiction.KOREA_LOCAL,
                    category = TaxCategory.DIVIDEND_WITHHOLDING,
                    source = source,
                    effectiveRange = range,
                ),
            ),
            warnings = listOf(
                "미래 과표기준가격은 ETF별 게임 과표 반영률로 생성한 추정치입니다.",
                "실제 원천징수는 증권사가 보유한 매수·매도 시점 과표기준가격과 과세유보금액을 사용합니다.",
            ),
        )
    }
}
