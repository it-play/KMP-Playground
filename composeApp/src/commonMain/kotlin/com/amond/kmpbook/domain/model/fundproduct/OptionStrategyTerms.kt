package com.amond.kmpbook.domain.model.fundproduct

/**
 * A product's disclosed option program, separated from the option valuation assumptions.
 *
 * Verified terms must cite at least one HTTPS source. Model assumptions deliberately use an
 * assumption identifier instead, so an inferred strike or overwrite ratio cannot be presented as
 * an issuer fact.
 */
class OptionStrategyTerms(
    val productId: String,
    val reference: DailyResetReference,
    val directReferenceTerminationRule: DirectReferenceTerminationRule?,
    val kind: OptionStrategyKind,
    val tenorTradingDays: Int,
    val rollCalendar: OptionRollCalendar,
    val rollLeadTradingDays: Int,
    val provenance: OptionStrategyTermsProvenance,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
    val premiumModel: OptionPremiumModelParameters,
    val coveredCall: CoveredCallStrategyTerms?,
    val optionIncome: OptionIncomeStrategyTerms?,
    val bufferedPutSpread: BufferedPutSpreadStrategyTerms?,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls
        .toList()
        .sorted()
        .toSet()

    init {
        require(ID_PATTERN.matches(productId))
        require(
            (reference.kind == DailyResetReferenceKind.INSTRUMENT) ==
                (directReferenceTerminationRule != null),
        ) { "직접 종목 reference에만 명시적 종료 규칙이 필요합니다." }
        require(tenorTradingDays in 1..MAX_TENOR_TRADING_DAYS)
        require(rollLeadTradingDays in 0 until tenorTradingDays)
        this.officialSourceUrls.forEach(::requireValidHttpsUrl)
        when (provenance) {
            OptionStrategyTermsProvenance.VERIFIED_PRODUCT_DISCLOSURE,
            OptionStrategyTermsProvenance.VERIFIED_INDEX_METHODOLOGY,
            -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            OptionStrategyTermsProvenance.MODEL_ASSUMPTION -> {
                require(this.officialSourceUrls.isEmpty())
                requireNotNull(assumptionId)
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
        when (kind) {
            OptionStrategyKind.COVERED_CALL -> {
                requireNotNull(coveredCall)
                require(optionIncome == null && bufferedPutSpread == null)
            }
            OptionStrategyKind.OPTION_INCOME -> {
                require(coveredCall == null)
                requireNotNull(optionIncome)
                require(bufferedPutSpread == null)
            }
            OptionStrategyKind.BUFFERED_PUT_SPREAD -> {
                require(coveredCall == null && optionIncome == null)
                requireNotNull(bufferedPutSpread)
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is OptionStrategyTerms &&
            productId == other.productId &&
            reference == other.reference &&
            directReferenceTerminationRule == other.directReferenceTerminationRule &&
            kind == other.kind &&
            tenorTradingDays == other.tenorTradingDays &&
            rollCalendar == other.rollCalendar &&
            rollLeadTradingDays == other.rollLeadTradingDays &&
            provenance == other.provenance &&
            officialSourceUrls == other.officialSourceUrls &&
            assumptionId == other.assumptionId &&
            premiumModel == other.premiumModel &&
            coveredCall == other.coveredCall &&
            optionIncome == other.optionIncome &&
            bufferedPutSpread == other.bufferedPutSpread

    override fun hashCode(): Int {
        var result = productId.hashCode()
        result = 31 * result + reference.hashCode()
        result = 31 * result + (directReferenceTerminationRule?.hashCode() ?: 0)
        result = 31 * result + kind.hashCode()
        result = 31 * result + tenorTradingDays
        result = 31 * result + rollCalendar.hashCode()
        result = 31 * result + rollLeadTradingDays
        result = 31 * result + provenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        result = 31 * result + premiumModel.hashCode()
        result = 31 * result + (coveredCall?.hashCode() ?: 0)
        result = 31 * result + (optionIncome?.hashCode() ?: 0)
        result = 31 * result + (bufferedPutSpread?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "OptionStrategyTerms(productId=$productId, reference=$reference, " +
            "directReferenceTerminationRule=$directReferenceTerminationRule, kind=$kind, " +
            "tenorTradingDays=$tenorTradingDays, rollCalendar=$rollCalendar, " +
            "rollLeadTradingDays=$rollLeadTradingDays, provenance=$provenance, " +
            "officialSourceUrls=$officialSourceUrls, assumptionId=$assumptionId, " +
            "premiumModel=$premiumModel, coveredCall=$coveredCall, optionIncome=$optionIncome, " +
            "bufferedPutSpread=$bufferedPutSpread)"

    private fun requireValidHttpsUrl(value: String) {
        require(value.startsWith("https://") && value.length <= MAX_URL_LENGTH)
        require(value.length > "https://".length && value.none(Char::isISOControl))
    }

    companion object {
        private const val MAX_TENOR_TRADING_DAYS: Int = 504
        private const val MAX_URL_LENGTH: Int = 2_048
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
