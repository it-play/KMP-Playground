package com.amond.kmpbook.domain.simulation.event

import com.amond.kmpbook.domain.model.event.EventScope

/** 디버그 콘솔이 임의 payload 없이 기존 이벤트 템플릿을 안내하는 읽기 전용 항목이다. */
data class DebugEventGuide(
    val templateId: String,
    val title: String,
    val scope: EventScope,
    val argumentName: String?,
    val eligibleTargets: List<String>,
    val condition: EventCondition,
    val oneShot: Boolean,
)
