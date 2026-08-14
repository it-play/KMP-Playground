package com.amond.kmpbook.domain.model.fund

import com.amond.kmpbook.domain.model.market.ReferenceCurrency
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlinx.datetime.LocalDate

/** 한국무위험지표금리(KOFR)와 그 일복리 지수를 실행하기 위한 검증된 방법론 조건이다. */
class KofrIndexProfile(
    val currency: ReferenceCurrency,
    val dayCountBasis: Int,
    val volumeTrimFractionPerTail: Double,
    val calculationRatePercentDecimalPlaces: Int,
    val publicationRatePercentDecimalPlaces: Int,
    val publicationHourKst: Int,
    val publicationMinuteKst: Int,
    val observationCaptureHourKst: Int,
    val indexBaseDate: LocalDate,
    val indexBaseValue: Double,
    val indexDecimalPlaces: Int,
    val initialPublishedRateAnnual: Double,
    val initialPublishedRateObservationDate: LocalDate,
    val initialIndexLevel: Double,
    val initialIndexPublicationDate: LocalDate,
    val initialPendingRateAnnual: Double,
    val initialPendingRateObservationDate: LocalDate,
    val supportLevel: BenchmarkSupportLevel,
    val modelAssumptionId: String,
    officialSourceUrls: Set<String>,
) {
    val officialSourceUrls: Set<String> = officialSourceUrls
        .sorted()
        .toCollection(linkedSetOf())
        .toSet()

    init {
        require(currency == ReferenceCurrency.KRW) { "KOFR 기준통화는 KRW여야 합니다." }
        require(dayCountBasis == 365) { "KOFR 지수는 ACT/365를 사용해야 합니다." }
        require(volumeTrimFractionPerTail.isFinite() && volumeTrimFractionPerTail in 0.0..<0.5)
        require(calculationRatePercentDecimalPlaces == 5) {
            "KOFR 산출 금리는 퍼센트 단위 소수점 다섯째 자리까지 계산해야 합니다."
        }
        require(publicationRatePercentDecimalPlaces == 3) {
            "KOFR 공표 금리는 퍼센트 단위 소수점 셋째 자리여야 합니다."
        }
        require(publicationRatePercentDecimalPlaces < calculationRatePercentDecimalPlaces)
        require(publicationHourKst in 0..23 && publicationMinuteKst in 0..59)
        require(observationCaptureHourKst in 0..23)
        require(indexBaseValue.isFinite() && indexBaseValue > 0.0)
        require(indexDecimalPlaces in 0..12)
        require(initialPublishedRateAnnual.isFinite() && initialPublishedRateAnnual in -0.05..1.0)
        require(isCanonicalPublishedRate(initialPublishedRateAnnual))
        require(initialIndexLevel.isFinite() && initialIndexLevel > 0.0)
        require(initialIndexPublicationDate >= indexBaseDate)
        require(initialPublishedRateObservationDate < initialIndexPublicationDate)
        require(initialPendingRateAnnual.isFinite() && initialPendingRateAnnual in -0.05..1.0)
        require(isCanonicalPublishedRate(initialPendingRateAnnual))
        require(initialPendingRateObservationDate > initialPublishedRateObservationDate)
        require(supportLevel != BenchmarkSupportLevel.PROVISIONAL_PROXY) {
            "KOFR 전용 엔진에는 검증된 기준 출처가 필요합니다."
        }
        require(modelAssumptionId.isNotBlank() && modelAssumptionId == modelAssumptionId.trim())
        require(modelAssumptionId.length <= MAX_ASSUMPTION_ID_LENGTH)
        require(this.officialSourceUrls.isNotEmpty() && this.officialSourceUrls.size <= MAX_OFFICIAL_SOURCE_URLS)
        require(this.officialSourceUrls.all(::isValidHttpsUrl))
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is KofrIndexProfile &&
            currency == other.currency &&
            dayCountBasis == other.dayCountBasis &&
            volumeTrimFractionPerTail == other.volumeTrimFractionPerTail &&
            calculationRatePercentDecimalPlaces == other.calculationRatePercentDecimalPlaces &&
            publicationRatePercentDecimalPlaces == other.publicationRatePercentDecimalPlaces &&
            publicationHourKst == other.publicationHourKst &&
            publicationMinuteKst == other.publicationMinuteKst &&
            observationCaptureHourKst == other.observationCaptureHourKst &&
            indexBaseDate == other.indexBaseDate &&
            indexBaseValue == other.indexBaseValue &&
            indexDecimalPlaces == other.indexDecimalPlaces &&
            initialPublishedRateAnnual == other.initialPublishedRateAnnual &&
            initialPublishedRateObservationDate == other.initialPublishedRateObservationDate &&
            initialIndexLevel == other.initialIndexLevel &&
            initialIndexPublicationDate == other.initialIndexPublicationDate &&
            initialPendingRateAnnual == other.initialPendingRateAnnual &&
            initialPendingRateObservationDate == other.initialPendingRateObservationDate &&
            supportLevel == other.supportLevel &&
            modelAssumptionId == other.modelAssumptionId &&
            officialSourceUrls == other.officialSourceUrls

    override fun hashCode(): Int {
        var result = currency.hashCode()
        result = 31 * result + dayCountBasis
        result = 31 * result + volumeTrimFractionPerTail.hashCode()
        result = 31 * result + calculationRatePercentDecimalPlaces
        result = 31 * result + publicationRatePercentDecimalPlaces
        result = 31 * result + publicationHourKst
        result = 31 * result + publicationMinuteKst
        result = 31 * result + observationCaptureHourKst
        result = 31 * result + indexBaseDate.hashCode()
        result = 31 * result + indexBaseValue.hashCode()
        result = 31 * result + indexDecimalPlaces
        result = 31 * result + initialPublishedRateAnnual.hashCode()
        result = 31 * result + initialPublishedRateObservationDate.hashCode()
        result = 31 * result + initialIndexLevel.hashCode()
        result = 31 * result + initialIndexPublicationDate.hashCode()
        result = 31 * result + initialPendingRateAnnual.hashCode()
        result = 31 * result + initialPendingRateObservationDate.hashCode()
        result = 31 * result + supportLevel.hashCode()
        result = 31 * result + modelAssumptionId.hashCode()
        result = 31 * result + officialSourceUrls.hashCode()
        return result
    }

    override fun toString(): String =
        "KofrIndexProfile(dayCountBasis=$dayCountBasis, trim=$volumeTrimFractionPerTail, " +
            "publication=$publicationHourKst:$publicationMinuteKst, baseDate=$indexBaseDate)"

    private fun isValidHttpsUrl(value: String): Boolean =
        value.length <= MAX_URL_LENGTH && value.startsWith("https://") &&
            value.length > "https://".length && value.none(Char::isISOControl)

    private fun isCanonicalPublishedRate(value: Double): Boolean {
        val scale = 10.0.pow(publicationRatePercentDecimalPlaces + PERCENT_TO_ANNUAL_DECIMAL_PLACES)
        return abs(value * scale - round(value * scale)) <= RATE_ROUNDING_TOLERANCE
    }

    companion object {
        const val MAX_OFFICIAL_SOURCE_URLS: Int = 16
        const val MAX_ASSUMPTION_ID_LENGTH: Int = 160
        const val MAX_URL_LENGTH: Int = 2_048
        private const val PERCENT_TO_ANNUAL_DECIMAL_PLACES: Int = 2
        private const val RATE_ROUNDING_TOLERANCE: Double = 1e-6
    }
}
