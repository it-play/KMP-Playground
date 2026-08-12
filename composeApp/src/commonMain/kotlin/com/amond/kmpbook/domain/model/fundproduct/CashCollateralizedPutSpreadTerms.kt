package com.amond.kmpbook.domain.model.fundproduct

import com.amond.kmpbook.domain.model.fund.BenchmarkRef

/**
 * Terms for a cash portfolio overlaid with a short high-strike put and a long lower-strike put.
 *
 * [cashBenchmarkRef] is the return source for collateral. [optionReference] is the equity or
 * equity-index reference used for option strikes and settlement. They are deliberately separate:
 * a cash benchmark must never be substituted for the option underlying merely because both feed
 * one listed product. [maximumSettlementLossRatio] sizes the spread by its contractual maximum
 * loss, rather than by a misleading ETF-style holding weight. For model-assumption terms,
 * [officialSourceUrls] may still preserve documents that establish the product's structural
 * mandate; [assumptionId] makes clear that strike, tenor or sizing parameters are not presented as
 * verified issuer facts.
 */
class CashCollateralizedPutSpreadTerms(
    val productId: String,
    val cashBenchmarkRef: BenchmarkRef,
    val optionReference: DailyResetReference,
    val directReferenceTerminationRule: DirectReferenceTerminationRule?,
    val tenorTradingDays: Int,
    val rollCalendar: OptionRollCalendar,
    val rollLeadTradingDays: Int,
    val maximumSettlementLossRatio: Double,
    val shortPutStrikeMoneyness: Double,
    val longPutStrikeMoneyness: Double,
    val provenance: OptionStrategyTermsProvenance,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
    val premiumModel: OptionPremiumModelParameters,
) {
    val officialSourceUrls: Set<String> =
        officialSourceUrls
            .toList()
            .sorted()
            .toSet()

    init {
        require(ID_PATTERN.matches(productId))
        require(
            (optionReference.kind == DailyResetReferenceKind.INSTRUMENT) ==
                (directReferenceTerminationRule != null),
        ) { "직접 종목 option reference에만 명시적 종료 규칙이 필요합니다." }
        require(
            optionReference.benchmarkRef == null ||
                optionReference.benchmarkRef != cashBenchmarkRef,
        ) { "현금 벤치마크와 풋옵션 기초 벤치마크는 서로 달라야 합니다." }
        require(tenorTradingDays in 1..MAX_TENOR_TRADING_DAYS)
        require(rollLeadTradingDays in 0 until tenorTradingDays)
        require(
            maximumSettlementLossRatio.isFinite() &&
                maximumSettlementLossRatio in MIN_POSITIVE_RATIO..1.0,
        )
        require(
            shortPutStrikeMoneyness.isFinite() &&
                shortPutStrikeMoneyness in MIN_SHORT_STRIKE_MONEYNESS..MAX_STRIKE_MONEYNESS,
        )
        require(
            longPutStrikeMoneyness.isFinite() &&
                longPutStrikeMoneyness in MIN_LONG_STRIKE_MONEYNESS..MAX_STRIKE_MONEYNESS,
        )
        require(shortPutStrikeMoneyness - longPutStrikeMoneyness >= MIN_SPREAD_WIDTH_MONEYNESS) {
            "매도 풋 행사가격은 매수 풋 행사가격보다 충분히 높아야 합니다."
        }
        this.officialSourceUrls.forEach(::requireValidHttpsUrl)
        when (provenance) {
            OptionStrategyTermsProvenance.VERIFIED_PRODUCT_DISCLOSURE,
            OptionStrategyTermsProvenance.VERIFIED_INDEX_METHODOLOGY,
            -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            OptionStrategyTermsProvenance.MODEL_ASSUMPTION -> {
                requireNotNull(assumptionId)
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is CashCollateralizedPutSpreadTerms &&
            productId == other.productId &&
            cashBenchmarkRef == other.cashBenchmarkRef &&
            optionReference == other.optionReference &&
            directReferenceTerminationRule == other.directReferenceTerminationRule &&
            tenorTradingDays == other.tenorTradingDays &&
            rollCalendar == other.rollCalendar &&
            rollLeadTradingDays == other.rollLeadTradingDays &&
            maximumSettlementLossRatio == other.maximumSettlementLossRatio &&
            shortPutStrikeMoneyness == other.shortPutStrikeMoneyness &&
            longPutStrikeMoneyness == other.longPutStrikeMoneyness &&
            provenance == other.provenance &&
            officialSourceUrls == other.officialSourceUrls &&
            assumptionId == other.assumptionId &&
            premiumModel == other.premiumModel

    override fun hashCode(): Int {
        var result = productId.hashCode()
        result = 31 * result + cashBenchmarkRef.hashCode()
        result = 31 * result + optionReference.hashCode()
        result = 31 * result + (directReferenceTerminationRule?.hashCode() ?: 0)
        result = 31 * result + tenorTradingDays
        result = 31 * result + rollCalendar.hashCode()
        result = 31 * result + rollLeadTradingDays
        result = 31 * result + maximumSettlementLossRatio.hashCode()
        result = 31 * result + shortPutStrikeMoneyness.hashCode()
        result = 31 * result + longPutStrikeMoneyness.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        result = 31 * result + premiumModel.hashCode()
        return result
    }

    override fun toString(): String =
        "CashCollateralizedPutSpreadTerms(productId=$productId, " +
            "cashBenchmarkRef=$cashBenchmarkRef, optionReference=$optionReference, " +
            "directReferenceTerminationRule=$directReferenceTerminationRule, " +
            "tenorTradingDays=$tenorTradingDays, rollCalendar=$rollCalendar, " +
            "rollLeadTradingDays=$rollLeadTradingDays, " +
            "maximumSettlementLossRatio=$maximumSettlementLossRatio, " +
            "shortPutStrikeMoneyness=$shortPutStrikeMoneyness, " +
            "longPutStrikeMoneyness=$longPutStrikeMoneyness, provenance=$provenance, " +
            "officialSourceUrls=$officialSourceUrls, assumptionId=$assumptionId, " +
            "premiumModel=$premiumModel)"

    private fun requireValidHttpsUrl(value: String) {
        require(value.startsWith("https://") && value.length <= MAX_URL_LENGTH)
        require(value.length > "https://".length && value.none(Char::isISOControl))
    }

    companion object {
        private const val MAX_TENOR_TRADING_DAYS: Int = 504
        private const val MIN_POSITIVE_RATIO: Double = 1e-9
        private const val MIN_LONG_STRIKE_MONEYNESS: Double = 0.05
        private const val MIN_SHORT_STRIKE_MONEYNESS: Double = 0.051
        private const val MAX_STRIKE_MONEYNESS: Double = 1.50
        private const val MIN_SPREAD_WIDTH_MONEYNESS: Double = 0.001
        private const val MAX_URL_LENGTH: Int = 2_048
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
