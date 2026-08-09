package com.amond.kmpbook.domain.model.protection.krx

import com.amond.kmpbook.domain.model.protection.krx.KrxSidecarPhase

enum class KrxSidecarPhase {
    IDLE,
    NOTICE,
    PROGRAM_FLOW_SUSPENDED,
    FINISHED_FOR_DAY,
}
