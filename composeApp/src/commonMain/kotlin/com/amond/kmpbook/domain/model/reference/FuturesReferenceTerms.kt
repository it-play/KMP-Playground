package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs

/** Static methodology boundary for a single- or multi-sleeve futures reference. */
class FuturesReferenceTerms(
    val benchmarkRef: BenchmarkRef,
    val baseCurrency: ReferenceCurrency,
    val portfolioStyle: FuturesPortfolioStyle,
    val allocationMode: FuturesAllocationMode,
    val collateralRatio: Double,
    val collateralYieldParticipation: Double,
    sleeves: List<FuturesSleeveTerms>,
    val provenance: CommodityReferenceTermsProvenance,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val sleeves: List<FuturesSleeveTerms> = sleeves.sortedBy(FuturesSleeveTerms::sleeveId)
    val officialSourceUrls: Set<String> = officialSourceUrls.toList().sorted().toSet()

    init {
        require(this.sleeves.isNotEmpty() && this.sleeves.size <= MAX_SLEEVES)
        require(this.sleeves.map(FuturesSleeveTerms::sleeveId).distinct().size == this.sleeves.size)
        require(this.sleeves.map(FuturesSleeveTerms::curveId).distinct().size == this.sleeves.size)
        require(abs(this.sleeves.sumOf(FuturesSleeveTerms::targetWeight) - 1.0) <= WEIGHT_EPSILON)
        require(collateralRatio.isFinite() && collateralRatio in 0.0..MAX_COLLATERAL_RATIO)
        require(collateralYieldParticipation.isFinite() && collateralYieldParticipation in 0.0..1.0)
        when (portfolioStyle) {
            FuturesPortfolioStyle.SINGLE_COMMODITY -> {
                require(this.sleeves.size == 1 && this.sleeves.single().assetClass.isCrypto.not())
                require(allocationMode == FuturesAllocationMode.STATIC_TARGETS)
            }
            FuturesPortfolioStyle.CRYPTO_FUTURES -> {
                require(this.sleeves.size == 1 && this.sleeves.single().assetClass.isCrypto)
                require(allocationMode == FuturesAllocationMode.STATIC_TARGETS)
            }
            FuturesPortfolioStyle.STATIC_COMMODITY_BASKET -> {
                require(this.sleeves.size >= 2)
                require(allocationMode == FuturesAllocationMode.STATIC_TARGETS)
            }
            FuturesPortfolioStyle.EXTERNAL_DYNAMIC_COMMODITY_BASKET -> {
                require(this.sleeves.size >= 2)
                require(allocationMode == FuturesAllocationMode.EXTERNAL_TARGETS)
            }
            FuturesPortfolioStyle.EXTERNAL_REAL_ASSET_BASKET ->
                require(allocationMode == FuturesAllocationMode.EXTERNAL_TARGETS)
        }
        this.officialSourceUrls.forEach(::requireValidHttpsUrl)
        when (provenance) {
            CommodityReferenceTermsProvenance.VERIFIED_INDEX_METHODOLOGY,
            CommodityReferenceTermsProvenance.VERIFIED_PRODUCT_DISCLOSURE,
            -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            CommodityReferenceTermsProvenance.MODEL_ASSUMPTION -> {
                require(this.officialSourceUrls.isEmpty())
                requireNotNull(assumptionId)
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FuturesReferenceTerms &&
            benchmarkRef == other.benchmarkRef &&
            baseCurrency == other.baseCurrency &&
            portfolioStyle == other.portfolioStyle &&
            allocationMode == other.allocationMode &&
            collateralRatio == other.collateralRatio &&
            collateralYieldParticipation == other.collateralYieldParticipation &&
            sleeves == other.sleeves &&
            provenance == other.provenance &&
            officialSourceUrls == other.officialSourceUrls &&
            assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = benchmarkRef.hashCode()
        result = 31 * result + baseCurrency.hashCode()
        result = 31 * result + portfolioStyle.hashCode()
        result = 31 * result + allocationMode.hashCode()
        result = 31 * result + collateralRatio.hashCode()
        result = 31 * result + collateralYieldParticipation.hashCode()
        result = 31 * result + sleeves.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "FuturesReferenceTerms(benchmarkRef=$benchmarkRef, baseCurrency=$baseCurrency, " +
            "portfolioStyle=$portfolioStyle, allocationMode=$allocationMode, " +
            "collateralRatio=$collateralRatio, " +
            "collateralYieldParticipation=$collateralYieldParticipation, sleeves=$sleeves, " +
            "provenance=$provenance, officialSourceUrls=$officialSourceUrls, " +
            "assumptionId=$assumptionId)"

    private fun requireValidHttpsUrl(value: String) {
        require(value.startsWith("https://") && value.length in MIN_URL_LENGTH..MAX_URL_LENGTH)
        require(value.none(Char::isISOControl))
    }

    companion object {
        const val WEIGHT_EPSILON: Double = 1e-8
        private const val MAX_SLEEVES: Int = 128
        private const val MAX_COLLATERAL_RATIO: Double = 2.0
        private const val MIN_URL_LENGTH: Int = 9
        private const val MAX_URL_LENGTH: Int = 2_048
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
