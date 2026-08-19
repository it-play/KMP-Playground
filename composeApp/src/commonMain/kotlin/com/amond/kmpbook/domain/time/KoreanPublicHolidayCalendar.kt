package com.amond.kmpbook.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** 관공서 공휴일 규정의 원 공휴일과 대체공휴일을 계산한다. */
internal object KoreanPublicHolidayCalendar {
    fun closedDates(year: Int): Set<LocalDate> {
        require(year in SUPPORTED_YEARS) { "한국 공휴일 계산 범위를 벗어났습니다: $year" }
        HISTORICAL_CLOSED_DATES[year]?.let { return it }
        val occasions = holidayOccasions(year)
        val originalDates = occasions.flatMapTo(mutableSetOf()) { occasion -> occasion.dates }
        val occurrencesByDate = occasions
            .flatMap(HolidayOccasion::dates)
            .groupingBy { date -> date }
            .eachCount()
        val closedDates = originalDates.toMutableSet()

        overlappingOccasionClusters(occasions)
            .filter { cluster ->
                cluster.any { (_, occasion) -> occasion.requiresSubstitute(occurrencesByDate) }
            }
            .sortedWith(
                compareBy<List<IndexedValue<HolidayOccasion>>> { cluster ->
                    cluster.maxOf { indexed -> indexed.value.lastDate }
                }.thenBy { cluster -> cluster.minOf(IndexedValue<HolidayOccasion>::index) },
            )
            .forEach { cluster ->
                var substitute = cluster.maxOf { indexed -> indexed.value.lastDate }
                    .plus(1, DateTimeUnit.DAY)
                // 시행령의 "공휴일이 아닌 날"은 Article 2 원 공휴일을 뜻한다. 다른 사유가
                // 같은 날을 대체일로 지목해도 그 생성된 대체일 때문에 하루 더 밀지 않는다.
                while (substitute.dayOfWeek in WEEKEND || substitute in originalDates) {
                    substitute = substitute.plus(1, DateTimeUnit.DAY)
                }
                closedDates += substitute
            }

        closedDates += KoreanElectionCalendar.regularElectionDates(year, closedDates)
        return closedDates.filterTo(mutableSetOf()) { date -> date.year == year }
    }

    /**
     * Two named holidays on the same civil date create one lost public-holiday occasion, not one
     * substitute for each label. Connected overlap clusters therefore own a single entitlement;
     * an additional weekend reason inside the same cluster cannot manufacture another closure.
     */
    private fun overlappingOccasionClusters(
        occasions: List<HolidayOccasion>,
    ): List<List<IndexedValue<HolidayOccasion>>> {
        val remaining = occasions.withIndex().toMutableList()
        val clusters = mutableListOf<List<IndexedValue<HolidayOccasion>>>()
        while (remaining.isNotEmpty()) {
            val cluster = mutableListOf(remaining.removeAt(0))
            val clusterDates = cluster.single().value.dates.toMutableSet()
            var expanded: Boolean
            do {
                expanded = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (candidate.value.dates.any(clusterDates::contains)) {
                        cluster += candidate
                        clusterDates += candidate.value.dates
                        iterator.remove()
                        expanded = true
                    }
                }
            } while (expanded)
            clusters += cluster
        }
        return clusters
    }

    private fun holidayOccasions(year: Int): List<HolidayOccasion> {
        val lunarNewYear = KoreanLunisolarCalendar.toSolarDate(year, 1, 1)
        val chuseok = KoreanLunisolarCalendar.toSolarDate(year, 8, 15)
        return buildList {
            add(HolidayOccasion.single(LocalDate(year, 1, 1), WeekendSubstitute.NONE, false))
            addSubstitutableNationalHoliday(LocalDate(year, 3, 1))
            add(
                HolidayOccasion.group(
                    dates = (-1..1).map { offset -> lunarNewYear.plus(offset, DateTimeUnit.DAY) },
                    weekendSubstitute = WeekendSubstitute.SUNDAY_ONLY,
                ),
            )
            addSubstitutableHoliday(KoreanLunisolarCalendar.toSolarDate(year, 4, 8))
            if (year >= 2026) {
                addSubstitutableHoliday(LocalDate(year, 5, 1))
            } else {
                // 2025 금융시장 휴업 관행은 유지하되 당시 법정 대체공휴일 대상은 아니다.
                add(HolidayOccasion.single(LocalDate(year, 5, 1), WeekendSubstitute.NONE, false))
            }
            addSubstitutableHoliday(LocalDate(year, 5, 5))
            add(HolidayOccasion.single(LocalDate(year, 6, 6), WeekendSubstitute.NONE, false))
            if (year >= 2026) {
                // 제헌절 공휴일 복원은 2026-05-11 시행이므로 2025 경계연도에는 적용하지 않는다.
                addSubstitutableNationalHoliday(LocalDate(year, 7, 17))
            }
            addSubstitutableNationalHoliday(LocalDate(year, 8, 15))
            add(
                HolidayOccasion.group(
                    dates = (-1..1).map { offset -> chuseok.plus(offset, DateTimeUnit.DAY) },
                    weekendSubstitute = WeekendSubstitute.SUNDAY_ONLY,
                ),
            )
            addSubstitutableNationalHoliday(LocalDate(year, 10, 3))
            addSubstitutableNationalHoliday(LocalDate(year, 10, 9))
            addSubstitutableHoliday(LocalDate(year, 12, 25))
        }
    }

    private fun MutableList<HolidayOccasion>.addSubstitutableNationalHoliday(date: LocalDate) {
        addSubstitutableHoliday(date)
    }

    private fun MutableList<HolidayOccasion>.addSubstitutableHoliday(date: LocalDate) {
        add(HolidayOccasion.single(date, WeekendSubstitute.SATURDAY_OR_SUNDAY, true))
    }

    // 두 타입은 이 달력의 공휴일 군집·대체일 계산에서만 쓰인다. 별도 파일로 분리하면 private
    // 규정 모델을 internal로 넓혀야 하므로 계산기 내부에 캡슐화한다.
    private data class HolidayOccasion(
        val dates: List<LocalDate>,
        val weekendSubstitute: WeekendSubstitute,
        val substitutesWhenOverlapping: Boolean,
    ) {
        val lastDate: LocalDate get() = dates.maxOrNull()!!

        fun requiresSubstitute(occurrencesByDate: Map<LocalDate, Int>): Boolean {
            val weekendTrigger = when (weekendSubstitute) {
                WeekendSubstitute.NONE -> false
                WeekendSubstitute.SUNDAY_ONLY -> dates.any { date -> date.dayOfWeek == DayOfWeek.SUNDAY }
                WeekendSubstitute.SATURDAY_OR_SUNDAY -> dates.any { date -> date.dayOfWeek in WEEKEND }
            }
            val overlapTrigger = substitutesWhenOverlapping && dates.any { date ->
                occurrencesByDate.getValue(date) > 1
            }
            return weekendTrigger || overlapTrigger
        }

        companion object {
            fun single(
                date: LocalDate,
                weekendSubstitute: WeekendSubstitute,
                substitutesWhenOverlapping: Boolean,
            ): HolidayOccasion = HolidayOccasion(
                dates = listOf(date),
                weekendSubstitute = weekendSubstitute,
                substitutesWhenOverlapping = substitutesWhenOverlapping,
            )

            fun group(
                dates: List<LocalDate>,
                weekendSubstitute: WeekendSubstitute,
            ): HolidayOccasion = HolidayOccasion(
                dates = dates,
                weekendSubstitute = weekendSubstitute,
                substitutesWhenOverlapping = true,
            )
        }
    }

    private enum class WeekendSubstitute {
        NONE,
        SUNDAY_ONLY,
        SATURDAY_OR_SUNDAY,
    }

    /**
     * 2021~2024는 대체공휴일 대상 확대와 임시공휴일이 연중 바뀐 경계라, 당시 확정된
     * 금융시장 휴업일을 명시한다. 2025년 이후는 위 규칙 계산 경로를 사용한다.
     * KRX 휴장 규칙: https://global.krx.co.kr/contents/GLB/06/0602/0602020204/GLB0602020204T1.jsp
     */
    private val HISTORICAL_CLOSED_DATES: Map<Int, Set<LocalDate>> = mapOf(
        2021 to setOf(
            LocalDate(2021, 1, 1),
            LocalDate(2021, 2, 11), LocalDate(2021, 2, 12), LocalDate(2021, 2, 13),
            LocalDate(2021, 3, 1), LocalDate(2021, 5, 1), LocalDate(2021, 5, 5),
            LocalDate(2021, 5, 19), LocalDate(2021, 6, 6), LocalDate(2021, 8, 15),
            LocalDate(2021, 8, 16), LocalDate(2021, 9, 20), LocalDate(2021, 9, 21),
            LocalDate(2021, 9, 22), LocalDate(2021, 10, 3), LocalDate(2021, 10, 4),
            LocalDate(2021, 10, 9), LocalDate(2021, 10, 11), LocalDate(2021, 12, 25),
        ),
        2022 to setOf(
            LocalDate(2022, 1, 1), LocalDate(2022, 1, 31), LocalDate(2022, 2, 1),
            LocalDate(2022, 2, 2), LocalDate(2022, 3, 1), LocalDate(2022, 3, 9),
            LocalDate(2022, 5, 1), LocalDate(2022, 5, 5), LocalDate(2022, 5, 8),
            LocalDate(2022, 6, 1), LocalDate(2022, 6, 6), LocalDate(2022, 8, 15),
            LocalDate(2022, 9, 9), LocalDate(2022, 9, 10), LocalDate(2022, 9, 11),
            LocalDate(2022, 9, 12), LocalDate(2022, 10, 3), LocalDate(2022, 10, 9),
            LocalDate(2022, 10, 10), LocalDate(2022, 12, 25),
        ),
        2023 to setOf(
            LocalDate(2023, 1, 1), LocalDate(2023, 1, 21), LocalDate(2023, 1, 22),
            LocalDate(2023, 1, 23), LocalDate(2023, 1, 24), LocalDate(2023, 3, 1),
            LocalDate(2023, 5, 1), LocalDate(2023, 5, 5), LocalDate(2023, 5, 27),
            LocalDate(2023, 5, 29), LocalDate(2023, 6, 6), LocalDate(2023, 8, 15),
            LocalDate(2023, 9, 28), LocalDate(2023, 9, 29), LocalDate(2023, 9, 30),
            LocalDate(2023, 10, 2), LocalDate(2023, 10, 3), LocalDate(2023, 10, 9),
            LocalDate(2023, 12, 25),
        ),
        2024 to setOf(
            LocalDate(2024, 1, 1), LocalDate(2024, 2, 9), LocalDate(2024, 2, 10),
            LocalDate(2024, 2, 11), LocalDate(2024, 2, 12), LocalDate(2024, 3, 1),
            LocalDate(2024, 4, 10), LocalDate(2024, 5, 1), LocalDate(2024, 5, 5),
            LocalDate(2024, 5, 6), LocalDate(2024, 5, 15), LocalDate(2024, 6, 6),
            LocalDate(2024, 8, 15), LocalDate(2024, 9, 16), LocalDate(2024, 9, 17),
            LocalDate(2024, 9, 18), LocalDate(2024, 10, 1), LocalDate(2024, 10, 3),
            LocalDate(2024, 10, 9), LocalDate(2024, 12, 25),
        ),
    )

    private val SUPPORTED_YEARS: IntRange = 2021..2041
    private val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
