package com.amond.kmpbook.domain.model.fundproduct

/** 일일 레버리지·인버스 상품의 계약 및 운용 목표다. */
data class DailyResetTerms(
    val productId: String,
    val reference: DailyResetReference,
    val directReferenceTerminationRule: DirectReferenceTerminationRule?,
    val targetLeverage: Double,
    val resetCalendar: DailyResetCalendar,
    val provenance: DailyResetTermsProvenance,
    val officialSourceUrl: String?,
    val modelParameters: DailyResetModelParameters,
) {
    init {
        require(ID_PATTERN.matches(productId))
        require(
            (reference.kind == DailyResetReferenceKind.INSTRUMENT) ==
                (directReferenceTerminationRule != null),
        ) { "직접 종목 reference에만 명시적 종료 규칙이 필요합니다." }
        require(targetLeverage.isFinite() && targetLeverage in -MAX_ABS_LEVERAGE..MAX_ABS_LEVERAGE)
        require(kotlin.math.abs(targetLeverage) >= 1.0)
        when (provenance) {
            DailyResetTermsProvenance.VERIFIED_PRODUCT_TERMS -> {
                requireNotNull(officialSourceUrl)
                require(officialSourceUrl.startsWith("https://") && officialSourceUrl.length <= MAX_URL_LENGTH)
                require(officialSourceUrl.none(Char::isISOControl))
            }
            DailyResetTermsProvenance.MODEL_ASSUMPTION -> require(officialSourceUrl == null)
        }
    }

    companion object {
        private const val MAX_ABS_LEVERAGE: Double = 5.0
        private const val MAX_URL_LENGTH: Int = 2_048
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9:._-]{2,199}")
    }
}
