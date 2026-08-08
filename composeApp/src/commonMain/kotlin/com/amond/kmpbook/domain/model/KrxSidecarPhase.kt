package com.amond.kmpbook.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

enum class KrxSidecarPhase {
    IDLE,
    NOTICE,
    PROGRAM_FLOW_SUSPENDED,
    FINISHED_FOR_DAY,
}
