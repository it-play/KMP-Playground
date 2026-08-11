package com.amond.kmpbook.modding.api

import com.amond.kmpbook.domain.model.event.EventScope
import com.amond.kmpbook.domain.model.event.EventSeverity
import com.amond.kmpbook.domain.model.event.EventType
import com.amond.kmpbook.domain.model.market.Market
import com.amond.kmpbook.domain.model.market.Sector
import kotlin.time.Instant

/** 뉴스·이벤트의 표시 정보와 직접 영향 대상을 복사한 읽기 모델이다. */
data class ModNewsSnapshot(
    val id: String,
    val title: String,
    val description: String,
    val sourceLabel: String,
    val scope: EventScope,
    val type: EventType,
    val severity: EventSeverity,
    val startsAt: Instant,
    val endsAt: Instant,
    val affectedMarkets: Set<Market>,
    val affectedSectors: Set<Sector>,
    val affectedInstrumentIds: Set<String>,
    val isActive: Boolean,
    val isRead: Boolean,
)
