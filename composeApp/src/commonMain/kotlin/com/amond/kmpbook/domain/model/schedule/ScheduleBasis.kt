package com.amond.kmpbook.domain.model.schedule


/** Whether the game is using a published-year date or its long-range recurrence projection. */
enum class ScheduleBasis(val displayName: String) {
    OFFICIAL("공식 일정"),
    PROJECTED("예상 일정"),
}
