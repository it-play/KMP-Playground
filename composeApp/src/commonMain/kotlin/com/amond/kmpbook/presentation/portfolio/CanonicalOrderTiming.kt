package com.amond.kmpbook.presentation.portfolio

import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.time.GameCalendar
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/** Canonical close of the first regular session in which a DAY order can execute. */
fun canonicalDayOrderSessionClose(
    market: Market,
    createdAt: Instant,
): Instant? {
    val createdDate = GameCalendar.marketLocalDateTime(market, createdAt).date
    var candidate = createdDate
    while (candidate <= GameCalendar.CAMPAIGN_END_DATE) {
        val session = GameCalendar.regularSessionWindow(market, candidate)
        if (session != null && (candidate > createdDate || createdAt < session.closesAt)) {
            return session.closesAt
        }
        candidate = candidate.plus(1, DateTimeUnit.DAY)
    }
    return null
}
