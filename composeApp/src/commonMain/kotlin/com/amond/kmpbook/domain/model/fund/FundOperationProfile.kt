package com.amond.kmpbook.domain.model.fund

/**
 * 상품의 운용 방식과 합성 스왑 구조를 기록한다.
 *
 * [activeReturnModelSupport]는 운용 방침과 엔진 구현 수준을 분리한다. 따라서 액티브 상품임을
 * 표현하면서도 검증되지 않은 숫자 alpha를 가격 경로에 추가하지 않을 수 있다.
 */
class FundOperationProfile(
    val managementStyle: FundManagementStyle,
    val syntheticSwapFunding: SyntheticSwapFunding?,
    val activeReturnModelSupport: ActiveReturnModelSupport,
    val activeSyntheticSwapModelParameters: ActiveSyntheticSwapModelParameters? = null,
    val provenance: FundOperationProvenance,
    officialSourceUrls: Set<String>,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls
        .sorted()
        .toCollection(linkedSetOf())
        .toSet()

    init {
        when (managementStyle) {
            FundManagementStyle.PASSIVE -> require(
                activeReturnModelSupport == ActiveReturnModelSupport.NOT_APPLICABLE &&
                    activeSyntheticSwapModelParameters == null,
            ) { "패시브 상품에는 액티브 수익률 모델을 지정할 수 없습니다." }
            FundManagementStyle.ACTIVE -> when (activeReturnModelSupport) {
                ActiveReturnModelSupport.UNMODELED -> require(
                    activeSyntheticSwapModelParameters == null,
                ) { "UNMODELED 액티브 상품에는 합성 수익률 가정을 지정할 수 없습니다." }
                ActiveReturnModelSupport.DETERMINISTIC_ASSUMPTION -> {
                    require(activeSyntheticSwapModelParameters != null) {
                        "결정론적 액티브 합성 모델에는 명시적인 수치 가정이 필요합니다."
                    }
                    require(syntheticSwapFunding == SyntheticSwapFunding.FULLY_FUNDED) {
                        "액티브 합성 스왑 가정은 fully-funded 상품에만 적용합니다."
                    }
                }
                ActiveReturnModelSupport.NOT_APPLICABLE -> error(
                    "액티브 상품에는 NOT_APPLICABLE을 지정할 수 없습니다.",
                )
            }
        }
        require(this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl)) {
            "상품 운용 구조 출처는 유효한 HTTPS URL이어야 합니다."
        }
        when (provenance) {
            FundOperationProvenance.VERIFIED_PRODUCT_DISCLOSURE -> require(
                this.officialSourceUrls.isNotEmpty(),
            ) { "검증된 상품 운용 구조에는 공식 상품 자료가 필요합니다." }
            FundOperationProvenance.UNVERIFIED -> require(this.officialSourceUrls.isEmpty()) {
                "미검증 운용 구조에는 공식 출처를 연결할 수 없습니다."
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FundOperationProfile &&
            managementStyle == other.managementStyle &&
            syntheticSwapFunding == other.syntheticSwapFunding &&
            activeReturnModelSupport == other.activeReturnModelSupport &&
            activeSyntheticSwapModelParameters == other.activeSyntheticSwapModelParameters &&
            provenance == other.provenance &&
            officialSourceUrls == other.officialSourceUrls

    override fun hashCode(): Int {
        var result = managementStyle.hashCode()
        result = 31 * result + (syntheticSwapFunding?.hashCode() ?: 0)
        result = 31 * result + activeReturnModelSupport.hashCode()
        result = 31 * result + (activeSyntheticSwapModelParameters?.hashCode() ?: 0)
        result = 31 * result + provenance.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        return result
    }

    override fun toString(): String =
        "FundOperationProfile(managementStyle=$managementStyle, " +
            "syntheticSwapFunding=$syntheticSwapFunding, " +
            "activeReturnModelSupport=$activeReturnModelSupport, " +
            "activeSyntheticSwapModelParameters=$activeSyntheticSwapModelParameters, " +
            "provenance=$provenance, " +
            "officialSourceUrls=$officialSourceUrls)"

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length in MIN_URL_LENGTH..MAX_URL_LENGTH &&
            value.startsWith("https://") &&
            value.none(Char::isISOControl)

    companion object {
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        private const val MIN_URL_LENGTH: Int = 9
        private const val MAX_URL_LENGTH: Int = 2_048
    }
}
