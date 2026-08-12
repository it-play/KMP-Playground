package com.amond.kmpbook.domain.model.fund

/** How the reference universe constrains GICS-compatible sectors. */
enum class EquitySectorPolicy {
    ALL_SECTORS,
    INCLUDED_ONLY,
    THEMATIC_CROSS_SECTOR,
    UNVERIFIED,
}
