package com.amond.kmpbook.domain.model.fundproduct

/**
 * Explicit lifecycle rule for a product whose return source is one operating company.
 *
 * For [DirectReferenceTerminationRuleProvenance.MODEL_ASSUMPTION], source URLs document only the
 * product structure; they do not turn the liquidation policy into a disclosed issuer promise.
 */
class DirectReferenceTerminationRule(
    val policy: DirectReferenceTerminationPolicy,
    val provenance: DirectReferenceTerminationRuleProvenance,
    officialSourceUrls: Set<String>,
    val assumptionId: String?,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls
        .sorted()
        .toCollection(linkedSetOf())
        .toSet()

    init {
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl))
        when (provenance) {
            DirectReferenceTerminationRuleProvenance.VERIFIED_PRODUCT_DISCLOSURE -> {
                require(this.officialSourceUrls.isNotEmpty())
                require(assumptionId == null)
            }
            DirectReferenceTerminationRuleProvenance.MODEL_ASSUMPTION -> {
                requireNotNull(assumptionId)
                require(ASSUMPTION_ID_PATTERN.matches(assumptionId))
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is DirectReferenceTerminationRule &&
            policy == other.policy && provenance == other.provenance &&
            officialSourceUrls == other.officialSourceUrls && assumptionId == other.assumptionId

    override fun hashCode(): Int {
        var result = policy.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        result = 31 * result + (assumptionId?.hashCode() ?: 0)
        return result
    }

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length in MIN_URL_LENGTH..MAX_URL_LENGTH && value.startsWith("https://") &&
            value.none(Char::isISOControl)

    companion object {
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        private const val MIN_URL_LENGTH: Int = 9
        private const val MAX_URL_LENGTH: Int = 2_048
        private val ASSUMPTION_ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]{2,159}")
    }
}
