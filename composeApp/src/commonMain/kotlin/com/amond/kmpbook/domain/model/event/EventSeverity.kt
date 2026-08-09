package com.amond.kmpbook.domain.model.event

enum class EventSeverity(
    val displayName: String,
    val level: Int,
) {
    MINOR("경미", 1),
    MODERATE("보통", 2),
    MAJOR("중대", 3),
    CRITICAL("심각", 4),
}
