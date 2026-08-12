package com.amond.kmpbook.domain.model.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs

/** Typed spot/collateral carry; no product symbol is used to infer storage or custody economics. */
class CommoditySpotReferenceTerms(
    val benchmarkRef: BenchmarkRef,
    val assetClass: CommodityAssetClass,
    val baseCurrency: ReferenceCurrency,
    val spotAllocation: Double,
    val collateralAllocation: Double,
    val annualStorageCostRate: Double,
    val annualCustodyAndInsuranceCostRate: Double,
    val annualConvenienceYieldRate: Double,
    val collateralYieldParticipation: Double,
    val provenance: CommodityReferenceTermsProvenance,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls.toList().sorted().toSet()

    init {
        require(spotAllocation.isFinite() && spotAllocation in MIN_POSITIVE_ALLOCATION..1.0)
        require(collateralAllocation.isFinite() && collateralAllocation in 0.0..1.0)
        require(abs(spotAllocation + collateralAllocation - 1.0) <= ALLOCATION_EPSILON)
        require(annualStorageCostRate.isFinite() && annualStorageCostRate in 0.0..MAX_COST_RATE)
        require(
            annualCustodyAndInsuranceCostRate.isFinite() &&
                annualCustodyAndInsuranceCostRate in 0.0..MAX_COST_RATE,
        )
        require(
            annualConvenienceYieldRate.isFinite() &&
                annualConvenienceYieldRate in MIN_CONVENIENCE_YIELD..MAX_CONVENIENCE_YIELD,
        )
        require(collateralYieldParticipation.isFinite() && collateralYieldParticipation in 0.0..1.0)
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
            other is CommoditySpotReferenceTerms &&
            benchmarkRef == other.benchmarkRef &&
            assetClass == other.assetClass &&
            baseCurrency == other.baseCurrency &&
            spotAllocation == other.spotAllocation &&
            collateralAllocation == other.collateralAllocation &&
            annualStorageCostRate == other.annualStorageCostRate &&
            annualCustodyAndInsuranceCostRate == other.annualCustodyAndInsuranceCostRate &&
            annualConvenienceYieldRate == other.annualConvenienceYieldRate &&
            collateralYieldParticipation == other.collateralYieldParticipation &&
            provenance == other.provenance &&
            officialSourceUrls == other.officialSourceUrls &&
            assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = benchmarkRef.hashCode()
        result = 31 * result + assetClass.hashCode()
        result = 31 * result + baseCurrency.hashCode()
        result = 31 * result + spotAllocation.hashCode()
        result = 31 * result + collateralAllocation.hashCode()
        result = 31 * result + annualStorageCostRate.hashCode()
        result = 31 * result + annualCustodyAndInsuranceCostRate.hashCode()
        result = 31 * result + annualConvenienceYieldRate.hashCode()
        result = 31 * result + collateralYieldParticipation.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "CommoditySpotReferenceTerms(benchmarkRef=$benchmarkRef, assetClass=$assetClass, " +
            "baseCurrency=$baseCurrency, spotAllocation=$spotAllocation, " +
            "collateralAllocation=$collateralAllocation, annualStorageCostRate=$annualStorageCostRate, " +
            "annualCustodyAndInsuranceCostRate=$annualCustodyAndInsuranceCostRate, " +
            "annualConvenienceYieldRate=$annualConvenienceYieldRate, " +
            "collateralYieldParticipation=$collateralYieldParticipation, provenance=$provenance, " +
            "officialSourceUrls=$officialSourceUrls, assumptionId=$assumptionId)"

    private fun requireValidHttpsUrl(value: String) {
        require(value.startsWith("https://") && value.length in MIN_URL_LENGTH..MAX_URL_LENGTH)
        require(value.none(Char::isISOControl))
    }

    companion object {
        private const val MIN_POSITIVE_ALLOCATION: Double = 1e-9
        private const val ALLOCATION_EPSILON: Double = 1e-10
        private const val MAX_COST_RATE: Double = 0.50
        private const val MIN_CONVENIENCE_YIELD: Double = -0.50
        private const val MAX_CONVENIENCE_YIELD: Double = 1.00
        private const val MIN_URL_LENGTH: Int = 9
        private const val MAX_URL_LENGTH: Int = 2_048
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
