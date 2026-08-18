package com.amond.kmpbook.domain.simulation.reference

import com.amond.kmpbook.domain.model.fund.BenchmarkDefinition
import com.amond.kmpbook.domain.model.fund.BenchmarkEngineKind
import com.amond.kmpbook.domain.model.fund.BenchmarkRef
import com.amond.kmpbook.domain.model.fund.KofrIndexProfile
import com.amond.kmpbook.domain.model.reference.KofrIndexBook
import com.amond.kmpbook.domain.model.reference.KofrIndexBookAdvance
import com.amond.kmpbook.domain.model.reference.KofrIndexState
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.domain.time.KofrBusinessCalendar
import kotlin.math.ln
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** 전일 KOFR fixing, 익영업일 공표와 공식 ACT/365 일복리 지수 흐름을 운용한다. */
class KofrIndexBookEngine(private val rateModel: KofrRateModel) {
    fun initialBook(
        definitions: Collection<BenchmarkDefinition>,
        at: Instant,
    ): KofrIndexBook {
        val byRef = validatedDefinitions(definitions)
        val states = byRef.values.sortedBy(BenchmarkDefinition::ref).associate { definition ->
            val profile = requireNotNull(definition.kofrIndexProfile)
            require(latestPublishedIndexDate(profile, at) == profile.initialIndexPublicationDate) {
                "KOFR 초기 공표 snapshot과 게임 시작 시각이 일치하지 않습니다."
            }
            require(latestCapturedObservationDate(profile, at) == profile.initialPendingRateObservationDate) {
                "KOFR 초기 fixing snapshot과 게임 시작 시각이 일치하지 않습니다."
            }
            definition.ref to KofrIndexState(
                benchmarkRef = definition.ref,
                publishedRateAnnual = profile.initialPublishedRateAnnual,
                publishedRateObservationDate = profile.initialPublishedRateObservationDate,
                indexLevel = profile.initialIndexLevel,
                indexPublicationDate = profile.initialIndexPublicationDate,
                pendingRateAnnual = profile.initialPendingRateAnnual,
                pendingRateObservationDate = profile.initialPendingRateObservationDate,
                revision = 0L,
                asOf = at,
            )
        }
        return KofrIndexBook(states)
    }

    fun advance(
        book: KofrIndexBook,
        definitions: Collection<BenchmarkDefinition>,
        koreanPolicyRateAnnualAt: (Instant) -> Double,
        from: Instant,
        to: Instant,
    ): KofrIndexBookAdvance {
        require(to >= from)
        val byRef = validatedDefinitions(definitions)
        require(byRef.keys == book.states.keys)
        val states = linkedMapOf<BenchmarkRef, KofrIndexState>()
        val returns = linkedMapOf<BenchmarkRef, Double>()
        val rates = linkedMapOf<BenchmarkRef, Double>()
        book.states.toSortedMap().forEach { (ref, previous) ->
            require(previous.asOf == from) { "KOFR 상태 시각과 진행 시작 시각이 일치해야 합니다." }
            val profile = requireNotNull(byRef.getValue(ref).kofrIndexProfile)
            var next = previous
            eventsBetween(profile, from, to).forEach { event ->
                next = when (event.kind) {
                    KofrEventKind.PUBLICATION -> publish(next, profile, event.date)
                    KofrEventKind.OBSERVATION_CAPTURE -> capture(
                        state = next,
                        profile = profile,
                        observationDate = event.date,
                        koreanPolicyRateAnnual = koreanPolicyRateAnnualAt(event.at),
                    )
                }
            }
            next = next.copy(asOf = to)
            states[ref] = next
            returns[ref] = ln(next.indexLevel / previous.indexLevel)
            rates[ref] = next.publishedRateAnnual
        }
        return KofrIndexBookAdvance(
            book = KofrIndexBook(states),
            grossReferenceLogReturns = returns,
            publishedAnnualRates = rates,
        )
    }

    /**
     * 공식 anchor에서 [at]까지 fixing·공표를 재생한다. 저장 검증도 runtime과 같은 시드와
     * 중앙은행 경로를 넣어 이 함수를 사용하므로 공개 상태나 대기 fixing을 따로 변조할 수 없다.
     */
    fun canonicalBook(
        definitions: Collection<BenchmarkDefinition>,
        at: Instant,
        koreanPolicyRateAnnualAt: (Instant) -> Double,
    ): KofrIndexBook {
        require(at >= GameCalendar.startInstant)
        val initial = initialBook(definitions, GameCalendar.startInstant)
        if (at == GameCalendar.startInstant) return initial
        return advance(
            book = initial,
            definitions = definitions,
            koreanPolicyRateAnnualAt = koreanPolicyRateAnnualAt,
            from = GameCalendar.startInstant,
            to = at,
        ).book
    }

    private fun publish(
        state: KofrIndexState,
        profile: KofrIndexProfile,
        publicationDate: LocalDate,
    ): KofrIndexState {
        if (publicationDate <= state.indexPublicationDate) return state
        val expectedObservationDate = KofrBusinessCalendar.previousBusinessDate(publicationDate)
        require(state.pendingRateObservationDate == expectedObservationDate) {
            "KOFR 공표에는 직전 영업일에 포착된 fixing이 필요합니다."
        }
        val calculatedRate = requireNotNull(state.pendingRateAnnual)
        val publishedRate = roundedAnnualRate(
            calculatedRate,
            profile.publicationRatePercentDecimalPlaces,
        )
        val elapsedCalendarDays = publicationDate.toEpochDays() - state.indexPublicationDate.toEpochDays()
        require(elapsedCalendarDays > 0)
        // The index published today accrues the previously published fixing through today;
        // today's newly published fixing starts the next index accrual interval.
        val factor = 1.0 + state.publishedRateAnnual * elapsedCalendarDays / profile.dayCountBasis
        require(factor > 0.0)
        return state.copy(
            publishedRateAnnual = publishedRate,
            publishedRateObservationDate = expectedObservationDate,
            indexLevel = roundedIndex(state.indexLevel * factor, profile.indexDecimalPlaces),
            indexPublicationDate = publicationDate,
            pendingRateAnnual = null,
            pendingRateObservationDate = null,
            revision = state.revision + 1L,
        )
    }

    private fun capture(
        state: KofrIndexState,
        profile: KofrIndexProfile,
        observationDate: LocalDate,
        koreanPolicyRateAnnual: Double,
    ): KofrIndexState {
        if (observationDate <= state.publishedRateObservationDate) return state
        return state.copy(
            pendingRateAnnual = rateModel.fixingRateAnnual(
                benchmarkRef = state.benchmarkRef,
                observationDate = observationDate,
                koreanPolicyRateAnnual = koreanPolicyRateAnnual,
                volumeTrimFractionPerTail = profile.volumeTrimFractionPerTail,
                calculationRatePercentDecimalPlaces = profile.calculationRatePercentDecimalPlaces,
            ),
            pendingRateObservationDate = observationDate,
        )
    }

    private fun eventsBetween(
        profile: KofrIndexProfile,
        from: Instant,
        to: Instant,
    ): List<KofrEvent> {
        var date = from.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).date
        val through = to.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE).date
        val result = mutableListOf<KofrEvent>()
        while (date <= through) {
            if (KofrBusinessCalendar.isBusinessDate(date)) {
                val publicationAt = LocalDateTime(
                    date,
                    LocalTime(profile.publicationHourKst, profile.publicationMinuteKst),
                ).toInstant(GameCalendar.KOREA_TIME_ZONE)
                if (from < publicationAt && to >= publicationAt) {
                    result += KofrEvent(KofrEventKind.PUBLICATION, date, publicationAt)
                }
                val captureAt = LocalDateTime(
                    date,
                    LocalTime(profile.observationCaptureHourKst, 0),
                ).toInstant(GameCalendar.KOREA_TIME_ZONE)
                if (from < captureAt && to >= captureAt) {
                    result += KofrEvent(KofrEventKind.OBSERVATION_CAPTURE, date, captureAt)
                }
            }
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return result.sortedBy(KofrEvent::at)
    }

    private fun latestPublishedIndexDate(profile: KofrIndexProfile, at: Instant): LocalDate {
        val local = at.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        var candidate = local.date
        val publicationTime = LocalTime(profile.publicationHourKst, profile.publicationMinuteKst)
        if (!KofrBusinessCalendar.isBusinessDate(candidate) || local.time < publicationTime) {
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return KofrBusinessCalendar.latestBusinessDateOnOrBefore(candidate)
    }

    private fun latestCapturedObservationDate(profile: KofrIndexProfile, at: Instant): LocalDate {
        val local = at.toLocalDateTime(GameCalendar.KOREA_TIME_ZONE)
        var candidate = local.date
        if (!KofrBusinessCalendar.isBusinessDate(candidate) ||
            local.time < LocalTime(profile.observationCaptureHourKst, 0)
        ) {
            candidate = candidate.minus(1, DateTimeUnit.DAY)
        }
        return KofrBusinessCalendar.latestBusinessDateOnOrBefore(candidate)
    }

    private fun roundedIndex(value: Double, decimalPlaces: Int): Double {
        return KofrOfficialRounding.halfUp(value, decimalPlaces)
    }

    private fun roundedAnnualRate(value: Double, percentDecimalPlaces: Int): Double {
        return KofrOfficialRounding.halfUp(
            value,
            percentDecimalPlaces + PERCENT_TO_ANNUAL_DECIMAL_PLACES,
        )
    }

    private fun validatedDefinitions(
        definitions: Collection<BenchmarkDefinition>,
    ): Map<BenchmarkRef, BenchmarkDefinition> {
        require(definitions.isNotEmpty())
        require(definitions.all { it.engineKind == BenchmarkEngineKind.OVERNIGHT_RATE_INDEX })
        val byRef = definitions.associateBy(BenchmarkDefinition::ref)
        require(byRef.size == definitions.size)
        return byRef
    }

    // 두 타입은 이 엔진의 발행·관측 이벤트를 시간순으로 처리할 때만 존재한다. 별도 파일로
    // 분리하면 private 이벤트 모델을 internal로 넓혀야 하므로 엔진 내부에 캡슐화한다.
    private data class KofrEvent(
        val kind: KofrEventKind,
        val date: LocalDate,
        val at: Instant,
    )

    private enum class KofrEventKind {
        PUBLICATION,
        OBSERVATION_CAPTURE,
    }

    companion object {
        private const val PERCENT_TO_ANNUAL_DECIMAL_PLACES: Int = 2
    }
}
