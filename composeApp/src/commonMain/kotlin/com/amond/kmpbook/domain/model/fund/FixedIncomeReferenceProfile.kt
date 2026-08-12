package com.amond.kmpbook.domain.model.fund

import com.amond.kmpbook.domain.model.market.ReferenceCurrency

/**
 * 고정수익·현금 벤치마크를 가격 엔진 입력으로 만드는 정적 기준 프로필이다.
 *
 * [effectiveDurationYears]는 legacy 상품 행동값과 별개다. 공식 수치가 아니면 반드시
 * [FixedIncomeDurationProvenance.CALIBRATED_ASSUMPTION]으로 표시한다.
 */
class FixedIncomeReferenceProfile(
    val geography: FixedIncomeGeography,
    currencies: Set<ReferenceCurrency>,
    val assetType: FixedIncomeAssetType,
    val effectiveDurationYears: Double,
    val tenorBand: FixedIncomeTenorBand,
    val creditQuality: FixedIncomeCreditBucket,
    val rateReset: FixedIncomeRateReset,
    val realRateLinked: Boolean,
    val supportLevel: FixedIncomeProfileSupportLevel,
    val durationProvenance: FixedIncomeDurationProvenance,
    officialSourceUrls: Set<String>,
) {
    val currencies: Set<ReferenceCurrency> = currencies
        .sortedBy(ReferenceCurrency::ordinal)
        .toCollection(linkedSetOf())
        .toSet()
    val officialSourceUrls: Set<String> = officialSourceUrls
        .sorted()
        .toCollection(linkedSetOf())
        .toSet()

    init {
        require(this.currencies.isNotEmpty() && this.currencies.size <= MAX_CURRENCIES) {
            "고정수익 기준통화는 1~${MAX_CURRENCIES}개여야 합니다."
        }
        require(effectiveDurationYears.isFinite() && effectiveDurationYears in 0.0..MAX_DURATION_YEARS) {
            "고정수익 유효 듀레이션은 0~$MAX_DURATION_YEARS 사이여야 합니다."
        }
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl)) {
            "고정수익 공식 출처는 절대 HTTPS URL이어야 합니다."
        }
        require(realRateLinked == (assetType == FixedIncomeAssetType.INFLATION_LINKED)) {
            "INFLATION_LINKED 노출과 realRateLinked가 일치해야 합니다."
        }
        require(rateReset == FixedIncomeRateReset.NOT_FLOATING || assetType in FLOATING_ASSET_TYPES) {
            "변동금리 reset은 변동금리·CLO·머니마켓 유형에만 지정할 수 있습니다."
        }
        if (assetType in REQUIRED_FLOATING_ASSET_TYPES) {
            require(rateReset != FixedIncomeRateReset.NOT_FLOATING) {
                "변동금리·CLO 유형에는 rateReset이 필요합니다."
            }
        }
        if (assetType == FixedIncomeAssetType.NOMINAL_GOVERNMENT) {
            require(creditQuality == FixedIncomeCreditBucket.GOVERNMENT_BACKED)
        }
        if (assetType == FixedIncomeAssetType.AGENCY_MBS) {
            require(
                creditQuality in setOf(
                    FixedIncomeCreditBucket.GOVERNMENT_BACKED,
                    FixedIncomeCreditBucket.INVESTMENT_GRADE,
                    FixedIncomeCreditBucket.MIXED,
                ),
            )
        }
        if (assetType == FixedIncomeAssetType.HIGH_YIELD) {
            require(creditQuality in setOf(FixedIncomeCreditBucket.HIGH_YIELD, FixedIncomeCreditBucket.MIXED))
        }
        if (assetType == FixedIncomeAssetType.CLO) {
            require(creditQuality in setOf(
                FixedIncomeCreditBucket.AAA,
                FixedIncomeCreditBucket.INVESTMENT_GRADE,
                FixedIncomeCreditBucket.MIXED,
                FixedIncomeCreditBucket.UNVERIFIED,
            ))
        }
        if (supportLevel == FixedIncomeProfileSupportLevel.VERIFIED_REFERENCE) {
            require(this.officialSourceUrls.isNotEmpty()) {
                "검증된 고정수익 기준 프로필에는 공식 출처가 필요합니다."
            }
        }
        if (durationProvenance == FixedIncomeDurationProvenance.OFFICIAL_DISCLOSURE) {
            require(this.officialSourceUrls.isNotEmpty()) {
                "공식 듀레이션 값에는 해당 공시 출처가 필요합니다."
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FixedIncomeReferenceProfile &&
            geography == other.geography &&
            currencies == other.currencies &&
            assetType == other.assetType &&
            effectiveDurationYears == other.effectiveDurationYears &&
            tenorBand == other.tenorBand &&
            creditQuality == other.creditQuality &&
            rateReset == other.rateReset &&
            realRateLinked == other.realRateLinked &&
            supportLevel == other.supportLevel &&
            durationProvenance == other.durationProvenance &&
            officialSourceUrls == other.officialSourceUrls

    override fun hashCode(): Int {
        var result = geography.hashCode()
        result = 31 * result + currencies.hashCode()
        result = 31 * result + assetType.hashCode()
        result = 31 * result + effectiveDurationYears.hashCode()
        result = 31 * result + tenorBand.hashCode()
        result = 31 * result + creditQuality.hashCode()
        result = 31 * result + rateReset.hashCode()
        result = 31 * result + realRateLinked.hashCode()
        result = 31 * result + supportLevel.hashCode()
        result = 31 * result + durationProvenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        return result
    }

    override fun toString(): String =
        "FixedIncomeReferenceProfile(geography=$geography, currencies=$currencies, " +
            "assetType=$assetType, effectiveDurationYears=$effectiveDurationYears, " +
            "tenorBand=$tenorBand, creditQuality=$creditQuality, rateReset=$rateReset, " +
            "realRateLinked=$realRateLinked, supportLevel=$supportLevel, " +
            "durationProvenance=$durationProvenance)"

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length <= MAX_URL_LENGTH &&
            value.startsWith("https://") &&
            value.length > "https://".length &&
            value.none(Char::isISOControl)

    companion object {
        const val MAX_CURRENCIES: Int = 16
        const val MAX_DURATION_YEARS: Double = 50.0
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        const val MAX_URL_LENGTH: Int = 2_048

        private val FLOATING_ASSET_TYPES = setOf(
            FixedIncomeAssetType.FLOATING_RATE,
            FixedIncomeAssetType.CLO,
            FixedIncomeAssetType.MONEY_MARKET,
            FixedIncomeAssetType.SECURITIZED_CREDIT,
            FixedIncomeAssetType.MUNICIPAL,
            FixedIncomeAssetType.PREFERRED_HYBRID,
        )
        private val REQUIRED_FLOATING_ASSET_TYPES = setOf(
            FixedIncomeAssetType.FLOATING_RATE,
            FixedIncomeAssetType.CLO,
        )
    }
}
