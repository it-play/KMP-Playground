package com.amond.kmpbook.domain.model.fund

/** MBS interest-only structure plus an explicitly sourced numeric prepayment model. */
class MbsInterestOnlySleeveTerms(
    val prepaymentModel: MbsInterestOnlyPrepaymentModel,
    val termsProvenance: MbsInterestOnlyTermsProvenance,
    officialSourceUrls: Set<String>,
    val modelParameters: MbsInterestOnlyModelParameters,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls.sorted().toCollection(linkedSetOf()).toSet()

    init {
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl))
        if (termsProvenance == MbsInterestOnlyTermsProvenance.VERIFIED_PRODUCT_DISCLOSURE) {
            require(this.officialSourceUrls.isNotEmpty())
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is MbsInterestOnlySleeveTerms &&
            prepaymentModel == other.prepaymentModel && termsProvenance == other.termsProvenance &&
            officialSourceUrls == other.officialSourceUrls && modelParameters == other.modelParameters

    override fun hashCode(): Int {
        var result = prepaymentModel.hashCode()
        result = 31 * result + termsProvenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + modelParameters.hashCode()
        return result
    }

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length in 9..2_048 && value.startsWith("https://") && value.none(Char::isISOControl)

    companion object {
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
    }
}
