package com.amond.kmpbook.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 대한민국 표준시 기준 음력 날짜를 양력으로 바꾸는 캠페인 범위 변환기다.
 *
 * 연도별 값은 월별 대소월과 윤달만 담은 압축 달력 데이터다. 공휴일 양력 날짜를
 * 직접 나열하지 않으므로 설·부처님 오신 날·추석은 같은 변환 경로에서 산출된다.
 */
internal object KoreanLunisolarCalendar {
    fun toSolarDate(
        lunarYear: Int,
        lunarMonth: Int,
        lunarDay: Int,
    ): LocalDate {
        require(lunarYear in FIRST_LUNAR_YEAR..LAST_LUNAR_YEAR) {
            "한국 음력 변환 범위를 벗어났습니다: $lunarYear"
        }
        require(lunarMonth in 1..12)
        require(lunarDay in 1..monthDays(lunarYear, lunarMonth))

        var elapsedDays = 0
        for (year in FIRST_LUNAR_YEAR until lunarYear) {
            elapsedDays += yearDays(year)
        }
        for (month in 1 until lunarMonth) {
            elapsedDays += monthDays(lunarYear, month)
            if (leapMonth(lunarYear) == month) {
                elapsedDays += leapMonthDays(lunarYear)
            }
        }
        elapsedDays += lunarDay - 1
        return SOLAR_DATE_OF_FIRST_LUNAR_DAY.plus(elapsedDays, DateTimeUnit.DAY)
    }

    private fun yearDays(year: Int): Int {
        var days = (1..12).sumOf { month -> monthDays(year, month) }
        if (leapMonth(year) != 0) days += leapMonthDays(year)
        return days
    }

    private fun monthDays(year: Int, month: Int): Int =
        SMALL_MONTH_DAYS + ((yearData(year) shr (12 - month)) and 1L).toInt()

    private fun leapMonth(year: Int): Int = ((yearData(year) shr 12) and 0x0fL).toInt()

    private fun leapMonthDays(year: Int): Int =
        SMALL_MONTH_DAYS + ((yearData(year) shr 16) and 1L).toInt()

    private fun yearData(year: Int): Long = YEAR_DATA[year - FIRST_LUNAR_YEAR]

    private const val FIRST_LUNAR_YEAR: Int = 2025
    private const val LAST_LUNAR_YEAR: Int = 2041
    private const val SMALL_MONTH_DAYS: Int = 29
    private val SOLAR_DATE_OF_FIRST_LUNAR_DAY: LocalDate = LocalDate(2025, 1, 29)

    /**
     * Bits 0..11 are the twelve regular month lengths, bits 12..15 the leap
     * month, and bit 16 its length. The remaining source-calendar bits are kept
     * intact so the compact records can be independently checked.
     */
    private val YEAR_DATA: List<Long> = listOf(
        0x83006A6EL, // 2025
        0x82C60A57L, // 2026
        0x82C40527L, // 2027
        0xC2FE56A6L, // 2028
        0x82C60D93L, // 2029
        0x82C405AAL, // 2030
        0x83003B6AL, // 2031
        0xC2C6096DL, // 2032
        0x8300B4AFL, // 2033
        0x82C404AEL, // 2034
        0x82C40A4DL, // 2035
        0xC3016D0DL, // 2036
        0x82C40D25L, // 2037
        0x82C40D52L, // 2038
        0x83005DD4L, // 2039
        0xC2C60B6AL, // 2040
        0x82C6096DL, // 2041
    )
}
