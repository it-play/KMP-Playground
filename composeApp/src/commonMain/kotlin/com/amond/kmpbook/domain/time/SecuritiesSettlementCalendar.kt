package com.amond.kmpbook.domain.time

import com.amond.kmpbook.domain.model.market.Market
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Canonical T+ settlement dates, separated from exchange-only closing days. */
object SecuritiesSettlementCalendar {
    fun settlementDate(market: Market, tradedOn: LocalDate): LocalDate {
        var candidate = tradedOn
        var elapsedBusinessDays = 0
        val requiredBusinessDays = if (market.isUnitedStates) 1 else 2
        while (elapsedBusinessDays < requiredBusinessDays) {
            candidate = candidate.plus(1, DateTimeUnit.DAY)
            val isBusinessDate = if (market.isKorean) {
                // KRX's year-end closing day is not a KSD/financial settlement holiday.
                KofrBusinessCalendar.isBusinessDate(candidate)
            } else {
                !GameCalendar.isWeekend(candidate) &&
                    candidate !in DefaultMarketHolidays.closedDates(market, candidate.year)
            }
            if (isBusinessDate) elapsedBusinessDays += 1
        }
        return candidate
    }
}
